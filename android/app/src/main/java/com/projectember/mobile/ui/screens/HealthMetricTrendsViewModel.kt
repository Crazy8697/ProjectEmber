package com.projectember.mobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.local.HealthMetric
import com.projectember.mobile.data.local.HealthMetricPreferencesStore
import com.projectember.mobile.data.local.entities.ManualHealthEntry
import com.projectember.mobile.data.repository.ManualHealthEntryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * ViewModel for [HealthMetricTrendsScreen].
 *
 * Owns:
 * - The flow of manual entries for the given [metric], newest first.
 * - The graph-enabled flag for the metric.
 * - Save / delete operations for manual entries.
 */
class HealthMetricTrendsViewModel(
    val metric: HealthMetric,
    private val repository: ManualHealthEntryRepository,
    private val metricPrefs: HealthMetricPreferencesStore,
) : ViewModel() {

    /** All manual entries for this metric, newest first. */
    val entries: StateFlow<List<ManualHealthEntry>> =
        repository.getAllForMetric(metric.name)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    /** Whether the graph should be shown for this metric. */
    val graphEnabled: StateFlow<Boolean> =
        metricPrefs.graphSettingsFlow
            .map { it[metric] ?: false }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = metricPrefs.isMetricGraphEnabled(metric)
            )

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

    fun deleteEntry(entry: ManualHealthEntry) {
        viewModelScope.launch { repository.delete(entry) }
    }
}

class HealthMetricTrendsViewModelFactory(
    private val metric: HealthMetric,
    private val repository: ManualHealthEntryRepository,
    private val metricPrefs: HealthMetricPreferencesStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HealthMetricTrendsViewModel(metric, repository, metricPrefs) as T
}
