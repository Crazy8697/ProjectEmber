package com.projectember.mobile.ui.screens

import com.projectember.mobile.data.local.CalorieAllocation
import com.projectember.mobile.data.local.DailyRhythm
import com.projectember.mobile.data.local.MealTiming
import com.projectember.mobile.data.local.MealWindow
import com.projectember.mobile.data.local.entities.KetoEntry
import com.projectember.mobile.data.local.entities.effectiveCalories
import java.time.LocalTime

enum class AnchorMeal(val displayName: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner")
}

data class CalorieDayState(
    val targetCalories: Double,
    val expectedCaloriesByNow: Double,
    val actualCaloriesByNow: Double,
    val status: PacingStatus,
    val completedAnchorMeals: Set<AnchorMeal>,
    val plannedAnchorBudgets: Map<AnchorMeal, Double>,
    val adjustedAnchorBudgets: Map<AnchorMeal, Double>,
    val nextAnchorMeal: AnchorMeal?,
    val nextMealPlannedCalories: Double?,
    val nextMealPracticalRemainingCalories: Double?
)

/**
 * Calorie pacing model for PR58.
 *
 * - Anchor meals are breakfast/lunch/dinner only.
 * - Snack calories are counted immediately and never create a timed checkpoint.
 * - Snack calories logged after completed anchors reduce only future anchors,
 *   proportionally to each future meal's planned allocation.
 */
object CaloriePacingCalculator {

    private const val AHEAD_DELTA_FRACTION = 0.08
    private const val BEHIND_DELTA_FRACTION = 0.08
    private const val OVER_DELTA_FRACTION = 0.18

    private val anchorOrder = listOf(AnchorMeal.BREAKFAST, AnchorMeal.LUNCH, AnchorMeal.DINNER)

    private data class TimedEntry(
        val entry: KetoEntry,
        val minuteOfDay: Int,
        val calories: Double
    )

    fun evaluateDayState(
        entries: List<KetoEntry>,
        targetCalories: Double,
        allocation: CalorieAllocation,
        rhythm: DailyRhythm,
        mealTiming: MealTiming,
        currentMinuteOfDay: Int = minuteOfDay()
    ): CalorieDayState? {
        if (targetCalories <= 0.0) return null

        val windows = anchorWindows(rhythm, mealTiming)
        val normalized = normalizedAllocation(allocation)

        val plannedAnchorBudgets = mapOf(
            AnchorMeal.BREAKFAST to (targetCalories * normalized.breakfast),
            AnchorMeal.LUNCH to (targetCalories * normalized.lunch),
            AnchorMeal.DINNER to (targetCalories * normalized.dinner)
        )
        val plannedSnackCalories = targetCalories * normalized.snack

        val timedEntries = entries
            .mapNotNull { entry ->
                val minute = parseMinuteOfDay(entry.eventTimestamp) ?: return@mapNotNull null
                TimedEntry(entry = entry, minuteOfDay = minute, calories = entry.effectiveCalories())
            }
            .sortedBy { it.minuteOfDay }

        val foodByNow = timedEntries.filter {
            it.minuteOfDay <= currentMinuteOfDay &&
                !it.entry.eventType.equals("exercise", ignoreCase = true)
        }

        val anchorEntriesByMeal = anchorOrder.associateWith { meal ->
            foodByNow.filter { timed -> classifyAnchorMeal(timed.entry, timed.minuteOfDay, windows) == meal }
        }

        val consumedByMeal = anchorOrder.associateWith { meal ->
            anchorEntriesByMeal.getValue(meal).sumOf { it.calories.coerceAtLeast(0.0) }
        }

        val snackEventsByNow = foodByNow
            .filter { it.entry.eventType.equals("snack", ignoreCase = true) }
            .map { it.minuteOfDay to it.calories.coerceAtLeast(0.0) }

        val adjustedAnchorBudgets = redistributeFutureAnchorBudgets(
            plannedBudgets = plannedAnchorBudgets,
            snackEvents = snackEventsByNow,
            windows = windows,
            anchorEntriesByMeal = anchorEntriesByMeal
        )

        val completedNow = anchorOrder.filterTo(mutableSetOf()) { meal ->
            isAnchorCompletedAt(
                meal = meal,
                minuteOfDay = currentMinuteOfDay,
                windows = windows,
                anchorEntriesByMeal = anchorEntriesByMeal
            )
        }

        val expectedByNow = expectedCaloriesByNow(
            currentMinuteOfDay = currentMinuteOfDay,
            windows = windows,
            plannedAnchorBudgets = plannedAnchorBudgets,
            plannedSnackCalories = plannedSnackCalories,
            completedNow = completedNow,
            anchorEntriesByMeal = anchorEntriesByMeal
        )

        val actualByNow = foodByNow.sumOf { it.calories.coerceAtLeast(0.0) }
        val status = classify(actualByNow, expectedByNow, targetCalories)

        val nextAnchor = anchorOrder.firstOrNull { meal ->
            !isAnchorCompletedAt(
                meal = meal,
                minuteOfDay = currentMinuteOfDay,
                windows = windows,
                anchorEntriesByMeal = anchorEntriesByMeal
            )
        }

        val nextPlanned = nextAnchor?.let { plannedAnchorBudgets[it] ?: 0.0 }
        val nextPracticalRemaining = nextAnchor?.let { meal ->
            val adjusted = adjustedAnchorBudgets[meal] ?: 0.0
            val consumed = consumedByMeal[meal] ?: 0.0
            (adjusted - consumed).coerceAtLeast(0.0)
        }

        return CalorieDayState(
            targetCalories = targetCalories,
            expectedCaloriesByNow = expectedByNow,
            actualCaloriesByNow = actualByNow,
            status = status,
            completedAnchorMeals = completedNow,
            plannedAnchorBudgets = plannedAnchorBudgets,
            adjustedAnchorBudgets = adjustedAnchorBudgets,
            nextAnchorMeal = nextAnchor,
            nextMealPlannedCalories = nextPlanned,
            nextMealPracticalRemainingCalories = nextPracticalRemaining
        )
    }

