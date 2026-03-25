package com.projectember.mobile.widget

import com.projectember.mobile.data.local.WeightUnit
import com.projectember.mobile.ui.theme.ThemeOption

/**
 * Shared data model for the Today summary widget.
 * This represents all the data needed to render the Today card in both
 * the main app and the home screen widget.
 */
data class TodayWidgetData(
    // Calories
    val caloriesCurrent: Double = 0.0,
    val caloriesTarget: Double = 0.0,
    val caloriesBurned: Double = 0.0,

    // Pacing
    val pacingStatus: String? = null,  // "ON_TRACK", "BEHIND", "AHEAD", "OVER_PACE"

    // Macros
    val proteinG: Double = 0.0,
    val proteinTarget: Double = 0.0,
    val netCarbsG: Double = 0.0,
    val netCarbsTarget: Double = 0.0,
    val fatG: Double = 0.0,
    val fatTarget: Double = 0.0,

    // Na:K Ratio
    val sodiumMg: Double = 0.0,
    val potassiumMg: Double = 0.0,
    val naKRatio: Double = 0.0,

    // Hydration
    val waterMl: Double = 0.0,
    val waterTarget: Double = 0.0,

    // Weight
    val weightKg: Double? = null,
    val weightDate: String? = null,
    val weightUnit: WeightUnit = WeightUnit.KG,

    // Theme
    val themeOption: ThemeOption = ThemeOption.EMBER_DARK,

    // Timestamp
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val displayCalories: Double
        get() = if (caloriesBurned > 0) (caloriesCurrent - caloriesBurned).coerceAtLeast(0.0) else caloriesCurrent

    val displayWeight: Double?
        get() = weightKg?.let { weightUnit.fromKg(it) }
}
