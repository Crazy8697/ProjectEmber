package com.projectember.mobile.data.backup

import android.content.Context

/**
 * SharedPreferences store for nightly-backup user preferences and last-run state.
 */
class NightlyBackupStore(context: Context) {

    private val prefs = context.getSharedPreferences("nightly_backup", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ENABLED   = "auto_backup_enabled"
        private const val KEY_RETENTION = "retention_count"
        private const val KEY_LAST_MS   = "last_success_ms"

        const val DEFAULT_RETENTION = 3
        const val BACKUP_HOUR       = 23
        const val BACKUP_MINUTE     = 59
    }

    fun isAutoBackupEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)
    fun setAutoBackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Returns 2 or 3 (clamped). */
    fun getRetentionCount(): Int = prefs.getInt(KEY_RETENTION, DEFAULT_RETENTION).coerceIn(2, 3)
    fun setRetentionCount(count: Int) {
        prefs.edit().putInt(KEY_RETENTION, count.coerceIn(2, 3)).apply()
    }

    /** Epoch-milliseconds of the last successful backup, or 0 if never. */
    fun getLastSuccessMs(): Long = prefs.getLong(KEY_LAST_MS, 0L)
    fun setLastSuccessMs(ms: Long) {
        prefs.edit().putLong(KEY_LAST_MS, ms).apply()
    }
}
