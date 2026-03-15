package com.projectember.mobile.ui.screens

import androidx.compose.ui.graphics.Color
import com.projectember.mobile.ui.theme.ErrorRed
import com.projectember.mobile.ui.theme.SuccessGreen
import com.projectember.mobile.ui.theme.WarningYellow

/**
 * Target-range color for two-sided target metrics (calories, fat on keto).
 * A value within ±15 % of the target is green; ±15–30 % is yellow; clearly outside is red.
 * This avoids the misleading "green at 0 kcal" produced by a pure upper-limit rule.
 */
internal fun targetRangeStatusColor(value: Double, target: Double): Color {
    if (target <= 0) return Color.Unspecified
    val pct = value / target
    return when {
        pct >= 0.85 && pct <= 1.15 -> SuccessGreen
        pct >= 0.70 && pct <= 1.30 -> WarningYellow
        else                        -> ErrorRed
    }
}

/**
 * Goal-direction color for "higher is better" metrics (protein, water, magnesium, potassium).
 * ≥ 85 % of target → green; 60–85 % → yellow; < 60 % → red.
 */
internal fun goalStatusColor(value: Double, target: Double): Color {
    if (target <= 0) return Color.Unspecified
    val pct = value / target
    return when {
        pct >= 0.85 -> SuccessGreen
        pct >= 0.60 -> WarningYellow
        else        -> ErrorRed
    }
}

/**
 * Strict upper-limit color for net carbs and sodium (core keto constraints).
 * < 80 % of the limit → green; 80–100 % → yellow; > 100 % → red.
 */
internal fun strictLimitStatusColor(value: Double, target: Double): Color {
    if (target <= 0) return Color.Unspecified
    val pct = value / target
    return when {
        pct > 1.0  -> ErrorRed
        pct >= 0.8 -> WarningYellow
        else       -> SuccessGreen
    }
}
