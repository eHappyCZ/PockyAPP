package com.pokecio.app.update

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pokecio.app.PockyApp
import com.pokecio.app.core.PockyException
import com.pokecio.app.core.checkForUpdate
import com.pokecio.app.ui.rooms.AppLocale
import java.util.concurrent.TimeUnit

class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PockyApp
        val prefs = UpdateController.prefs(app)
        if (!UpdateController.isAutomatic(prefs) || app.inForeground) return Result.success()

        val update = try {
            checkForUpdate(UpdateController.installedCode(app), Build.VERSION.SDK_INT.toUInt(), AppLocale.language)
        } catch (_: PockyException) {
            return Result.retry()
        } ?: return Result.success()

        val code = update.versionCode.toLong()
        if (UpdateController.snoozed(prefs, code) || code == UpdateController.notified(prefs)) {
            return Result.success()
        }
        if (app.notifier.showUpdate(update.versionName)) {
            UpdateController.markNotified(prefs, code)
        }
        return Result.success()
    }

    companion object {
        private const val WORK = "update-check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
        }
    }
}
