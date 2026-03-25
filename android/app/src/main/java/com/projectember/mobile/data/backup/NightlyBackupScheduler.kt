package com.projectember.mobile.data.backup

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Schedules [NightlyBackupWorker] to run once per day at approximately
 * [NightlyBackupStore.BACKUP_HOUR]:[NightlyBackupStore.BACKUP_MINUTE] local time.
 *
 * Uses WorkManager periodic work with an initial delay calculated to the next
 * occurrence of that time.  WorkManager handles Doze mode and device restarts
 * internally, so no manifest BroadcastReceiver is needed.
 *
 * Policy: [ExistingPeriodicWorkPolicy.KEEP] — if the work is already enqueued
 * (e.g. from a previous app launch) it is left untouched, so the firing time
 * is not reset on every app open.
 */
object NightlyBackupScheduler {

    private const val WORK_NAME = "ember_nightly_backup"

    fun schedule(context: Context) {
        val now    = LocalDateTime.now()
        val target = now.toLocalDate()
            .atTime(NightlyBackupStore.BACKUP_HOUR, NightlyBackupStore.BACKUP_MINUTE)
        val nextTarget = if (now.isBefore(target)) target else target.plusDays(1)
        val initialDelayMs = java.time.Duration.between(now, nextTarget).toMillis()
            .coerceAtLeast(0L)

        val request = PeriodicWorkRequestBuilder<NightlyBackupWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
