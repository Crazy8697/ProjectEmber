package com.projectember.mobile.data.local

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "health_metric_prefs"

/**
 * Persists the per-metric toggle states for Health Connect metrics.
 *
 * Each metric has two independent flags:
 * - **enabled** (visible): whether the metric card is shown at all.
 *   Defaults to `true` on first run.
 * - **showGraph**: whether the trend/graph section is surfaced on the metric's
 *   screen.  Defaults to `false` on first run (cards only by default).
 *
 * Exposes [settingsFlow] and [graphSettingsFlow] so observing composables/ViewModels
 * react to changes immediately.
 */
class HealthMetricPreferencesStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadAll())
    val settingsFlow: Flow<Map<HealthMetric, Boolean>> = _settingsFlow.asStateFlow()

    private val _graphSettingsFlow = MutableStateFlow(loadAllGraph())
    val graphSettingsFlow: Flow<Map<HealthMetric, Boolean>> = _graphSettingsFlow.asStateFlow()

    fun isMetricEnabled(metric: HealthMetric): Boolean =
        _settingsFlow.value[metric] ?: true

    fun setMetricEnabled(metric: HealthMetric, enabled: Boolean) {
        prefs.edit().putBoolean(metric.prefKey, enabled).apply()
        _settingsFlow.value = _settingsFlow.value.toMutableMap().also { it[metric] = enabled }
    }

    fun isMetricGraphEnabled(metric: HealthMetric): Boolean =
        _graphSettingsFlow.value[metric] ?: false

    fun setMetricGraphEnabled(metric: HealthMetric, enabled: Boolean) {
        prefs.edit().putBoolean(metric.graphPrefKey, enabled).apply()
        _graphSettingsFlow.value = _graphSettingsFlow.value.toMutableMap().also { it[metric] = enabled }
    }

    fun getAllSettings(): Map<HealthMetric, Boolean> = _settingsFlow.value

    fun getAllGraphSettings(): Map<HealthMetric, Boolean> = _graphSettingsFlow.value

    private fun loadAll(): Map<HealthMetric, Boolean> =
        HealthMetric.entries.associateWith { prefs.getBoolean(it.prefKey, true) }

    private fun loadAllGraph(): Map<HealthMetric, Boolean> =
        HealthMetric.entries.associateWith { prefs.getBoolean(it.graphPrefKey, false) }
}

/**
 * Enumeration of all Health Connect metrics that the user can enable or disable.
 *
 * @param prefKey  SharedPreferences key for this metric's visibility toggle.
 * @param graphPrefKey  SharedPreferences key for the graph/trend toggle.
 * @param displayName  Human-readable label shown in Settings.
 */
enum class HealthMetric(val prefKey: String, val graphPrefKey: String, val displayName: String) {
    // Keto screen
    WEIGHT("metric_weight", "metric_weight_graph", "Weight"),
    // Exercise screen
    STEPS("metric_steps", "metric_steps_graph", "Steps"),
    DISTANCE("metric_distance", "metric_distance_graph", "Distance"),
    ACTIVE_CALORIES("metric_active_calories", "metric_active_calories_graph", "Active / Total Calories Burned"),
    EXERCISE_SESSIONS("metric_exercise_sessions", "metric_exercise_sessions_graph", "Exercise Sessions"),
    // Health screen
    HEART_RATE("metric_heart_rate", "metric_heart_rate_graph", "Heart Rate"),
    RESTING_HEART_RATE("metric_resting_heart_rate", "metric_resting_heart_rate_graph", "Resting Heart Rate"),
    SLEEP("metric_sleep", "metric_sleep_graph", "Sleep"),
    BLOOD_PRESSURE("metric_blood_pressure", "metric_blood_pressure_graph", "Blood Pressure"),
    BLOOD_GLUCOSE("metric_blood_glucose", "metric_blood_glucose_graph", "Blood Glucose"),
    BODY_TEMPERATURE("metric_body_temperature", "metric_body_temperature_graph", "Body Temperature"),
    OXYGEN_SATURATION("metric_oxygen_saturation", "metric_oxygen_saturation_graph", "Oxygen Saturation"),
    RESPIRATORY_RATE("metric_respiratory_rate", "metric_respiratory_rate_graph", "Respiratory Rate"),
}

