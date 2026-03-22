package com.projectember.mobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.local.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class KetoSettingsViewModel(
    private val targetsStore: KetoTargetsStore,
    private val calorieAllocationStore: CalorieAllocationStore,
    private val mealTimingStore: MealTimingStore,
    private val dailyRhythmStore: DailyRhythmStore,
    private val recipeCategoryStore: RecipeCategoryStore
) : ViewModel() {

    val targets: KetoTargets get() = targetsStore.targets.value

    private val _allocation = MutableStateFlow(calorieAllocationStore.allocation.value)
    val allocation: StateFlow<CalorieAllocation> = _allocation.asStateFlow()

    private val _mealTiming = MutableStateFlow(mealTimingStore.getMealTiming())
    val mealTiming: StateFlow<MealTiming> = _mealTiming.asStateFlow()

    private val _dailyRhythm = MutableStateFlow(dailyRhythmStore.getRhythm())
    val dailyRhythm: StateFlow<DailyRhythm> = _dailyRhythm.asStateFlow()

    val recipeCategories: StateFlow<List<String>> = recipeCategoryStore.categories

    fun updateAllocation(a: CalorieAllocation) {
        _allocation.value = a
    }

    fun saveAllocationIfValid(): Boolean {
        val a = _allocation.value
        if (a.totalPct() != 100) return false
        // Validate disabled meal timing: if a meal timing is unset, allocation must be 0
        val timing = mealTimingStore.getMealTiming()
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
    }

    fun setLunchWindow(window: MealWindow?) {
        mealTimingStore.setLunchWindow(window)
        _mealTiming.value = mealTimingStore.getMealTiming()
    }

    fun setDinnerWindow(window: MealWindow?) {
        mealTimingStore.setDinnerWindow(window)
        _mealTiming.value = mealTimingStore.getMealTiming()
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

    fun addRecipeCategory(name: String) {
        recipeCategoryStore.addCategory(name)
    }

    fun renameRecipeCategory(oldName: String, newName: String) {
        recipeCategoryStore.renameCategory(oldName, newName)
    }

    fun deleteRecipeCategory(name: String) {
        recipeCategoryStore.deleteCategory(name)
    }

    companion object {
        fun factory(
            targetsStore: KetoTargetsStore,
            calorieAllocationStore: CalorieAllocationStore,
            mealTimingStore: MealTimingStore,
            dailyRhythmStore: DailyRhythmStore,
            recipeCategoryStore: RecipeCategoryStore
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                KetoSettingsViewModel(
                    targetsStore,
                    calorieAllocationStore,
                    mealTimingStore,
                    dailyRhythmStore,
                    recipeCategoryStore
                ) as T
        }
    }
}

