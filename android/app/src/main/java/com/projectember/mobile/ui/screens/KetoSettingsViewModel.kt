package com.projectember.mobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.projectember.mobile.data.local.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class KetoSettingsViewModel(
    private val targetsStore: KetoTargetsStore,
    private val calorieAllocationStore: CalorieAllocationStore,
    private val mealTimingStore: MealTimingStore,
    private val dailyRhythmStore: DailyRhythmStore,
) : ViewModel() {

    val targets: KetoTargets get() = targetsStore.targets.value

    private val _allocation = MutableStateFlow(calorieAllocationStore.allocation.value)
    val allocation: StateFlow<CalorieAllocation> = _allocation.asStateFlow()

    private val _mealTiming = MutableStateFlow(mealTimingStore.getMealTiming())
    val mealTiming: StateFlow<MealTiming> = _mealTiming.asStateFlow()

    private val _dailyRhythm = MutableStateFlow(dailyRhythmStore.getRhythm())
    val dailyRhythm: StateFlow<DailyRhythm> = _dailyRhythm.asStateFlow()

    private fun sanitizeAllocationForTiming(
        allocation: CalorieAllocation,
        timing: MealTiming = _mealTiming.value
    ): CalorieAllocation {
        return allocation.copy(
            breakfastPct = if (timing.breakfastWindow == null) 0 else allocation.breakfastPct,
            lunchPct = if (timing.lunchWindow == null) 0 else allocation.lunchPct,
            dinnerPct = if (timing.dinnerWindow == null) 0 else allocation.dinnerPct
        )
    }

    fun updateAllocation(a: CalorieAllocation) {
        _allocation.value = sanitizeAllocationForTiming(a)
    }

    fun saveAllocationIfValid(): Boolean {
        val timing = mealTimingStore.getMealTiming()
        val a = sanitizeAllocationForTiming(_allocation.value, timing)
        _allocation.value = a
        if (a.totalPct() != 100) return false
        if (timing.breakfastWindow == null && a.breakfastPct != 0) return false
        if (timing.lunchWindow == null && a.lunchPct != 0) return false
        if (timing.dinnerWindow == null && a.dinnerPct != 0) return false
        calorieAllocationStore.save(a)
        return true
    }

    fun saveTargets(newTargets: KetoTargets) {
        targetsStore.save(newTargets)
    }

    fun setBreakfastWindow(window: MealWindow?) {
        mealTimingStore.setBreakfastWindow(window)
        _mealTiming.value = mealTimingStore.getMealTiming()
        _allocation.value = sanitizeAllocationForTiming(_allocation.value, _mealTiming.value)
    }

    fun setLunchWindow(window: MealWindow?) {
        mealTimingStore.setLunchWindow(window)
        _mealTiming.value = mealTimingStore.getMealTiming()
        _allocation.value = sanitizeAllocationForTiming(_allocation.value, _mealTiming.value)
    }

    fun setDinnerWindow(window: MealWindow?) {
        mealTimingStore.setDinnerWindow(window)
        _mealTiming.value = mealTimingStore.getMealTiming()
        _allocation.value = sanitizeAllocationForTiming(_allocation.value, _mealTiming.value)
    }

    fun setWakeTime(h: Int, m: Int) {
        dailyRhythmStore.setWakeTime(h, m)
        _dailyRhythm.value = dailyRhythmStore.getRhythm()
    }

    fun setSleepTime(h: Int, m: Int) {
        dailyRhythmStore.setSleepTime(h, m)
        _dailyRhythm.value = dailyRhythmStore.getRhythm()
    }

    fun setEatingStyle(style: EatingStyle) {
        dailyRhythmStore.setEatingStyle(style)
        _dailyRhythm.value = dailyRhythmStore.getRhythm()
    }

    companion object {
        fun factory(
            targetsStore: KetoTargetsStore,
            calorieAllocationStore: CalorieAllocationStore,
            mealTimingStore: MealTimingStore,
            dailyRhythmStore: DailyRhythmStore,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                KetoSettingsViewModel(
                    targetsStore,
                    calorieAllocationStore,
                    mealTimingStore,
                    dailyRhythmStore,
                ) as T
        }
    }
}