    private data class NormalizedAllocation(
        val breakfast: Double,
        val lunch: Double,
        val dinner: Double,
        val snack: Double
    )

    private fun normalizedAllocation(allocation: CalorieAllocation): NormalizedAllocation {
        val rawBreakfast = allocation.breakfastPct.coerceAtLeast(0).toDouble()
        val rawLunch = allocation.lunchPct.coerceAtLeast(0).toDouble()
        val rawDinner = allocation.dinnerPct.coerceAtLeast(0).toDouble()
        val rawSnack = allocation.snackPct.coerceAtLeast(0).toDouble()
        val total = rawBreakfast + rawLunch + rawDinner + rawSnack
        if (total <= 0.0) return NormalizedAllocation(0.0, 0.0, 0.0, 0.0)
        return NormalizedAllocation(
            breakfast = rawBreakfast / total,
            lunch = rawLunch / total,
            dinner = rawDinner / total,
            snack = rawSnack / total
        )
    }

    private fun anchorWindows(rhythm: DailyRhythm, mealTiming: MealTiming): Map<AnchorMeal, MealWindow?> {
        val hasConfiguredAnchors = mealTiming.breakfastWindow != null ||
            mealTiming.lunchWindow != null ||
            mealTiming.dinnerWindow != null

        if (hasConfiguredAnchors) {
            return mapOf(
                AnchorMeal.BREAKFAST to mealTiming.breakfastWindow,
                AnchorMeal.LUNCH to mealTiming.lunchWindow,
                AnchorMeal.DINNER to mealTiming.dinnerWindow
            )
        }

        val (first, last) = PacingEngine.eatWindow(rhythm, MealTiming())
        val span = (last - first).coerceAtLeast(180)
        val slice = (span / 3).coerceAtLeast(45)

        return mapOf(
            AnchorMeal.BREAKFAST to MealWindow(
                startHour = first / 60,
                startMinute = first % 60,
                endHour = (first + slice) / 60,
                endMinute = (first + slice) % 60
            ),
            AnchorMeal.LUNCH to MealWindow(
                startHour = (first + slice) / 60,
                startMinute = (first + slice) % 60,
                endHour = (first + (slice * 2)) / 60,
                endMinute = (first + (slice * 2)) % 60
            ),
            AnchorMeal.DINNER to MealWindow(
                startHour = (first + (slice * 2)) / 60,
                startMinute = (first + (slice * 2)) % 60,
                endHour = last / 60,
                endMinute = last % 60
            )
        )
    }

    private fun classifyAnchorMeal(
        entry: KetoEntry,
        minuteOfDay: Int,
        windows: Map<AnchorMeal, MealWindow?>
    ): AnchorMeal? {
        val label = entry.label.lowercase()
        if ("breakfast" in label) return AnchorMeal.BREAKFAST
        if ("lunch" in label) return AnchorMeal.LUNCH
        if ("dinner" in label) return AnchorMeal.DINNER

        if (entry.eventType.equals("snack", ignoreCase = true) ||
            entry.eventType.equals("exercise", ignoreCase = true)
        ) return null

        val matching = anchorOrder.filter { meal ->
            val window = windows[meal] ?: return@filter false
            minuteOfDay >= window.startMinuteOfDay && minuteOfDay <= window.endMinuteOfDay
        }
        return matching.singleOrNull()
    }

    private fun isAnchorCompletedAt(
        meal: AnchorMeal,
        minuteOfDay: Int,
        windows: Map<AnchorMeal, MealWindow?>,
        anchorEntriesByMeal: Map<AnchorMeal, List<TimedEntry>>
    ): Boolean {
        val hasEntry = anchorEntriesByMeal[meal].orEmpty().any { it.minuteOfDay <= minuteOfDay }
        val windowPassed = windows[meal]?.let { minuteOfDay >= it.endMinuteOfDay } ?: false
        return hasEntry || windowPassed
    }

