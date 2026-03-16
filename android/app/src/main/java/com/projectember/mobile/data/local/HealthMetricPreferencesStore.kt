package com.projectember.mobile.data.local

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "health_metric_prefs"

/**
 * Persists the per-metric enable/disable toggle states for Health Connect metrics.
 * All metrics default to enabled (true) on first run.
 *
 * Exposes a [settingsFlow] so observing composables/ViewModels react to changes immediately.
 */
class HealthMetricPreferencesStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadAll())
    val settingsFlow: Flow<Map<HealthMetric, Boolean>> = _settingsFlow.asStateFlow()

    fun isMetricEnabled(metric: HealthMetric): Boolean =
        _settingsFlow.value[metric] ?: true

    fun setMetricEnabled(metric: HealthMetric, enabled: Boolean) {
        prefs.edit().putBoolean(metric.prefKey, enabled).apply()
        _settingsFlow.value = _settingsFlow.value.toMutableMap().also { it[metric] = enabled }
    }

    fun getAllSettings(): Map<HealthMetric, Boolean> = _settingsFlow.value

    private fun loadAll(): Map<HealthMetric, Boolean> =
        HealthMetric.entries.associateWith { prefs.getBoolean(it.prefKey, true) }
}

/**
 * Enumeration of all Health Connect metrics that the user can enable or disable.
 *
 * @param prefKey  SharedPreferences key for this metric's toggle state.
 * @param displayName  Human-readable label shown in Settings.
 */
enum class HealthMetric(val prefKey: String, val displayName: String) {
    // Keto screen
    WEIGHT("metric_weight", "Weight"),
    // Exercise screen
    STEPS("metric_steps", "Steps"),
    DISTANCE("metric_distance", "Distance"),
    ACTIVE_CALORIES("metric_active_calories", "Active / Total Calories Burned"),
    EXERCISE_SESSIONS("metric_exercise_sessions", "Exercise Sessions"),
    // Health screen
    HEART_RATE("metric_heart_rate", "Heart Rate"),
    RESTING_HEART_RATE("metric_resting_heart_rate", "Resting Heart Rate"),
    SLEEP("metric_sleep", "Sleep"),
    BLOOD_PRESSURE("metric_blood_pressure", "Blood Pressure"),
    BLOOD_GLUCOSE("metric_blood_glucose", "Blood Glucose"),
    BODY_TEMPERATURE("metric_body_temperature", "Body Temperature"),
    OXYGEN_SATURATION("metric_oxygen_saturation", "Oxygen Saturation"),
    RESPIRATORY_RATE("metric_respiratory_rate", "Respiratory Rate"),
}
