package com.projectember.mobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.local.HealthMetric
import com.projectember.mobile.data.local.HealthMetricPreferencesStore
import com.projectember.mobile.data.local.entities.ManualHealthEntry
import com.projectember.mobile.data.repository.ManualHealthEntryRepository
import com.projectember.mobile.sync.ActivitySummary
import com.projectember.mobile.sync.HealthConnectManager
import com.projectember.mobile.sync.HealthConnectUiState
import com.projectember.mobile.sync.HealthMetricsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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
    private val manualHealthEntryRepository: ManualHealthEntryRepository,
) : ViewModel() {

    // ── Metric toggle states ──────────────────────────────────────────────────

    val enabledMetrics: StateFlow<Map<HealthMetric, Boolean>> =
        metricPrefs.settingsFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = metricPrefs.getAllSettings()
            )

    val graphEnabledMetrics: StateFlow<Map<HealthMetric, Boolean>> =
        metricPrefs.graphSettingsFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = metricPrefs.getAllGraphSettings()
            )

    fun isMetricEnabled(metric: HealthMetric): Boolean =
        enabledMetrics.value[metric] ?: true

    fun isMetricGraphEnabled(metric: HealthMetric): Boolean =
        graphEnabledMetrics.value[metric] ?: false

    // ── Health Connect UI state ───────────────────────────────────────────────

    private val _hcState = MutableStateFlow<HealthConnectUiState>(HealthConnectUiState.Checking)
    val hcState: StateFlow<HealthConnectUiState> = _hcState.asStateFlow()

    // ── Health metrics data ───────────────────────────────────────────────────

    private val _healthData = MutableStateFlow<HealthDataState>(HealthDataState.Loading)
    val healthData: StateFlow<HealthDataState> = _healthData.asStateFlow()

    // ── Activity summary (for Exercise screen) ────────────────────────────────

    private val _activityData = MutableStateFlow<ActivityDataState>(ActivityDataState.Loading)
    val activityData: StateFlow<ActivityDataState> = _activityData.asStateFlow()

    // ── Manual health entries ─────────────────────────────────────────────────

    /** Returns a flow of all manual entries for the given metric, newest first. */
    fun getManualEntriesForMetric(metric: HealthMetric): Flow<List<ManualHealthEntry>> =
        manualHealthEntryRepository.getAllForMetric(metric.name)

    /** Latest manual entry for a metric — for card overlay display. */
    fun getLatestManualEntryForMetric(metric: HealthMetric): Flow<ManualHealthEntry?> =
        manualHealthEntryRepository.getLatestForMetric(metric.name)

    /**
     * Save a manual health entry for the given metric.
     * [value1] is the primary value (BPM, °C, %, etc.).
     * [value2] is optional — used for blood pressure diastolic.
     * Date defaults to today; time defaults to now.
     */
    fun saveManualEntry(
        metric: HealthMetric,
        value1: Double,
        value2: Double? = null,
        entryDate: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
        entryTime: String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
    ) {
        viewModelScope.launch {
            manualHealthEntryRepository.insert(
                ManualHealthEntry(
                    metricType = metric.name,
                    value1 = value1,
                    value2 = value2,
                    entryDate = entryDate,
                    entryTime = entryTime,
                )
            )
        }
    }

    /** Delete a manual health entry. */
    fun deleteManualEntry(entry: ManualHealthEntry) {
        viewModelScope.launch { manualHealthEntryRepository.delete(entry) }
    }

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
    private val manualHealthEntryRepository: ManualHealthEntryRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HealthViewModel(healthConnectManager, metricPrefs, manualHealthEntryRepository) as T
}