    private fun redistributeFutureAnchorBudgets(
        plannedBudgets: Map<AnchorMeal, Double>,
        snackEvents: List<Pair<Int, Double>>,
        windows: Map<AnchorMeal, MealWindow?>,
        anchorEntriesByMeal: Map<AnchorMeal, List<TimedEntry>>
    ): Map<AnchorMeal, Double> {
        val adjusted = plannedBudgets.toMutableMap()

        snackEvents.sortedBy { it.first }.forEach { (snackMinute, snackCalories) ->
            if (snackCalories <= 0.0) return@forEach

            val futureAnchors = anchorOrder.filter { meal ->
                !isAnchorCompletedAt(meal, snackMinute, windows, anchorEntriesByMeal)
            }
            if (futureAnchors.isEmpty()) return@forEach

            val plannedFuture = futureAnchors.sumOf { plannedBudgets[it] ?: 0.0 }
            if (plannedFuture <= 0.0) return@forEach

            futureAnchors.forEach { meal ->
                val share = (plannedBudgets[meal] ?: 0.0) / plannedFuture
                val reduction = snackCalories * share
                adjusted[meal] = ((adjusted[meal] ?: 0.0) - reduction).coerceAtLeast(0.0)
            }
        }

        return adjusted.toMap()
    }

    private fun expectedCaloriesByNow(
        currentMinuteOfDay: Int,
        windows: Map<AnchorMeal, MealWindow?>,
        plannedAnchorBudgets: Map<AnchorMeal, Double>,
        plannedSnackCalories: Double,
        completedNow: Set<AnchorMeal>,
        anchorEntriesByMeal: Map<AnchorMeal, List<TimedEntry>>
    ): Double {
        val expectedAnchors = anchorOrder.sumOf { meal ->
            val planned = plannedAnchorBudgets[meal] ?: 0.0
            if (planned <= 0.0) return@sumOf 0.0

            val window = windows[meal]
            if (window == null) {
                return@sumOf if (
                    meal in completedNow ||
                    anchorEntriesByMeal[meal].orEmpty().isNotEmpty()
                ) planned else 0.0
            }

            when {
                currentMinuteOfDay >= window.endMinuteOfDay -> planned
                currentMinuteOfDay <= window.startMinuteOfDay -> 0.0
                else -> {
                    val elapsed = (currentMinuteOfDay - window.startMinuteOfDay).toDouble()
                    val duration = (window.endMinuteOfDay - window.startMinuteOfDay)
                        .coerceAtLeast(1)
                        .toDouble()
                    planned * (elapsed / duration)
                }
            }
        }

        val configuredWindows = windows.values.filterNotNull()
        val expectedSnack = if (plannedSnackCalories <= 0.0 || configuredWindows.isEmpty()) {
            0.0
        } else {
            val dayStart = configuredWindows.minOf { it.startMinuteOfDay }
            val dayEnd = configuredWindows.maxOf { it.endMinuteOfDay }
            when {
                currentMinuteOfDay <= dayStart -> 0.0
                currentMinuteOfDay >= dayEnd -> plannedSnackCalories
                else -> {
                    val elapsed = (currentMinuteOfDay - dayStart).toDouble()
                    val duration = (dayEnd - dayStart).coerceAtLeast(1).toDouble()
                    plannedSnackCalories * (elapsed / duration)
                }
            }
        }

        return expectedAnchors + expectedSnack
    }

    private fun classify(actual: Double, expected: Double, target: Double): PacingStatus {
        val delta = actual - expected
        val aheadDelta = target * AHEAD_DELTA_FRACTION
        val behindDelta = target * BEHIND_DELTA_FRACTION
        val overDelta = target * OVER_DELTA_FRACTION
        return when {
            delta >= overDelta -> PacingStatus.OVER_PACE
            delta >= aheadDelta -> PacingStatus.AHEAD
            delta <= -behindDelta -> PacingStatus.BEHIND
            else -> PacingStatus.ON_TRACK
        }
    }

    private fun parseMinuteOfDay(timestamp: String): Int? {
        val match = TIME_REGEX.find(timestamp.trim()) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    private fun minuteOfDay(): Int {
        val now = LocalTime.now()
        return now.hour * 60 + now.minute
    }

    private val TIME_REGEX = Regex("(\\d{1,2}):(\\d{2})$")
}

internal fun CalorieDayState.toPacingResult(): PacingResult = PacingResult(
    status = status,
    actualFraction = if (targetCalories > 0) (actualCaloriesByNow / targetCalories).toFloat() else 0f,
    expectedFraction = if (targetCalories > 0) (expectedCaloriesByNow / targetCalories).toFloat() else 0f
)
