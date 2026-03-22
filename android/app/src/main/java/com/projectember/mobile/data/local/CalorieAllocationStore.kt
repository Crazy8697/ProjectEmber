package com.projectember.mobile.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "calorie_allocation_prefs"
private const val KEY_BF = "bf_pct"
private const val KEY_LN = "ln_pct"
private const val KEY_DN = "dn_pct"
private const val KEY_SN = "sn_pct"

class CalorieAllocationStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _allocation = MutableStateFlow(load())
    val allocation: StateFlow<CalorieAllocation> = _allocation.asStateFlow()

    private fun load(): CalorieAllocation = CalorieAllocation(
        breakfastPct = prefs.getInt(KEY_BF, 25),
        lunchPct = prefs.getInt(KEY_LN, 25),
        dinnerPct = prefs.getInt(KEY_DN, 50),
        snackPct = prefs.getInt(KEY_SN, 0)
    )

    fun save(allocation: CalorieAllocation) {
        prefs.edit()
            .putInt(KEY_BF, allocation.breakfastPct)
            .putInt(KEY_LN, allocation.lunchPct)
            .putInt(KEY_DN, allocation.dinnerPct)
            .putInt(KEY_SN, allocation.snackPct)
            .apply()
        _allocation.value = allocation
    }
}

