package com.projectember.mobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.local.HealthMetric
import com.projectember.mobile.data.local.HealthMetricPreferencesStore
import com.projectember.mobile.data.local.entities.ManualHealthEntry
import com.projectember.mobile.data.repository.ManualHealthEntryRepository
import com.projectember.mobile.sync.HealthConnectManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Loading state for the Health Connect historical read. */
enum class HcLoadState { Idle, Loading, Done, Unavailable, NoPermission }

/**
 * ViewModel for [HealthMetricTrendsScreen].
 *
 * Owns:
 * - The flow of manual entries for the given [metric], newest first.
 * - HC historical entries for the selected date range (in-memory, never persisted).
 * - A combined [entries] StateFlow (manual + HC), newest first.
 * - The graph-enabled flag for the metric.
 * - Save / delete operations for manual entries.
 *   HC entries are read-only and cannot be deleted.
 */
class HealthMetricTrendsViewModel(
    val metric: HealthMetric,
    private val repository: ManualHealthEntryRepository,
    private val metricPrefs: HealthMetricPreferencesStore,
    private val healthConnectManager: HealthConnectManager,
) : ViewModel() {

    // ── Manual entries from Room ──────────────────────────────────────────────

    private val _manualEntries: StateFlow<List<ManualHealthEntry>> =
        repository.getAllForMetric(metric.name)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    // ── Health Connect historical entries (in-memory) ─────────────────────────

    private val _hcEntries = MutableStateFlow<List<ManualHealthEntry>>(emptyList())

    private val _hcLoadState = MutableStateFlow(HcLoadState.Idle)
    val hcLoadState: StateFlow<HcLoadState> = _hcLoadState.asStateFlow()

    // ── Combined entries: manual + HC, newest first ───────────────────────────

    /**
     * All entries for this metric — manual Ember entries **and** Health Connect historical
     * entries merged together, sorted newest-first.
     *
     * Manual entries always appear for their exact timestamps.  HC entries for the same
     * date are also included so the full historical picture is visible.  The graph view
     * uses manual values with priority when building chart points.
     */
    val entries: StateFlow<List<ManualHealthEntry>> =
        combine(_manualEntries, _hcEntries) { manual, hc ->
            (manual + hc).sortedWith(
                compareByDescending<ManualHealthEntry> { it.entryDate }
                    .thenByDescending { it.entryTime }
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // ── Graph enabled ─────────────────────────────────────────────────────────

    /** Whether the graph toggle is enabled for this metric (from Settings). */
    val graphEnabled: StateFlow<Boolean> =
        metricPrefs.graphSettingsFlow
            .map { it[metric] ?: false }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = metricPrefs.isMetricGraphEnabled(metric)
            )

    // ── Date range ────────────────────────────────────────────────────────────

    private val _fromDate = MutableStateFlow(
        LocalDate.now().minusDays(30).format(DateTimeFormatter.ISO_LOCAL_DATE)
    )
    val fromDate: StateFlow<String> = _fromDate.asStateFlow()

    private val _toDate = MutableStateFlow(
        LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    )
    val toDate: StateFlow<String> = _toDate.asStateFlow()

    fun setFromDate(date: String) {
        _fromDate.value = date
    }

    fun setToDate(date: String) {
        _toDate.value = date
    }

    // ── Init: trigger HC history load when date range changes ─────────────────

    init {
        viewModelScope.launch {
            combine(_fromDate, _toDate) { from, to -> from to to }
                .collect { (from, to) ->
                    loadHcHistory(from, to)
                }
        }
    }

    private suspend fun loadHcHistory(fromDate: String, toDate: String) {
        if (!healthConnectManager.isAvailable()) {
            _hcLoadState.value = HcLoadState.Unavailable
            return
        }
        _hcLoadState.value = HcLoadState.Loading
        try {
            val imported = healthConnectManager.readHistoricalMetrics(metric, fromDate, toDate)
            _hcEntries.value = imported
            _hcLoadState.value = HcLoadState.Done
        } catch (_: Exception) {
            // If HC read fails for any reason (permission revoked mid-session, etc.)
            // we silently leave any previously loaded HC entries in place and mark as done.
            _hcLoadState.value = HcLoadState.Done
        }
    }

    // ── Edit / delete ─────────────────────────────────────────────────────────

    fun saveEntry(
        value1: Double,
        value2: Double?,
        entryDate: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
        entryTime: String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
    ) {
        viewModelScope.launch {
            repository.insert(
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

    /** Deletes a manual Ember entry.  HC-imported entries are silently ignored. */
    fun deleteEntry(entry: ManualHealthEntry) {
        if (entry.source == ManualHealthEntry.SOURCE_MANUAL) {
            viewModelScope.launch { repository.delete(entry) }
        }
        // HC entries are read-only — never deleted from HC or from Ember's in-memory list here
    }
}

class HealthMetricTrendsViewModelFactory(
    private val metric: HealthMetric,
    private val repository: ManualHealthEntryRepository,
    private val metricPrefs: HealthMetricPreferencesStore,
    private val healthConnectManager: HealthConnectManager,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HealthMetricTrendsViewModel(metric, repository, metricPrefs, healthConnectManager) as T
}
