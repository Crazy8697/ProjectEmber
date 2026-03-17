package com.projectember.mobile.ui.screens

import androidx.compose.ui.graphics.Color
import com.projectember.mobile.data.local.HealthMetric
import com.projectember.mobile.ui.theme.KetoAccent
import com.projectember.mobile.ui.theme.SuccessGreen
import com.projectember.mobile.ui.theme.WarningYellow
import com.projectember.mobile.ui.theme.ErrorRed

/**
 * Centralized semantic graph color mapping for all metric families.
 *
 * Colors are assigned intentionally by metric category so that the same
 * type of data always appears in the same color family across every
 * screen that renders a trend/graph.
 *
 * Palette direction:
 * - Heart-related (heart rate, resting HR, blood pressure): red family
 * - Respiratory / oxygen metrics: blue family
 * - Sleep metrics: indigo / dark-blue family
 * - Weight / body metrics: purple family
 * - Glucose: amber / orange family
 * - Temperature: warm orange/red family
 * - Water / hydration: cyan-blue family
 * - Electrolytes / minerals: consistent differentiated hues
 * - Keto nutrition: coherent but distinguishable palette
 */

// ── Internal palette constants ─────────────────────────────────────────────────

// Heart / cardiovascular
private val HeartRed    = Color(0xFFE53935) // vivid red
private val HeartRedMid = Color(0xFFEF5350) // medium red

// Respiratory / oxygen
private val OxyBlue     = Color(0xFF1E88E5) // bright blue
private val RespBlue    = Color(0xFF42A5F5) // lighter blue

// Sleep
private val SleepIndigo = Color(0xFF5C6BC0) // indigo
private val SleepDeep   = Color(0xFF3949AB) // deeper indigo

// Body / weight
private val BodyPurple  = Color(0xFF9C69E2) // medium purple
private val BodyPurpleMid = Color(0xFFAB47BC)

// Glucose
private val GlucoseAmber = Color(0xFFFFA726) // amber/orange
private val GlucoseOrange = Color(0xFFFF8F00)

// Temperature
private val TempOrange  = Color(0xFFFF7043) // warm orange-red

// Hydration / water
private val HydroBlue   = Color(0xFF29B6F6) // sky blue

// Electrolytes / minerals
private val SodiumYellow = WarningYellow    // yellow — sodium/Na
private val PotassiumGreen = SuccessGreen   // green — potassium/K
private val MagnesiumBlue = Color(0xFF4A8FE8) // light blue — magnesium

// Keto nutrition
private val CaloriesBlue  = KetoAccent      // blue — calories (primary keto metric)
private val ProteinGreen  = SuccessGreen    // green — protein
private val FatYellow     = WarningYellow   // yellow — fat
private val CarbsRed      = ErrorRed        // red — net carbs (strict limit)
private val NakRatioOrange = Color(0xFFFF8C42) // orange — Na:K ratio

// ── Public API ─────────────────────────────────────────────────────────────────

/**
 * Returns the semantic graph color for a Keto metric string key.
 * Keys match those used in [KetoTrendsScreen] and [KetoViewModel].
 */
fun ketoMetricGraphColor(metric: String): Color = when (metric) {
    "calories"  -> CaloriesBlue
    "protein"   -> ProteinGreen
    "fat"       -> FatYellow
    "net_carbs" -> CarbsRed
    "hydration" -> HydroBlue
    "sodium"    -> SodiumYellow
    "potassium" -> PotassiumGreen
    "magnesium" -> MagnesiumBlue
    "nak_ratio" -> NakRatioOrange
    "weight"    -> BodyPurple
    else        -> Color(0xFF78909C) // neutral blue-grey for unknown/future metrics
}

/**
 * Returns the semantic graph color for a [HealthMetric].
 * Used in [HealthMetricTrendsScreen] and any other screen rendering a health metric chart.
 */
fun healthMetricGraphColor(metric: HealthMetric): Color = when (metric) {
    // Heart / cardiovascular — red family
    HealthMetric.HEART_RATE         -> HeartRed
    HealthMetric.RESTING_HEART_RATE -> HeartRedMid
    HealthMetric.BLOOD_PRESSURE     -> Color(0xFFD32F2F) // darker red for BP

    // Respiratory / oxygen — blue family
    HealthMetric.OXYGEN_SATURATION  -> OxyBlue
    HealthMetric.RESPIRATORY_RATE   -> RespBlue

    // Sleep — indigo / dark-blue family
    HealthMetric.SLEEP              -> SleepIndigo

    // Body / weight — purple family
    HealthMetric.WEIGHT             -> BodyPurple

    // Glucose — amber / orange
    HealthMetric.BLOOD_GLUCOSE      -> GlucoseAmber

    // Temperature — warm orange/red
    HealthMetric.BODY_TEMPERATURE   -> TempOrange

    // Activity / exercise metrics — green/teal family
    HealthMetric.STEPS              -> Color(0xFF26A69A) // teal
    HealthMetric.DISTANCE           -> Color(0xFF00897B) // darker teal
    HealthMetric.ACTIVE_CALORIES    -> Color(0xFF43A047) // green
    HealthMetric.EXERCISE_SESSIONS  -> Color(0xFF2E7D32) // dark green
}
