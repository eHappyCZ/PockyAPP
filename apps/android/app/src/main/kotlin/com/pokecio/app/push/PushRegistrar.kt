package com.pokecio.app.push

import android.os.Build
import com.google.firebase.messaging.FirebaseMessaging
import com.pokecio.app.core.PockyClient
import com.pokecio.app.core.PockyException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

object PushRegistrar {

    suspend fun register(client: PockyClient, token: String? = null): Boolean {
        val pushKey = token ?: fetchToken() ?: return false
        return try {
            client.registerPusher(pushKey, deviceName(), LANGUAGE)
            true
        } catch (_: PockyException) {
            false
        }
    }

    @Suppress("DEPRECATION")
    suspend fun fetchToken(): String? = suspendCancellableCoroutine { continuation ->
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                continuation.resume(if (task.isSuccessful) task.result else null)
            }
        } catch (_: IllegalStateException) {
            continuation.resume(null)
        }
    }

    private fun deviceName() = "PockyAPP (${Build.MODEL})"

    private const val LANGUAGE = "cs"
}
