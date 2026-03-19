package com.projectember.mobile.sync

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.projectember.mobile.EmberApplication
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.Duration

/**
 * Lightweight daily scheduler that uses AlarmManager to trigger an in-app sync receiver.
 * This avoids requiring WorkManager in environments where it's not available.
 */
class HealthSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext as? EmberApplication ?: return
        val manager = app.healthConnectManager
        // Run sync in background coroutine
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!manager.isAvailable()) return@launch
                val granted = try { manager.getGrantedPermissions() } catch (_: Exception) { return@launch }
                if (granted.isEmpty()) return@launch
                manager.syncFromHealthConnect(lookbackDays = 7L)
            } catch (_: Exception) {
                // Swallow; nothing actionable in receiver
            }
        }
    }

    companion object {
        private const val ACTION_SYNC = "com.projectember.mobile.action.HEALTH_DAILY_SYNC"

        fun scheduleDaily(context: Context, hourLocal: Int = 10) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, HealthSyncReceiver::class.java).apply { action = ACTION_SYNC }
            val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val initialDelay = calculateInitialDelayToHour(hourLocal)
            val triggerAt = System.currentTimeMillis() + initialDelay.toMillis()

            // Use setInexactRepeating to be battery-friendly; exact timing is not required.
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                AlarmManager.INTERVAL_DAY,
                pending
            )
        }

        private fun calculateInitialDelayToHour(hourLocal: Int): Duration {
            return try {
                val now = ZonedDateTime.now()
                var target = now.withHour(hourLocal).withMinute(0).withSecond(0).withNano(0)
                if (target.isBefore(now)) target = target.plusDays(1)
                Duration.between(now, target)
            } catch (e: Exception) {
                Duration.ofHours(1)
            }
        }
    }
}

