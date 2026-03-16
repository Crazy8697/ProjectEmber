package com.projectember.mobile.data.local

/**
 * Eating style preset that controls how the pacing engine shifts the expected-by-now
 * meal window relative to the user's wake and sleep times.
 */
enum class EatingStyle(val displayName: String) {
    EARLY_EATER("Early Eater"),
    NORMAL_EATER("Normal Eater"),
    LATE_EATER("Late Eater");
}

/**
 * User-configurable daily rhythm used to drive smart nutrition pacing.
 *
 * Defaults represent a typical adult schedule (7 AM wake, 11 PM sleep, normal eating).
 */
data class DailyRhythm(
    val wakeHour: Int = 7,
    val wakeMinute: Int = 0,
    val sleepHour: Int = 23,
    val sleepMinute: Int = 0,
    val eatingStyle: EatingStyle = EatingStyle.NORMAL_EATER
) {
    /** Wake time as total minutes since midnight. */
    val wakeMinuteOfDay: Int get() = wakeHour * 60 + wakeMinute

    /** Sleep time as total minutes since midnight. */
    val sleepMinuteOfDay: Int get() = sleepHour * 60 + sleepMinute
}
