package com.projectember.mobile.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.projectember.mobile.ui.theme.ErrorRed
import com.projectember.mobile.ui.theme.StatusAmberLight
import com.projectember.mobile.ui.theme.StatusGreenLight
import com.projectember.mobile.ui.theme.StatusRedLight
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
 * Pacing-aware color derived from a [PacingResult].
 *
 * - null result (eating window not yet open) → [Color.Unspecified] so the caller can
 *   fall back to a neutral/muted style (e.g. [MaterialTheme.colorScheme.onSurface]).
 * - ON_TRACK  → neutral/default (no warning color)
 * - AHEAD     → yellow
 * - OVER/BEHIND → red
 *
 * This replaces full-day-deficit colors in the Home Today card so that the app never
 * shows a deep red number for calories at 7 AM just because the daily target hasn't
 * been reached yet.
 */
internal fun pacingStatusColor(result: PacingResult?): Color {
    if (result == null) return Color.Unspecified
    return pacingStatusAccentColor(result.status)
}

/** Accent color for pacing state badges/text. ON_TRACK intentionally stays neutral. */
internal fun pacingStatusAccentColor(status: PacingStatus): Color = when (status) {
    PacingStatus.ON_TRACK  -> Color.Unspecified
    PacingStatus.AHEAD     -> WarningYellow
    PacingStatus.OVER_PACE,
    PacingStatus.BEHIND    -> ErrorRed
}

/** Product-safe pacing labels used across Home and Keto tracker cards. */
internal fun pacingStatusDisplayLabel(status: PacingStatus): String = when (status) {
    PacingStatus.ON_TRACK  -> "On Track"
    PacingStatus.AHEAD     -> "Ahead of Pace"
    PacingStatus.OVER_PACE,
    PacingStatus.BEHIND    -> "Over Pace / Off Track"
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

/**
 * Adapts a raw status color to be readable against the current theme surface.
 * On light themes (where [MaterialTheme.colorScheme.surface] has high luminance) the
 * neon/pastel dark-optimised palette is swapped for darker accessible equivalents.
 * On dark themes the original color is returned unchanged.
 */
@Composable
internal fun Color.accessible(): Color {
    if (this == Color.Unspecified) return this
    val isLight = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    if (!isLight) return this
    return when (this) {
        SuccessGreen  -> StatusGreenLight
        WarningYellow -> StatusAmberLight
        ErrorRed      -> StatusRedLight
        else          -> this
    }
}
