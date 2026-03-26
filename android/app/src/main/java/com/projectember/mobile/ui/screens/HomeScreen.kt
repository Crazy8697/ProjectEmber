package com.projectember.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectember.mobile.data.local.WeightUnit
import com.projectember.mobile.ui.theme.KetoAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToKeto: () -> Unit,
    onNavigateToRecipes: () -> Unit,
    onNavigateToExercise: () -> Unit,
    onNavigateToHealth: () -> Unit,
    onNavigateToSupplements: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToTrends: () -> Unit
) {
    val summary by viewModel.todaySummary.collectAsState()
    val targets by viewModel.targets.collectAsState()
    val lastWeight by viewModel.lastWeightEntry.collectAsState()
    val unitPrefs by viewModel.unitPreferences.collectAsState()
    val pacing by viewModel.todayPacing.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "🔥 Ember",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            TodaySummaryCard(
                summary = summary,
                caloriesTarget = targets.caloriesKcal,
                proteinTarget = targets.proteinG,
                netCarbsTarget = targets.netCarbsG,
                fatTarget = targets.fatG,
                waterTarget = targets.waterMl,
                lastWeightKg = lastWeight?.weightKg,
                lastWeightDate = lastWeight?.entryDate,
                weightUnit = unitPrefs.weightUnit,
                pacing = pacing,
                onNavigateToTrends = onNavigateToTrends
            )

            NavCard(
                title = "Keto",
                subtitle = "Daily entries, macros, graphs",
                icon = Icons.Default.MonitorWeight,
                onClick = onNavigateToKeto
            )

            NavCard(
                title = "Recipes",
                subtitle = "Keto recipe library",
                icon = Icons.Default.MenuBook,
                onClick = onNavigateToRecipes
            )

            NavCard(
                title = "Exercise",
                subtitle = "Activity tracking",
                icon = Icons.Default.DirectionsRun,
                onClick = onNavigateToExercise
            )

            NavCard(
                title = "Health",
                subtitle = "Vitals & sleep from Health Connect",
                icon = Icons.Default.Favorite,
                onClick = onNavigateToHealth
            )

            NavCard(
                title = "Supplements",
                subtitle = "Daily supplement log",
                icon = Icons.Default.Science,
                onClick = onNavigateToSupplements
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TodaySummaryCard(
    summary: TodaySummary,
    caloriesTarget: Double,
    proteinTarget: Double,
    netCarbsTarget: Double,
    fatTarget: Double,
    waterTarget: Double,
    lastWeightKg: Double?,
    lastWeightDate: String?,
    weightUnit: WeightUnit,
    pacing: TodayPacing,
    onNavigateToTrends: () -> Unit
) {
    val burned = summary.exerciseBurnedKcal
    val displayCalories = if (burned > 0) (summary.calories - burned).coerceAtLeast(0.0) else summary.calories
    val calRatio = if (caloriesTarget > 0) (displayCalories / caloriesTarget).toFloat() else 0f
    val calPct = calRatio.coerceIn(0f, 1f)

    // Pacing-aware colors: neutral/muted before window opens, status color once eating starts.
    // windowOpen = true once at least one pacing result is available (eating window started).
    val windowOpen = pacing.calories != null || pacing.protein != null
    val calColor = pacingStatusColor(pacing.calories)
    val calTextColor = (if (calColor != Color.Unspecified) calColor
                        else MaterialTheme.colorScheme.onSurface).accessible()
    // Progress bar must not receive Color.Unspecified — fall back to primary for neutral.
    val calBarColor = (if (calColor != Color.Unspecified) calColor
                       else MaterialTheme.colorScheme.primary).accessible()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Today",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    // Pacing chip — visible only when eating window is open
                    pacing.calories?.let { PacingChip(result = it) }
                }
                TextButton(
                    onClick = onNavigateToTrends
                ) {
                    Icon(
                        imageVector = Icons.Default.ShowChart,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = KetoAccent
                    )
                    Text(
                        text = "Trends",
                        style = MaterialTheme.typography.labelMedium,
                        color = KetoAccent,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            // Calories progress
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (burned > 0) "Calories (net)" else "Calories",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (caloriesTarget > 0)
                            "%.0f / %.0f kcal".format(displayCalories, caloriesTarget)
                        else
                            "%.0f kcal".format(displayCalories),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = calTextColor
                    )
                    Text(
                        text = "food %.0f \u2212 %.0f burned".format(summary.calories, burned),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (caloriesTarget > 0) {
                    LinearProgressIndicator(
                        progress = { calPct },
                        modifier = Modifier.fillMaxWidth(),
                        color = calBarColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
                pacing.calorieDayState?.let { dayState ->
                    Text(
                        text = "Pacing: ${pacingStatusDisplayLabel(dayState.status)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = (pacingStatusAccentColor(dayState.status)
                            .takeIf { it != Color.Unspecified }
                            ?: MaterialTheme.colorScheme.onSurfaceVariant).accessible()
                    )
                    Text(
                        text = "By now: expected %.0f kcal, actual %.0f kcal"
                            .format(dayState.expectedCaloriesByNow, dayState.actualCaloriesByNow),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val nextMeal = dayState.nextAnchorMeal
                    val practical = dayState.nextMealPracticalRemainingCalories
                    val planned = dayState.nextMealPlannedCalories
                    if (nextMeal != null && practical != null && planned != null) {
                        Text(
                            text = "%s practical remaining: %.0f kcal (planned %.0f)"
                                .format(nextMeal.displayName, practical, planned),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Macro row: P · NC · F · Na:K on one evenly-spaced line
            val naKRatio = if (summary.potassiumMg > 0) summary.sodiumMg / summary.potassiumMg else 0.0
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MacroChip(
                    label = "P",
                    value = summary.proteinG,
                    unit = "g",
                    statusColor = pacingStatusColor(pacing.protein)
                )
                MacroChip(
                    label = "NC",
                    value = summary.netCarbsG,
                    unit = "g",
                    statusColor = pacingStatusColor(pacing.netCarbs)
                        .takeIf { it != Color.Unspecified }
                        ?: if (windowOpen) strictLimitStatusColor(summary.netCarbsG, netCarbsTarget)
                           else Color.Unspecified
                )
                MacroChip(
                    label = "F",
                    value = summary.fatG,
                    unit = "g",
                    statusColor = if (windowOpen) targetRangeStatusColor(summary.fatG, fatTarget)
                                  else Color.Unspecified
                )
                MacroChip(
                    label = "Na:K",
                    value = naKRatio,
                    unit = "",
                    statusColor = Color.Unspecified,
                    decimalPlaces = 2
                )
            }

            // Hydration row — shown only when a water target is set
            if (waterTarget > 0) {
                val waterBarColor = KetoAccent.accessible()
                val waterTextColor = KetoAccent.accessible()
                val waterPct = (summary.waterMl / waterTarget).toFloat().coerceIn(0f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Water",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "%.0f / %.0f mL".format(summary.waterMl, waterTarget),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = waterTextColor
                        )
                    }
                    LinearProgressIndicator(
                        progress = { waterPct },
                        modifier = Modifier.fillMaxWidth(),
                        color = waterBarColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            // Weight row
            if (lastWeightKg != null) {
                val displayWeight = weightUnit.fromKg(lastWeightKg)
                val symbol = weightUnit.symbol
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Weight",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (lastWeightDate != null)
                            "%.1f $symbol  ·  %s".format(displayWeight, lastWeightDate)
                        else
                            "%.1f $symbol".format(displayWeight),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = KetoAccent
                    )
                }
            }
        }
    }
}

@Composable
private fun MacroChip(
    label: String,
    value: Double,
    unit: String,
    statusColor: Color,
    modifier: Modifier = Modifier,
    decimalPlaces: Int = 0
) {
    val color = (if (statusColor != Color.Unspecified) statusColor
                 else MaterialTheme.colorScheme.onSurface).accessible()

    val formattedValue = if (decimalPlaces > 0) {
        "%.${decimalPlaces}f%s".format(value, unit)
    } else {
        "%.0f%s".format(value, unit)
    }

    Box(modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formattedValue,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

/**
 * Small pill-shaped chip that displays the smart pacing status label.
 * Color mirrors pacing severity: neutral = on track, yellow = behind/ahead, red = over/off track.
 */
@Composable
private fun PacingChip(result: PacingResult) {
    val accent = pacingStatusAccentColor(result.status)
    val chipColor = (accent.takeIf { it != Color.Unspecified }
        ?: MaterialTheme.colorScheme.onSurfaceVariant).accessible()
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (accent != Color.Unspecified) chipColor.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Text(
            text = pacingStatusDisplayLabel(result.status),
            style = MaterialTheme.typography.labelSmall,
            color = chipColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun NavCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
