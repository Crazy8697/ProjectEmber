package com.projectember.mobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.local.HealthMetric
import com.projectember.mobile.data.local.HealthMetricPreferencesStore
import com.projectember.mobile.data.local.KetoTargets
import com.projectember.mobile.data.local.KetoTargetsStore
import com.projectember.mobile.data.local.UnitPreferences
import com.projectember.mobile.data.local.UnitsPreferencesStore
import com.projectember.mobile.data.local.entities.KetoEntry
import com.projectember.mobile.data.local.entities.WeightEntry
import com.projectember.mobile.data.local.entities.effectiveCalories
import com.projectember.mobile.data.local.entities.effectiveFat
import com.projectember.mobile.data.local.entities.effectiveMagnesium
import com.projectember.mobile.data.local.entities.effectiveNetCarbs
import com.projectember.mobile.data.local.entities.effectivePotassium
import com.projectember.mobile.data.local.entities.effectiveProtein
import com.projectember.mobile.data.local.entities.effectiveSodium
import com.projectember.mobile.data.local.entities.effectiveWater
import com.projectember.mobile.data.repository.ExerciseCategoryRepository
import com.projectember.mobile.data.repository.ExerciseRepository
import com.projectember.mobile.data.repository.KetoRepository
import com.projectember.mobile.data.repository.WeightRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class DayTotals(
    val label: String,
    val date: String,
    val calories: Double,
    val netCarbsG: Double,
    val proteinG: Double = 0.0,
    val fatG: Double = 0.0,
    val waterMl: Double = 0.0,
    val sodiumMg: Double = 0.0,
    val potassiumMg: Double = 0.0,
    val magnesiumMg: Double = 0.0,
    /** Weight logged on this day (kg), or null if none. */
    val weightKg: Double? = null,
    /** Na:K ratio (sodiumMg / potassiumMg) for the day, or null if potassiumMg == 0. */
    val nakRatio: Double? = null
)

