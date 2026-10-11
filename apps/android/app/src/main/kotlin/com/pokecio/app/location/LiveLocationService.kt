package com.pokecio.app.location

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pokecio.app.MainActivity
import com.pokecio.app.PockyApp
import com.pokecio.app.R
import com.pokecio.app.core.GeoLocation
import com.pokecio.app.core.LocationArea
import com.pokecio.app.core.LocationFix
import com.pokecio.app.core.PockyException
import com.pokecio.app.core.shouldSendLiveLocation
import com.pokecio.app.push.Notifier
import com.pokecio.app.ui.room.timeLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LiveLocationService : Service() {

    private class Share(val endsMs: Long, val area: LocationArea)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val shares = LinkedHashMap<String, Share>()
    private val sent = HashMap<String, LocationFix>()
    private var lastFix: LocationFix? = null
    private var sending = false
    private var ticker: Job? = null
    private var listening = false

    private val listener = LocationListener { location -> onFix(location) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (shares.isEmpty()) restore()
        when (intent?.action) {
            ACTION_SHARE -> {
                val roomId = intent.getStringExtra(EXTRA_ROOM) ?: return START_NOT_STICKY
                val endsMs = intent.getLongExtra(EXTRA_ENDS, 0L)
                val area = LocationArea.entries[intent.getIntExtra(EXTRA_AREA, LocationArea.CITY.ordinal)]
                if (endsMs > System.currentTimeMillis()) {
                    shares[roomId] = Share(endsMs, area)
                    sent.remove(roomId)
                    save()
                }
            }
            ACTION_STOP -> {
                val roomId = intent.getStringExtra(EXTRA_ROOM)
                val stopped = if (roomId != null) listOf(roomId) else shares.keys.toList()
                stopped.forEach(::forget)
                save()
                publish()
                scope.launch {
                    stopOnServer(stopped)
                    if (shares.isEmpty()) finish() else refreshNotification()
                }
                return START_NOT_STICKY
            }
        }

        if (shares.isEmpty()) {
            finish()
            return START_NOT_STICKY
        }

        goForeground()
        listen()
        publish()
        lastFix?.let { scope.launch { sendDue(it) } }
        if (ticker == null) ticker = scope.launch { tick() }
        return START_STICKY
    }

    private fun restore() {
        val now = System.currentTimeMillis()
        store().all.forEach { (roomId, value) ->
            val (ends, area) = (value as? String)?.split('|')?.takeIf { it.size == 2 } ?: return@forEach
            val endsMs = ends.toLongOrNull() ?: return@forEach
            val zone = LocationArea.entries.getOrNull(area.toIntOrNull() ?: -1) ?: return@forEach
            if (endsMs > now) shares[roomId] = Share(endsMs, zone)
        }
        save()
    }

    private fun save() {
        store().edit().clear().apply {
            shares.forEach { (roomId, share) -> putString(roomId, "${share.endsMs}|${share.area.ordinal}") }
        }.apply()
    }

    private fun store() = getSharedPreferences(STORE, MODE_PRIVATE)

    private fun forget(roomId: String) {
        shares.remove(roomId)
        sent.remove(roomId)
    }

    override fun onDestroy() {
        stopListening()
        _active.value = emptyMap()
        scope.cancel()
        super.onDestroy()
    }

    private fun goForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
    }

    private fun notification(): Notification {
        val until = shares.values.maxOfOrNull { it.endsMs } ?: System.currentTimeMillis()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, LiveLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, Notifier.CHANNEL_LOCATION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.live_notification_title))
            .setContentText(
                resources.getQuantityString(
                    R.plurals.live_notification_text,
                    shares.size,
                    timeLabel(until),
                    shares.size,
                ),
            )
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_LOCATION_SHARING)
            .addAction(0, getString(R.string.live_stop), stop)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun listen() {
        if (listening || !DeviceLocation.allowed(this)) return
        val manager = getSystemService(LocationManager::class.java) ?: return
        DeviceLocation.providers(manager).forEach { provider ->
            manager.requestLocationUpdates(provider, UPDATE_MS, 0f, listener, Looper.getMainLooper())
            manager.getLastKnownLocation(provider)?.let(::onFix)
        }
        listening = true
    }

    private fun stopListening() {
        if (!listening) return
        getSystemService(LocationManager::class.java)?.removeUpdates(listener)
        listening = false
    }

    private fun onFix(location: Location) {
        val accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else null
        val fix = LocationFix(
            location = GeoLocation(location.latitude, location.longitude, accuracy),
            speedMps = if (location.hasSpeed()) location.speed.toDouble() else null,
            atMs = System.currentTimeMillis().toULong(),
        )
        lastFix = fix
        scope.launch { sendDue(fix) }
    }

    private suspend fun tick() {
        while (scope.isActive) {
            delay(TICK_MS)
            val now = System.currentTimeMillis()
            val expired = shares.filterValues { it.endsMs <= now }.keys.toList()
            expired.forEach(::forget)
            if (expired.isNotEmpty()) save()
            if (shares.isEmpty()) {
                finish()
                return
            }
            publish()
            refreshNotification()
            lastFix?.let { sendDue(it.copy(atMs = now.toULong())) }
        }
    }

    private suspend fun sendDue(fix: LocationFix) {
        if (sending) return
        val client = (application as PockyApp).clients.current() ?: return
        sending = true
        try {
            val count = shares.size.toUInt()
            for ((roomId, share) in shares.toMap()) {
                if (!shouldSendLiveLocation(sent[roomId], fix, share.area, count)) continue
                try {
                    client.sendLiveLocation(roomId, fix.location, share.area)
                    sent[roomId] = fix
                } catch (_: PockyException.LiveLocationEnded) {
                    forget(roomId)
                    save()
                } catch (_: PockyException) {
                }
            }
        } finally {
            sending = false
        }
        if (shares.isEmpty()) finish() else publish()
    }

    private suspend fun stopOnServer(roomIds: List<String>) {
        val client = (application as PockyApp).clients.current() ?: return
        roomIds.forEach { roomId ->
            try {
                client.stopLiveLocation(roomId)
            } catch (_: PockyException) {
            }
        }
    }

    private fun publish() {
        _active.value = shares.mapValues { it.value.endsMs }
    }

    private fun refreshNotification() {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification())
    }

    private fun finish() {
        stopListening()
        _active.value = emptyMap()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val ACTION_SHARE = "com.pokecio.app.live.SHARE"
        private const val ACTION_STOP = "com.pokecio.app.live.STOP"
        private const val EXTRA_ROOM = "room"
        private const val EXTRA_ENDS = "ends"
        private const val EXTRA_AREA = "area"
        private const val NOTIFICATION_ID = 7_001
        private const val STORE = "live_location"
        private const val UPDATE_MS = 2_000L
        private const val TICK_MS = 30_000L

        private val _active = MutableStateFlow<Map<String, Long>>(emptyMap())
        val active: StateFlow<Map<String, Long>> = _active.asStateFlow()

        fun share(context: Context, roomId: String, endsMs: Long, area: LocationArea) {
            val intent = Intent(context, LiveLocationService::class.java)
                .setAction(ACTION_SHARE)
                .putExtra(EXTRA_ROOM, roomId)
                .putExtra(EXTRA_ENDS, endsMs)
                .putExtra(EXTRA_AREA, area.ordinal)
            ContextCompat.startForegroundService(context, intent)
        }

        fun hasStored(context: Context): Boolean {
            val now = System.currentTimeMillis()
            return context.getSharedPreferences(STORE, MODE_PRIVATE).all.values.any { value ->
                ((value as? String)?.substringBefore('|')?.toLongOrNull() ?: 0L) > now
            }
        }

        fun stop(context: Context, roomId: String? = null) {
            if (_active.value.isEmpty()) return
            val intent = Intent(context, LiveLocationService::class.java)
                .setAction(ACTION_STOP)
                .putExtra(EXTRA_ROOM, roomId)
            context.startService(intent)
        }
    }
}
