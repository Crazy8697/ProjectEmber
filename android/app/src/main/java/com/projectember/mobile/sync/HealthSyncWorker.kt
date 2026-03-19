package com.projectember.mobile.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Lightweight periodic worker that triggers a Health Connect sync once per day.
 * Scheduled to run roughly around 10:00 local time by calculating an initial delay.
 */
class HealthSyncWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        try {
            // Obtain a HealthConnectManager instance through EmberApplication if available
            val app = ctx.applicationContext as? EmberApplication
                ?: return Result.success()
            val manager = app.healthConnectManager

            // Only run when Health Connect is available and permissions are granted
            if (!manager.isAvailable()) return Result.success()
            val granted = try { manager.getGrantedPermissions() } catch (_: Exception) { return Result.success() }
            if (granted.isEmpty()) return Result.success()

            // Perform a short lookback sync (7 days) to keep recent data fresh but avoid heavy reads
            manager.syncFromHealthConnect(lookbackDays = 7L)
            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "health_daily_sync"

        fun scheduleDaily(context: Context) {
            val workManager = WorkManager.getInstance(context)

            // Create a periodic request that repeats every 24 hours.
            // Rely on WorkManager's flex window to run roughly near the desired time.
            val request = PeriodicWorkRequestBuilder<HealthSyncWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(calculateInitialDelayToHour(10))
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        private fun calculateInitialDelayToHour(hourLocal: Int): Duration {
            // Calculate approximate initial delay (in case device just booted). We'll use a best-effort approach.
            try {
                val now = java.time.ZonedDateTime.now()
                var target = now.withHour(hourLocal).withMinute(0).withSecond(0).withNano(0)
                if (target.isBefore(now)) target = target.plusDays(1)
                val delayMillis = java.time.Duration.between(now, target).toMillis()
                return Duration.ofMillis(delayMillis)
            } catch (e: Exception) {
                return Duration.ofHours(1)
            }
        }
    }
}

