package com.pokecio.app.push

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.pokecio.app.PockyApp
import com.pokecio.app.core.NotificationInfo
import com.pokecio.app.core.PockyException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class NotificationActions : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as PockyApp
        val roomId = intent.getStringExtra(EXTRA_ROOM_ID) ?: return
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID)?.takeIf { it.isNotBlank() }
        val reply = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_REPLY)
            ?.toString()
            ?.trim()
            .orEmpty()

        val pending = goAsync()
        scope.launch {
            try {
                withTimeoutOrNull(TIMEOUT_MS) {
                    val client = app.clients.current() ?: return@withTimeoutOrNull
                    val timeline = client.timeline(roomId)
                    try {
                        if (intent.action == REPLY && reply.isNotEmpty()) {
                            if (eventId != null) timeline.sendReply(reply, eventId) else timeline.sendText(reply)
                        }
                        timeline.markAsRead()
                    } finally {
                        timeline.close()
                    }
                }
            } catch (_: PockyException) {
            } finally {
                app.notifier.cancel(roomId)
                pending.finish()
            }
        }
    }

    companion object {
        const val REPLY = "com.pokecio.app.action.REPLY"
        const val READ = "com.pokecio.app.action.READ"
        const val KEY_REPLY = "reply"

        private const val EXTRA_ROOM_ID = "room_id"
        private const val EXTRA_EVENT_ID = "event_id"
        private const val TIMEOUT_MS = 9_000L
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun pendingIntent(context: Context, action: String, info: NotificationInfo, mutable: Boolean): PendingIntent {
            val intent = Intent(context, NotificationActions::class.java)
                .setAction(action)
                .putExtra(EXTRA_ROOM_ID, info.roomId)
                .putExtra(EXTRA_EVENT_ID, info.eventId)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getBroadcast(context, (action + info.roomId).hashCode(), intent, flags)
        }
    }
}
