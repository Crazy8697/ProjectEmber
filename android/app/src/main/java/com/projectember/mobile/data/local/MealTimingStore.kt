package com.projectember.mobile.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "meal_timing_prefs"

// Each window stores 4 ints: start hour, start minute, end hour, end minute.
// A value of -1 for start hour indicates the window is not configured.
private const val KEY_BF_START_H = "bf_start_h"
private const val KEY_BF_START_M = "bf_start_m"
private const val KEY_BF_END_H = "bf_end_h"
private const val KEY_BF_END_M = "bf_end_m"
private const val KEY_LN_START_H = "ln_start_h"
private const val KEY_LN_START_M = "ln_start_m"
private const val KEY_LN_END_H = "ln_end_h"
private const val KEY_LN_END_M = "ln_end_m"
private const val KEY_DN_START_H = "dn_start_h"
private const val KEY_DN_START_M = "dn_start_m"
private const val KEY_DN_END_H = "dn_end_h"
private const val KEY_DN_END_M = "dn_end_m"
private const val KEY_SN_START_H = "sn_start_h"
private const val KEY_SN_START_M = "sn_start_m"
private const val KEY_SN_END_H = "sn_end_h"
private const val KEY_SN_END_M = "sn_end_m"

/**
 * Persists and exposes the user's optional [MealTiming] configuration via SharedPreferences.
 * A window is considered unset when its start-hour key is stored as -1.
 */
class MealTimingStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _mealTiming = MutableStateFlow(load())
    val mealTimingFlow: StateFlow<MealTiming> = _mealTiming.asStateFlow()

    fun getMealTiming(): MealTiming = _mealTiming.value

    fun setBreakfastWindow(window: MealWindow?) {
        saveWindow(window, KEY_BF_START_H, KEY_BF_START_M, KEY_BF_END_H, KEY_BF_END_M)
        _mealTiming.value = _mealTiming.value.copy(breakfastWindow = window)
    }

    fun setLunchWindow(window: MealWindow?) {
        saveWindow(window, KEY_LN_START_H, KEY_LN_START_M, KEY_LN_END_H, KEY_LN_END_M)
        _mealTiming.value = _mealTiming.value.copy(lunchWindow = window)
    }

    fun setDinnerWindow(window: MealWindow?) {
        saveWindow(window, KEY_DN_START_H, KEY_DN_START_M, KEY_DN_END_H, KEY_DN_END_M)
        _mealTiming.value = _mealTiming.value.copy(dinnerWindow = window)
    }

    fun setSnackWindow(window: MealWindow?) {
        saveWindow(window, KEY_SN_START_H, KEY_SN_START_M, KEY_SN_END_H, KEY_SN_END_M)
        _mealTiming.value = _mealTiming.value.copy(snackWindow = window)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun saveWindow(
        window: MealWindow?,
        startHKey: String,
        startMKey: String,
        endHKey: String,
        endMKey: String
    ) {
        prefs.edit()
            .putInt(startHKey, window?.startHour ?: -1)
            .putInt(startMKey, window?.startMinute ?: 0)
            .putInt(endHKey, window?.endHour ?: -1)
            .putInt(endMKey, window?.endMinute ?: 0)
            .apply()
    }

    private fun loadWindow(
        startHKey: String,
        startMKey: String,
        endHKey: String,
        endMKey: String
    ): MealWindow? {
        val startH = prefs.getInt(startHKey, -1)
        if (startH == -1) return null
        return MealWindow(
            startHour = startH,
            startMinute = prefs.getInt(startMKey, 0),
            endHour = prefs.getInt(endHKey, startH + 1),
            endMinute = prefs.getInt(endMKey, 0)
        )
    }

    private fun load() = MealTiming(
        breakfastWindow = loadWindow(KEY_BF_START_H, KEY_BF_START_M, KEY_BF_END_H, KEY_BF_END_M),
        lunchWindow = loadWindow(KEY_LN_START_H, KEY_LN_START_M, KEY_LN_END_H, KEY_LN_END_M),
        dinnerWindow = loadWindow(KEY_DN_START_H, KEY_DN_START_M, KEY_DN_END_H, KEY_DN_END_M),
        snackWindow = loadWindow(KEY_SN_START_H, KEY_SN_START_M, KEY_SN_END_H, KEY_SN_END_M)
    )
}
