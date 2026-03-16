package com.projectember.mobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.local.HealthMetric
import com.projectember.mobile.data.local.HealthMetricPreferencesStore
import com.projectember.mobile.sync.ActivitySummary
import com.projectember.mobile.sync.HealthConnectManager
import com.projectember.mobile.sync.HealthConnectUiState
import com.projectember.mobile.sync.HealthMetricsSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State for the async Health Connect data fetch on the Health screen. */
sealed class HealthDataState {
    data object Loading : HealthDataState()
    data object NotInstalled : HealthDataState()
    data class Ready(val snapshot: HealthMetricsSnapshot) : HealthDataState()
    data class Error(val message: String) : HealthDataState()
}

/** State for the async activity summary fetch on the Exercise screen. */
sealed class ActivityDataState {
    data object Loading : ActivityDataState()
    data object NotInstalled : ActivityDataState()
    data class Ready(val summary: ActivitySummary, val grantedPermissions: Set<String>) : ActivityDataState()
    data class Error(val message: String) : ActivityDataState()
}

class HealthViewModel(
    private val healthConnectManager: HealthConnectManager,
    private val metricPrefs: HealthMetricPreferencesStore,
) : ViewModel() {

    // ── Metric toggle states ──────────────────────────────────────────────────

    val enabledMetrics: StateFlow<Map<HealthMetric, Boolean>> =
        metricPrefs.settingsFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = metricPrefs.getAllSettings()
            )

    fun isMetricEnabled(metric: HealthMetric): Boolean =
        enabledMetrics.value[metric] ?: true

    // ── Health Connect UI state ───────────────────────────────────────────────

    private val _hcState = MutableStateFlow<HealthConnectUiState>(HealthConnectUiState.Checking)
    val hcState: StateFlow<HealthConnectUiState> = _hcState.asStateFlow()

    // ── Health metrics data ───────────────────────────────────────────────────

    private val _healthData = MutableStateFlow<HealthDataState>(HealthDataState.Loading)
    val healthData: StateFlow<HealthDataState> = _healthData.asStateFlow()

    // ── Activity summary (for Exercise screen) ────────────────────────────────

    private val _activityData = MutableStateFlow<ActivityDataState>(ActivityDataState.Loading)
    val activityData: StateFlow<ActivityDataState> = _activityData.asStateFlow()

    // ── Initialisation ────────────────────────────────────────────────────────

    /**
     * Called when the Health screen is (re-)entered.
     * Refreshes the HC availability check and fetches the latest health metrics.
     */
    fun refreshHealthScreen() {
        viewModelScope.launch {
            _hcState.value = HealthConnectUiState.Checking
            if (!healthConnectManager.isAvailable()) {
                _hcState.value = HealthConnectUiState.NotInstalled
                _healthData.value = HealthDataState.NotInstalled
                return@launch
            }
            _hcState.value = HealthConnectUiState.Ready
            _healthData.value = HealthDataState.Loading
            try {
                val snapshot = healthConnectManager.readLatestHealthMetrics()
                _healthData.value = HealthDataState.Ready(snapshot)
            } catch (e: Exception) {
                _healthData.value = HealthDataState.Error(
                    e.message ?: "Could not read Health Connect data"
                )
            }
        }
    }

    /**
     * Called when the Exercise screen is (re-)entered.
     * Refreshes the HC availability check and fetches today's activity summary.
     */
    fun refreshActivitySummary() {
        viewModelScope.launch {
            if (!healthConnectManager.isAvailable()) {
                _activityData.value = ActivityDataState.NotInstalled
                return@launch
            }
            _activityData.value = ActivityDataState.Loading
            try {
                val summary = healthConnectManager.readTodayActivitySummary()
                val granted = healthConnectManager.getGrantedPermissions()
                _activityData.value = ActivityDataState.Ready(summary, granted)
            } catch (e: Exception) {
                _activityData.value = ActivityDataState.Error(
                    e.message ?: "Could not read activity data"
                )
            }
        }
    }

    /** Called after a permission result — re-fetches all data. */
    fun onPermissionsResult() {
        refreshHealthScreen()
        refreshActivitySummary()
    }
}

class HealthViewModelFactory(
    private val healthConnectManager: HealthConnectManager,
    private val metricPrefs: HealthMetricPreferencesStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HealthViewModel(healthConnectManager, metricPrefs) as T
}
