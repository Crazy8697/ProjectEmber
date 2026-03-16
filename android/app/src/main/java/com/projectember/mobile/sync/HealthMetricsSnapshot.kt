package com.projectember.mobile.sync

/**
 * A snapshot of the latest health-metric values read directly from Health Connect.
 *
 * Each field is nullable: null means either the permission is missing or no data exists for
 * that metric.  Use [grantedPermissions] to distinguish the two cases in the UI.
 */
data class HealthMetricsSnapshot(
    /** Set of HC permission strings that are currently granted. */
    val grantedPermissions: Set<String>,

    // ── Vitals ────────────────────────────────────────────────────────────────

    val heartRateBpm: Long? = null,
    val heartRateTimestamp: String? = null,

    val restingHeartRateBpm: Long? = null,
    val restingHeartRateTimestamp: String? = null,

    val bloodPressureSystolicMmHg: Double? = null,
    val bloodPressureDiastolicMmHg: Double? = null,
    val bloodPressureTimestamp: String? = null,

    /** Plasma glucose in mmol/L. */
    val bloodGlucoseMmol: Double? = null,
    val bloodGlucoseTimestamp: String? = null,

    /** Body temperature in °C. */
    val bodyTemperatureCelsius: Double? = null,
    val bodyTemperatureTimestamp: String? = null,

    /** SpO₂ percentage (0–100). */
    val oxygenSaturationPct: Double? = null,
    val oxygenSaturationTimestamp: String? = null,

    /** Breaths per minute. */
    val respiratoryRateBreath: Double? = null,
    val respiratoryRateTimestamp: String? = null,

    // ── Sleep ─────────────────────────────────────────────────────────────────

    val latestSleepSession: SleepSessionSummary? = null,
)

data class SleepSessionSummary(
    /** e.g. "2025-03-15" */
    val date: String,
    /** Human-readable duration, e.g. "7h 32m". */
    val durationDisplay: String,
    /** Total sleep duration in decimal hours, for compact display. */
    val durationHours: Double,
)

/**
 * Summary of today's activity metrics read from Health Connect.
 * Each field is null if the permission is missing or no data was recorded today.
 */
data class ActivitySummary(
    val stepsToday: Long? = null,
    val distanceMeters: Double? = null,
    val activeCaloriesKcal: Double? = null,
    val totalCaloriesKcal: Double? = null,
)
