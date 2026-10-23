package com.pokecio.app.push

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pokecio.app.PockyApp
import com.pokecio.app.core.NotificationInfo
import com.pokecio.app.core.PockyClient
import com.pokecio.app.core.PockyException
import com.pokecio.app.core.Room
import com.pokecio.app.core.RoomListListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

class MessageCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PockyApp
        if (app.inForeground) return Result.success()

        try {
            val client = app.clients.current() ?: return Result.success()
            val rooms = syncedRooms(client) ?: return Result.success()
            if (!app.inForeground) client.stopSync()

            val preview = client.notificationText()
            val canReply = runCatching { !client.appLock().enabled }.getOrDefault(false)
            val seen = applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            val editor = seen.edit()
            rooms.filter { it.unreadMessages > 0u && it.lastMessage?.isOwn == false }
                .forEach { room ->
                    val timestamp = room.timestampMs?.toLong() ?: return@forEach
                    val key = room.id.hashCode().toString()
                    if (timestamp <= seen.getLong(key, 0L)) return@forEach
                    app.notifier.show(room.asNotification(), preview, canReply)
                    editor.putLong(key, timestamp)
                }
            editor.apply()
        } catch (_: PockyException) {
        }
        return Result.success()
    }

    private suspend fun syncedRooms(client: PockyClient): List<Room>? {
        val latest = AtomicReference<List<Room>?>(null)
        val handle = client.observeRooms(object : RoomListListener {
            override fun onUpdate(rooms: List<Room>) {
                latest.set(rooms)
            }
        })
        try {
            client.startSync()
            withTimeoutOrNull(SYNC_WINDOW_MS) {
                while (latest.get().isNullOrEmpty()) delay(250)
                delay(SETTLE_MS)
            }
        } finally {
            handle.cancel()
            handle.close()
        }
        return latest.get()
    }

    private fun Room.asNotification(): NotificationInfo {
        val message = lastMessage!!
        return NotificationInfo(
            roomId = id,
            eventId = "",
            roomName = name,
            roomAvatar = avatarUrl,
            senderId = message.senderId,
            senderName = message.senderName,
            senderAvatar = null,
            isDirect = isDirect,
            isInvite = isInvite,
            content = message.content,
        )
    }

    companion object {
        private const val WORK = "message-check"
        private const val STORE = "message_checks"
        private const val SYNC_WINDOW_MS = 25_000L
        private const val SETTLE_MS = 3_000L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MessageCheckWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
            context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().clear().apply()
        }
    }
}
