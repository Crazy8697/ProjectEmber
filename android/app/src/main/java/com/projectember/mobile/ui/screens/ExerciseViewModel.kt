package com.projectember.mobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.local.HealthMetric
import com.projectember.mobile.data.local.HealthMetricPreferencesStore
import com.projectember.mobile.data.local.entities.ExerciseCategory
import com.projectember.mobile.data.local.entities.ExerciseEntry
import com.projectember.mobile.data.local.entities.ManualHealthEntry
import com.projectember.mobile.data.repository.ExerciseCategoryRepository
import com.projectember.mobile.data.repository.ExerciseRepository
import com.projectember.mobile.data.repository.ManualHealthEntryRepository
import com.projectember.mobile.sync.HealthConnectManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class ExerciseViewModel(
    private val exerciseRepository: ExerciseRepository,
    private val categoryRepository: ExerciseCategoryRepository,
    private val healthConnectManager: HealthConnectManager,
    private val healthMetricPreferencesStore: HealthMetricPreferencesStore,
    private val manualHealthEntryRepository: ManualHealthEntryRepository,
) : ViewModel() {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val today: String = LocalDate.now().format(dateFormatter)

    private val _selectedDate = MutableStateFlow(today)
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    fun setSelectedDate(date: String) {
        _selectedDate.value = date
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedDateEntries: StateFlow<List<ExerciseEntry>> = _selectedDate
        .flatMapLatest { date -> exerciseRepository.getEntriesForDate(date) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val categories: StateFlow<List<ExerciseCategory>> = categoryRepository
        .getAllCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // ── Health Connect activity summary ───────────────────────────────────────

    private val _activityData = MutableStateFlow<ActivityDataState>(ActivityDataState.Loading)
    val activityData: StateFlow<ActivityDataState> = _activityData.asStateFlow()

    val enabledMetrics: StateFlow<Map<HealthMetric, Boolean>> =
        healthMetricPreferencesStore.settingsFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = healthMetricPreferencesStore.getAllSettings()
            )

    /** Call when the Exercise screen is entered to refresh HC activity data. */
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

    /** Save a manual health entry for exercise-related metrics. */
    fun saveManualHealthEntry(
        metric: HealthMetric,
        value1: Double,
        value2: Double?,
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
}

class ExerciseViewModelFactory(
    private val exerciseRepository: ExerciseRepository,
    private val categoryRepository: ExerciseCategoryRepository,
    private val healthConnectManager: HealthConnectManager,
    private val healthMetricPreferencesStore: HealthMetricPreferencesStore,
    private val manualHealthEntryRepository: ManualHealthEntryRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ExerciseViewModel(
            exerciseRepository,
            categoryRepository,
            healthConnectManager,
            healthMetricPreferencesStore,
            manualHealthEntryRepository,
        ) as T
}
