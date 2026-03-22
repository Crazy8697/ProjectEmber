package com.projectember.mobile.data.local

data class CalorieAllocation(
    val breakfastPct: Int = 25,
    val lunchPct: Int = 25,
    val dinnerPct: Int = 50,
    val snackPct: Int = 0,
) {
    fun totalPct(): Int = breakfastPct + lunchPct + dinnerPct + snackPct

    fun perMealCalories(totalCalories: Double): Map<String, Double> {
        if (totalCalories <= 0.0) return mapOf(
            "breakfast" to 0.0,
            "lunch" to 0.0,
            "dinner" to 0.0,
            "snack" to 0.0
        )
        return mapOf(
            "breakfast" to (totalCalories * breakfastPct / 100.0),
            "lunch" to (totalCalories * lunchPct / 100.0),
            "dinner" to (totalCalories * dinnerPct / 100.0),
            "snack" to (totalCalories * snackPct / 100.0)
        )
    }
}

