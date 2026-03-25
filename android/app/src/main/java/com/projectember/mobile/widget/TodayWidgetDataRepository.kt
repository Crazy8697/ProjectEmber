package com.projectember.mobile.widget

import android.content.Context
import com.projectember.mobile.data.local.DailyRhythmStore
import com.projectember.mobile.data.local.KetoTargetsStore
import com.projectember.mobile.data.local.MealTimingStore
import com.projectember.mobile.data.local.UnitsPreferencesStore
import com.projectember.mobile.data.local.entities.effectiveCalories
import com.projectember.mobile.data.local.entities.effectiveFat
import com.projectember.mobile.data.local.entities.effectiveNetCarbs
import com.projectember.mobile.data.local.entities.effectivePotassium
import com.projectember.mobile.data.local.entities.effectiveProtein
import com.projectember.mobile.data.local.entities.effectiveSodium
import com.projectember.mobile.data.local.entities.effectiveWater
import com.projectember.mobile.data.repository.ExerciseRepository
import com.projectember.mobile.data.repository.KetoRepository
import com.projectember.mobile.data.repository.WeightRepository
import com.projectember.mobile.ui.screens.PacingEngine
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Repository for fetching Today summary data for widgets.
 * Centralizes the logic for gathering Today card data so both the app
 * and home screen widget use the same source of truth.
 */
class TodayWidgetDataRepository(
    private val context: Context,
    private val ketoRepository: KetoRepository,
    private val exerciseRepository: ExerciseRepository,
    private val weightRepository: WeightRepository,
    private val targetsStore: KetoTargetsStore,
    private val unitsPreferencesStore: UnitsPreferencesStore,
    private val dailyRhythmStore: DailyRhythmStore,
    private val mealTimingStore: MealTimingStore
) {
    /**
     * Fetch the current Today widget data snapshot.
     * This is a suspend function that should be called from a coroutine.
     */
    suspend fun getTodayData(): TodayWidgetData {
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        // Fetch all required data
        val entries = ketoRepository.getEntriesForDate(today).first()
        val exerciseEntries = exerciseRepository.getEntriesForDate(today).first()
        val targets = targetsStore.targets.first()
        val unitPrefs = unitsPreferencesStore.preferencesFlow.first()
        val lastWeight = weightRepository.getLatestEntry().first()
        val dailyRhythm = dailyRhythmStore.rhythmFlow.first()
        val mealTiming = mealTimingStore.mealTimingFlow.first()

        // Calculate totals (only food entries, exclude exercise)
        val food = entries.filter { it.eventType != "exercise" }
        val calories = food.sumOf { it.effectiveCalories() }.coerceAtLeast(0.0)
        val proteinG = food.sumOf { it.effectiveProtein() }
        val fatG = food.sumOf { it.effectiveFat() }
        val netCarbsG = food.sumOf { it.effectiveNetCarbs() }
        val waterMl = food.sumOf { it.effectiveWater() }
        val sodiumMg = food.sumOf { it.effectiveSodium() }
        val potassiumMg = food.sumOf { it.effectivePotassium() }
        val burned = exerciseEntries.sumOf { it.caloriesBurned ?: 0.0 }.coerceAtLeast(0.0)

        // Calculate pacing status
        val caloriePacingResult = PacingEngine.evaluate(
            actual = calories,
            target = targets.caloriesKcal,
            rhythm = dailyRhythm,
            mealTiming = mealTiming
        )
        val pacingStatus = caloriePacingResult?.status?.name

        // Calculate Na:K ratio
        val naKRatio = if (potassiumMg > 0) sodiumMg / potassiumMg else 0.0

        return TodayWidgetData(
            caloriesCurrent = calories,
            caloriesTarget = targets.caloriesKcal,
            caloriesBurned = burned,
            pacingStatus = pacingStatus,
            proteinG = proteinG,
            proteinTarget = targets.proteinG,
            netCarbsG = netCarbsG,
            netCarbsTarget = targets.netCarbsG,
            fatG = fatG,
            fatTarget = targets.fatG,
            sodiumMg = sodiumMg,
            potassiumMg = potassiumMg,
            naKRatio = naKRatio,
            waterMl = waterMl,
            waterTarget = targets.waterMl,
            weightKg = lastWeight?.weightKg,
            weightDate = lastWeight?.entryDate,
            weightUnit = unitPrefs.weightUnit,
            lastUpdated = System.currentTimeMillis()
        )
    }
}
