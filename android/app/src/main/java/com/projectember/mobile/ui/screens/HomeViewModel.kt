package com.projectember.mobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.local.DailyRhythm
import com.projectember.mobile.data.local.DailyRhythmStore
import com.projectember.mobile.data.local.KetoTargets
import com.projectember.mobile.data.local.KetoTargetsStore
import com.projectember.mobile.data.local.MealTiming
import com.projectember.mobile.data.local.MealTimingStore
import com.projectember.mobile.data.local.UnitPreferences
import com.projectember.mobile.data.local.UnitsPreferencesStore
import com.projectember.mobile.data.local.entities.SyncStatus
import com.projectember.mobile.data.local.entities.WeightEntry
import com.projectember.mobile.data.local.entities.effectiveCalories
import com.projectember.mobile.data.local.entities.effectiveFat
import com.projectember.mobile.data.local.entities.effectiveNetCarbs
import com.projectember.mobile.data.local.entities.effectiveProtein
import com.projectember.mobile.data.local.entities.effectiveWater
import com.projectember.mobile.data.repository.ExerciseRepository
import com.projectember.mobile.data.repository.KetoRepository
import com.projectember.mobile.data.repository.SyncRepository
import com.projectember.mobile.data.repository.WeightRepository
import com.projectember.mobile.sync.SyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class TodaySummary(
    val calories: Double,
    val proteinG: Double,
    val fatG: Double,
    val netCarbsG: Double,
    val waterMl: Double = 0.0,
    /** Calories burned via exercise today; 0 when no exercise logged. */
    val exerciseBurnedKcal: Double = 0.0
)

/**
 * Bundles the three per-metric pacing results shown on the dashboard.
 * Any field may be null when no target is set or the eating window has not yet
 * opened (suppresses false "behind" labels early in the morning).
 */
data class TodayPacing(
    val calories: PacingResult?,
    val protein: PacingResult?,
    val netCarbs: PacingResult?
)

class HomeViewModel(
    private val syncRepository: SyncRepository,
    private val syncManager: SyncManager,
    ketoRepository: KetoRepository,
    targetsStore: KetoTargetsStore,
    weightRepository: WeightRepository,
    private val unitsPreferencesStore: UnitsPreferencesStore,
    exerciseRepository: ExerciseRepository,
    dailyRhythmStore: DailyRhythmStore,
    mealTimingStore: MealTimingStore
) : ViewModel() {

    private val today: String =
        LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    // ── Sync ─────────────────────────────────────────────────────────────────

    val syncStatus: StateFlow<SyncStatus?> = syncRepository
        .getSyncStatus()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    fun triggerSync() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            syncManager.triggerManualSync()
            _isSyncing.value = false
        }
    }

    // ── Dashboard metrics ─────────────────────────────────────────────────────

    val targets: StateFlow<KetoTargets> = targetsStore.targets

    /** Aggregate of all keto (food) entries for today — exercise excluded.
     *  When exercise is logged, [TodaySummary.exerciseBurnedKcal] is populated so the
     *  Home screen can display net calories (food − burned), matching the Keto screen's
     *  PR29 calorie model. */
    val todaySummary: StateFlow<TodaySummary> = combine(
        ketoRepository.getEntriesForDate(today),
        exerciseRepository.getEntriesForDate(today)
    ) { entries, exerciseEntries ->
        // Only food entries count toward macro totals.
        val food = entries.filter { it.eventType != "exercise" }
        val burned = exerciseEntries.sumOf { it.caloriesBurned ?: 0.0 }
        TodaySummary(
            calories          = food.sumOf { it.effectiveCalories() }.coerceAtLeast(0.0),
            proteinG          = food.sumOf { it.effectiveProtein() },
            fatG              = food.sumOf { it.effectiveFat() },
            netCarbsG         = food.sumOf { it.effectiveNetCarbs() },
            waterMl           = food.sumOf { it.effectiveWater() },
            exerciseBurnedKcal = burned.coerceAtLeast(0.0)
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TodaySummary(0.0, 0.0, 0.0, 0.0, 0.0)
        )

    // ── Smart pacing ──────────────────────────────────────────────────────────

    private val dailyRhythm: StateFlow<DailyRhythm> = dailyRhythmStore
        .rhythmFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = dailyRhythmStore.getRhythm()
        )

    private val mealTiming: StateFlow<MealTiming> = mealTimingStore
        .mealTimingFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = mealTimingStore.getMealTiming()
        )

    /**
     * Per-metric pacing results driven by the user's Daily Rhythm and optional
     * Meal Timing configuration.  Null entries mean pacing is not applicable for
     * that metric right now (no target, or eating window has not opened yet).
     */
    val todayPacing: StateFlow<TodayPacing> = combine(
        todaySummary,
        targets,
        dailyRhythm,
        mealTiming
    ) { summary, tgts, rhythm, timing ->
        val netCal = (summary.calories - summary.exerciseBurnedKcal).coerceAtLeast(0.0)
        TodayPacing(
            calories = PacingEngine.evaluate(netCal, tgts.caloriesKcal, rhythm, timing),
            protein  = PacingEngine.evaluate(summary.proteinG, tgts.proteinG, rhythm, timing),
            netCarbs = if (tgts.netCarbsG > 0)
                PacingEngine.evaluate(summary.netCarbsG, tgts.netCarbsG, rhythm, timing)
            else null
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TodayPacing(null, null, null)
        )

    // ── Weight & units ────────────────────────────────────────────────────────

    /** Latest logged body weight, or null if none recorded. */
    val lastWeightEntry: StateFlow<WeightEntry?> = weightRepository
        .getLatestEntry()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    /** Live unit preferences for display. */
    val unitPreferences: StateFlow<UnitPreferences> = unitsPreferencesStore
        .preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = unitsPreferencesStore.getPreferences()
        )
}

class HomeViewModelFactory(
    private val syncRepository: SyncRepository,
    private val syncManager: SyncManager,
    private val ketoRepository: KetoRepository,
    private val targetsStore: KetoTargetsStore,
    private val weightRepository: WeightRepository,
    private val unitsPreferencesStore: UnitsPreferencesStore,
    private val exerciseRepository: ExerciseRepository,
    private val dailyRhythmStore: DailyRhythmStore,
    private val mealTimingStore: MealTimingStore
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HomeViewModel(
            syncRepository, syncManager, ketoRepository, targetsStore,
            weightRepository, unitsPreferencesStore, exerciseRepository,
            dailyRhythmStore, mealTimingStore
        ) as T
}

