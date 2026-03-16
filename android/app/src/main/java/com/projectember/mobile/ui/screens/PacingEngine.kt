package com.projectember.mobile.ui.screens

import com.projectember.mobile.data.local.DailyRhythm
import com.projectember.mobile.data.local.EatingStyle
import com.projectember.mobile.data.local.MealTiming
import java.time.LocalTime

/**
 * Pacing status for a tracked metric relative to the expected-by-now fraction.
 */
enum class PacingStatus(val label: String) {
    BEHIND("behind"),
    ON_TRACK("on track"),
    AHEAD("ahead")
}

/**
 * Result from a single pacing evaluation.
 *
 * @param status      The label to display (behind / on track / ahead).
 * @param actualFraction  Actual consumed fraction of the daily target (0–1+).
 * @param expectedFraction  Fraction expected to have been consumed by now (0–1).
 */
data class PacingResult(
    val status: PacingStatus,
    val actualFraction: Float,
    val expectedFraction: Float
)

/**
 * Centralised, reusable pacing engine for Smart Daily Pacing (PR36).
 *
 * Strategy:
 * 1. Derive the eating window [firstMealMinute, lastMealMinute] from either
 *    configured [MealTiming] windows (advanced) or the [DailyRhythm] eating-style
 *    preset (default).
 * 2. Compute the expected fraction of daily targets that should be consumed by
 *    [currentMinuteOfDay] using a linear ramp over that eating window.
 * 3. Compare the actual fraction against the expected fraction to produce a
 *    [PacingStatus] label.
 *
 * Meal Timing **refines** Daily Rhythm rather than replacing it — if meal windows
 * are configured their outermost boundaries are used; if not, the preset offsets
 * relative to wake/sleep times are used.
 */
object PacingEngine {

    // Threshold: how far from expected before the label changes from ON_TRACK.
    private const val THRESHOLD = 0.15f

    /**
     * Returns a [PacingResult] for a metric, or null when no target is set or
     * the eating window has not started yet (avoids false "behind" messages early
     * in the morning).
     *
     * @param actual            Actual consumed amount today.
     * @param target            Full-day target amount. Returns null if ≤ 0.
     * @param rhythm            The user's daily rhythm settings.
     * @param mealTiming        Optional meal-timing configuration.
     * @param currentMinuteOfDay  Minutes since midnight for "now". Defaults to the
     *                          current system time, injectable for testing.
     */
    fun evaluate(
        actual: Double,
        target: Double,
        rhythm: DailyRhythm,
        mealTiming: MealTiming,
        currentMinuteOfDay: Int = minuteOfDay()
    ): PacingResult? {
        if (target <= 0) return null

        val (firstMeal, lastMeal) = eatWindow(rhythm, mealTiming)

        // Before the eating window opens: expected = 0; suppress label entirely so
        // users don't see false "behind" messages early in the morning.
        if (currentMinuteOfDay < firstMeal) return null

        val expectedFraction: Float = when {
            currentMinuteOfDay >= lastMeal -> 1f
            else -> (currentMinuteOfDay - firstMeal).toFloat() /
                    (lastMeal - firstMeal).toFloat()
        }

        val actualFraction = (actual / target).toFloat().coerceAtLeast(0f)
        val status = classify(actualFraction, expectedFraction)
        return PacingResult(status, actualFraction, expectedFraction)
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Returns (firstMealMinuteOfDay, lastMealMinuteOfDay) for the eating window.
     *
     * When [mealTiming] is configured, the outer boundaries of the configured windows
     * are used. When it is not configured, [EatingStyle] offsets from wake/sleep times
     * are applied.
     */
    internal fun eatWindow(rhythm: DailyRhythm, mealTiming: MealTiming): Pair<Int, Int> {
        if (mealTiming.isConfigured()) {
            val windows = listOfNotNull(
                mealTiming.breakfastWindow,
                mealTiming.lunchWindow,
                mealTiming.dinnerWindow,
                mealTiming.snackWindow
            )
            val first = windows.minOf { it.startMinuteOfDay }
            val last  = windows.maxOf { it.endMinuteOfDay }
            // Sanity: first < last
            if (first < last) return first to last
        }

        // Fall back to Daily Rhythm preset
        val wake  = rhythm.wakeMinuteOfDay
        val sleep = rhythm.sleepMinuteOfDay

        return when (rhythm.eatingStyle) {
            // Early eater: starts eating right at wake, stops 4 h before sleep
            EatingStyle.EARLY_EATER  -> wake to (sleep - 240).coerceAtLeast(wake + 60)
            // Normal eater: starts 1 h after wake, stops 3 h before sleep
            EatingStyle.NORMAL_EATER -> (wake + 60) to (sleep - 180).coerceAtLeast(wake + 120)
            // Late eater: starts 2 h after wake, stops 1.5 h before sleep
            EatingStyle.LATE_EATER   -> (wake + 120) to (sleep - 90).coerceAtLeast(wake + 180)
        }
    }

    private fun classify(actual: Float, expected: Float): PacingStatus = when {
        actual > expected + THRESHOLD -> PacingStatus.AHEAD
        actual < expected - THRESHOLD -> PacingStatus.BEHIND
        else                          -> PacingStatus.ON_TRACK
    }

    private fun minuteOfDay(): Int {
        val now = LocalTime.now()
        return now.hour * 60 + now.minute
    }
}
