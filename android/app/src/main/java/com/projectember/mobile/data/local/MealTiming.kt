package com.projectember.mobile.data.local

/**
 * A single meal window defined by a start and end time (hours + minutes).
 */
data class MealWindow(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
) {
    /** Start time as total minutes since midnight. */
    val startMinuteOfDay: Int get() = startHour * 60 + startMinute

    /** End time as total minutes since midnight. */
    val endMinuteOfDay: Int get() = endHour * 60 + endMinute
}

/**
 * Optional meal-timing configuration that refines the pacing model when set.
 * Any window left null falls back to the [DailyRhythm]-based pacing behavior.
 */
data class MealTiming(
    val breakfastWindow: MealWindow? = null,
    val lunchWindow: MealWindow? = null,
    val dinnerWindow: MealWindow? = null,
    val snackWindow: MealWindow? = null
) {
    /**
     * Returns true if at least one meal window has been configured.
     * When false, the pacing engine falls back entirely to [DailyRhythm].
     */
    fun isConfigured(): Boolean =
        breakfastWindow != null || lunchWindow != null || dinnerWindow != null || snackWindow != null
}