class KetoViewModel(
    private val ketoRepository: KetoRepository,
    val targetsStore: KetoTargetsStore,
    private val weightRepository: WeightRepository,
    private val exerciseRepository: ExerciseRepository,
    private val exerciseCategoryRepository: ExerciseCategoryRepository,
    private val unitsPreferencesStore: UnitsPreferencesStore,
    private val healthMetricPreferencesStore: HealthMetricPreferencesStore,
) : ViewModel() {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val today: String = LocalDate.now().format(dateFormatter)
    private val sevenDaysAgo: String = LocalDate.now().minusDays(6).format(dateFormatter)

    private val _selectedDate = MutableStateFlow(today)
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    fun setSelectedDate(date: String) {
        _selectedDate.value = date
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedDateEntries: StateFlow<List<KetoEntry>> = _selectedDate
        .flatMapLatest { date ->
            combine(
                ketoRepository.getEntriesForDate(date),
                exerciseRepository.getEntriesForDate(date),
                exerciseCategoryRepository.getAllCategories()
            ) { ketoEntries, exerciseEntries, categories ->
                val categoryMap = categories.associateBy { it.id }
                // Map each ExerciseEntry to a KetoEntry using a negative id so the UI can
                // route edit taps to the ExerciseEditEntry screen instead of KetoEditEntry.
                val mappedExercise = exerciseEntries.map { ex ->
                    // Exercise entries fetched from Room always have a positive auto-generated id.
                    // We negate it to distinguish them from keto entries (also positive) in the
                    // UI routing layer. id = 0 cannot appear for persisted records, but we guard
                    // against it to avoid a routing collision with the default KetoEntry id.
                    val mappedId = if (ex.id > 0) -ex.id else Int.MIN_VALUE
                    // Use the category name as the primary label (matches ExerciseEntryCard).
                    // Fall back to entry.type when category lookup fails (e.g. deleted category).
                    val displayLabel = categoryMap[ex.categoryId]?.name ?: ex.type
                    KetoEntry(
                        id = mappedId,
                        label = displayLabel,
                        eventType = "exercise",
                        calories = ex.caloriesBurned ?: 0.0,
                        proteinG = 0.0,
                        fatG = 0.0,
                        netCarbsG = 0.0,
                        entryDate = ex.entryDate,
                        eventTimestamp = ex.timestamp,
                        notes = ex.notes
                    )
                }
                (ketoEntries + mappedExercise).sortedByDescending { it.eventTimestamp }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val targets: StateFlow<KetoTargets> = targetsStore.targets

    /** Latest logged body weight entry from Room. */
    val lastWeightEntry: StateFlow<WeightEntry?> = weightRepository
        .getLatestEntry()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    fun logWeight(weightKg: Double) {
        val date = LocalDate.now().format(dateFormatter)
        viewModelScope.launch(Dispatchers.IO) {
            // Enforce one-entry-per-day: replace any existing entry for today.
            weightRepository.upsertForDate(WeightEntry(entryDate = date, weightKg = weightKg))
        }
    }

    // ── Trends controls ──────────────────────────────────────────────────────
    private val _trendsMetric = MutableStateFlow("calories")
    val trendsMetric: StateFlow<String> = _trendsMetric.asStateFlow()

    private val _trendsFromDate = MutableStateFlow(sevenDaysAgo)
    val trendsFromDate: StateFlow<String> = _trendsFromDate.asStateFlow()

    private val _trendsToDate = MutableStateFlow(today)
    val trendsToDate: StateFlow<String> = _trendsToDate.asStateFlow()

    private val _trendsMode = MutableStateFlow("daily") // "daily" or "rolling"
    val trendsMode: StateFlow<String> = _trendsMode.asStateFlow()

    private val _trendsRollingDays = MutableStateFlow(3)
    val trendsRollingDays: StateFlow<Int> = _trendsRollingDays.asStateFlow()

    fun setTrendsMetric(metric: String) { _trendsMetric.value = metric }
    fun setTrendsFromDate(date: String) { _trendsFromDate.value = date }
    fun setTrendsToDate(date: String) { _trendsToDate.value = date }
    fun setTrendsMode(mode: String) { _trendsMode.value = mode }
    fun setTrendsRollingDays(days: Int) { _trendsRollingDays.value = days }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val trendsData: StateFlow<List<DayTotals>> =
        combine(_trendsFromDate, _trendsToDate) { from, to -> from to to }
            .flatMapLatest { (fromDate, toDate) ->
                combine(
                    ketoRepository.getEntriesFromDate(fromDate),
                    weightRepository.getEntriesInRange(fromDate, toDate)
                ) { ketoEntries, weightEntries ->
                    val from = try {
                        LocalDate.parse(fromDate, dateFormatter)
                    } catch (_: Exception) {
                        LocalDate.now().minusDays(6)
                    }
                    val to = try {
                        LocalDate.parse(toDate, dateFormatter)
                    } catch (_: Exception) {
                        LocalDate.now()
                    }
                    val numDays = ((to.toEpochDay() - from.toEpochDay() + 1)
                        .toInt()).coerceIn(1, 90)
                    val dayShortFmt = DateTimeFormatter.ofPattern("EEE")
                    // Build a map from date → latest weight for that day (by highest id).
                    val weightByDate: Map<String, Double> = weightEntries
                        .groupBy { it.entryDate }
                        .mapValues { (_, entries) ->
                            // groupBy guarantees each value list is non-empty, but use
                            // ?: 0.0 instead of !! to avoid a crash on future refactoring.
                            entries.maxByOrNull { it.id }?.weightKg ?: 0.0
                        }
                    (0 until numDays).map { offset ->
                        val date = from.plusDays(offset.toLong())
                        val dateStr = date.format(dateFormatter)
                        val dayEntries = ketoEntries.filter { it.entryDate == dateStr }
                        val sodiumMg  = dayEntries.sumOf { it.effectiveSodium() }
                        val potassiumMg = dayEntries.sumOf { it.effectivePotassium() }
                        DayTotals(
                            label = date.format(dayShortFmt),
                            date = dateStr,
                            calories = dayEntries.sumOf { it.effectiveCalories() }
                                .coerceAtLeast(0.0),
                            netCarbsG = dayEntries.sumOf { it.effectiveNetCarbs() },
                            proteinG = dayEntries.sumOf { it.effectiveProtein() },
                            fatG = dayEntries.sumOf { it.effectiveFat() },
                            waterMl = dayEntries.sumOf { it.effectiveWater() },
                            sodiumMg = sodiumMg,
                            potassiumMg = potassiumMg,
                            magnesiumMg = dayEntries.sumOf { it.effectiveMagnesium() },
                            weightKg = weightByDate[dateStr],
                            nakRatio = if (potassiumMg > 0) sodiumMg / potassiumMg else null
                        )
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    val weeklyData: StateFlow<List<DayTotals>> = ketoRepository
        .getEntriesFromDate(sevenDaysAgo)
        .map { entries ->
            val dayShortFmt = DateTimeFormatter.ofPattern("EEE")
            (0..6).map { offset ->
                val date = LocalDate.now().minusDays(6 - offset.toLong())
                val dateStr = date.format(dateFormatter)
                val dayEntries = entries.filter { it.entryDate == dateStr }
                val cals = dayEntries.sumOf { it.effectiveCalories() }
                val sodiumMg  = dayEntries.sumOf { it.effectiveSodium() }
                val potassiumMg = dayEntries.sumOf { it.effectivePotassium() }
                DayTotals(
                    label = date.format(dayShortFmt),
                    date = dateStr,
                    calories = cals.coerceAtLeast(0.0),
                    netCarbsG = dayEntries.sumOf { it.effectiveNetCarbs() },
                    proteinG = dayEntries.sumOf { it.effectiveProtein() },
                    fatG = dayEntries.sumOf { it.effectiveFat() },
                    waterMl = dayEntries.sumOf { it.effectiveWater() },
                    sodiumMg = sodiumMg,
                    potassiumMg = potassiumMg,
                    magnesiumMg = dayEntries.sumOf { it.effectiveMagnesium() },
                    nakRatio = if (potassiumMg > 0) sodiumMg / potassiumMg else null
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** Live unit preferences — consumed by trend screens for weight unit display. */
    val unitPreferences: StateFlow<UnitPreferences> = unitsPreferencesStore
        .preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = unitsPreferencesStore.getPreferences()
        )

    /** Whether the Weight metric toggle is currently enabled. */
    val weightMetricEnabled: StateFlow<Boolean> =
        healthMetricPreferencesStore.settingsFlow
            .map { settings -> settings[HealthMetric.WEIGHT] != false }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = healthMetricPreferencesStore.isMetricEnabled(HealthMetric.WEIGHT)
            )
}

class KetoViewModelFactory(
    private val ketoRepository: KetoRepository,
    private val targetsStore: KetoTargetsStore,
    private val weightRepository: WeightRepository,
    private val exerciseRepository: ExerciseRepository,
    private val exerciseCategoryRepository: ExerciseCategoryRepository,
    private val unitsPreferencesStore: UnitsPreferencesStore,
    private val healthMetricPreferencesStore: HealthMetricPreferencesStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        KetoViewModel(
            ketoRepository, targetsStore, weightRepository,
            exerciseRepository, exerciseCategoryRepository, unitsPreferencesStore,
            healthMetricPreferencesStore,
        ) as T
}
