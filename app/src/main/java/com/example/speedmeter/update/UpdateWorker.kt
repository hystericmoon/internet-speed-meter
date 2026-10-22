package com.example.speedmeter.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.speedmeter.AppPrefs
import java.util.concurrent.TimeUnit

/**
 * Periodic background update: downloads and installs new builds. Silent when Android allows
 * it (Android 12+, after this app has installed itself once); otherwise ends in a notification.
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val info = runCatching { UpdateInstaller.fetchLatest() }.getOrElse { return Result.retry() }
        if (!info.isNewer) return Result.success()

        if (!applicationContext.packageManager.canRequestPackageInstalls()) {
            // Needs the one-time "install unknown apps" grant, which only the UI can ask for.
            UpdateNotifications.showAvailable(applicationContext, info)
            return Result.success()
        }
        return runCatching {
            val apk = UpdateInstaller.download(applicationContext, info) {}
            UpdateInstaller.install(applicationContext, apk)
        }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        private const val WORK_NAME = "auto_update"

        /** Schedules or cancels the periodic check to match the user's setting. */
        fun sync(context: Context) {
            val workManager = WorkManager.getInstance(context)
            if (!AppPrefs.autoUpdate(context)) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
