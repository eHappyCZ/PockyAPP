package com.pokecio.app.presence

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pokecio.app.PockyApp
import com.pokecio.app.core.PockyException
import java.util.concurrent.TimeUnit

class StatusExpiryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PockyApp
        return try {
            val client = app.clients.current() ?: return Result.success()
            client.expireStatus(System.currentTimeMillis().toULong())
            Result.success()
        } catch (_: PockyException) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK = "status-expiry"

        fun schedule(context: Context, untilMs: Long?) {
            val manager = WorkManager.getInstance(context)
            if (untilMs == null) {
                manager.cancelUniqueWork(WORK)
                return
            }
            val delay = (untilMs - System.currentTimeMillis()).coerceAtLeast(0)
            val request = OneTimeWorkRequestBuilder<StatusExpiryWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            manager.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
