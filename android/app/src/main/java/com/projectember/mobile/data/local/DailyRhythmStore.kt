package com.projectember.mobile.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "daily_rhythm_prefs"
private const val KEY_WAKE_HOUR = "wake_hour"
private const val KEY_WAKE_MINUTE = "wake_minute"
private const val KEY_SLEEP_HOUR = "sleep_hour"
private const val KEY_SLEEP_MINUTE = "sleep_minute"
private const val KEY_EATING_STYLE = "eating_style"

/**
 * Persists and exposes the user's [DailyRhythm] settings via SharedPreferences.
 */
class DailyRhythmStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _rhythm = MutableStateFlow(load())
    val rhythmFlow: StateFlow<DailyRhythm> = _rhythm.asStateFlow()

    fun getRhythm(): DailyRhythm = _rhythm.value

    fun setWakeTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_WAKE_HOUR, hour)
            .putInt(KEY_WAKE_MINUTE, minute)
            .apply()
        _rhythm.value = _rhythm.value.copy(wakeHour = hour, wakeMinute = minute)
    }

    fun setSleepTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_SLEEP_HOUR, hour)
            .putInt(KEY_SLEEP_MINUTE, minute)
            .apply()
        _rhythm.value = _rhythm.value.copy(sleepHour = hour, sleepMinute = minute)
    }

    fun setEatingStyle(style: EatingStyle) {
        prefs.edit().putString(KEY_EATING_STYLE, style.name).apply()
        _rhythm.value = _rhythm.value.copy(eatingStyle = style)
    }

    private fun load(): DailyRhythm {
        val style = runCatching {
            EatingStyle.valueOf(
                prefs.getString(KEY_EATING_STYLE, EatingStyle.NORMAL_EATER.name)!!
            )
        }.getOrDefault(EatingStyle.NORMAL_EATER)
        return DailyRhythm(
            wakeHour = prefs.getInt(KEY_WAKE_HOUR, 7),
            wakeMinute = prefs.getInt(KEY_WAKE_MINUTE, 0),
            sleepHour = prefs.getInt(KEY_SLEEP_HOUR, 23),
            sleepMinute = prefs.getInt(KEY_SLEEP_MINUTE, 0),
            eatingStyle = style
        )
    }
}
