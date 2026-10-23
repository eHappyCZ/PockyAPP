package com.pokecio.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pokecio.app.PockyApp
import com.pokecio.app.core.PockyException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class PushService : FirebaseMessagingService() {

    private val app get() = application as PockyApp

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        runBlocking {
            withTimeoutOrNull(TIMEOUT_MS) {
                val client = runCatching { app.clients.current() }.getOrNull() ?: return@withTimeoutOrNull
                PushRegistrar.register(client, token)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val roomId = message.data["room_id"] ?: return
        val eventId = message.data["event_id"] ?: return
        if (roomId == app.visibleRoomId) return

        runBlocking {
            withTimeoutOrNull(TIMEOUT_MS) {
                try {
                    val client = app.clients.current() ?: return@withTimeoutOrNull
                    val info = client.notification(roomId, eventId) ?: return@withTimeoutOrNull
                    val preview = client.notificationText()
                    val canReply = runCatching { !client.appLock().enabled }.getOrDefault(false)
                    app.notifier.show(info, preview, canReply)
                } catch (_: PockyException) {
                }
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 18_000L
    }
}
