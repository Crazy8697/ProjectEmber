package com.projectember.mobile.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.projectember.mobile.EmberApplication

/**
 * WorkManager [CoroutineWorker] that executes the nightly automatic backup.
 *
 * Scheduled by [NightlyBackupScheduler] to fire once per day near 23:59 local time.
 * Checks whether auto-backup is enabled before running; if disabled, exits immediately
 * with success so WorkManager does not treat it as a failure.
 *
 * All actual backup logic lives in [NightlyBackupEngine] so that the manual
 * "Backup Now" path in the SettingsViewModel can reuse the same implementation.
 */
class NightlyBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? EmberApplication ?: return Result.failure()

        if (!app.nightlyBackupStore.isAutoBackupEnabled()) {
            return Result.success()   // disabled — skip silently
        }

        return app.nightlyBackupEngine.performBackup()
            .fold(
                onSuccess = { Result.success() },
                onFailure = { Result.failure() }  // existing backups are untouched on failure
            )
    }
}
