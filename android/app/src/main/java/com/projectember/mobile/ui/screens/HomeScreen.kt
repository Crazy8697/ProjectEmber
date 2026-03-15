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
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MonitorWeight
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectember.mobile.ui.theme.ErrorRed
import com.projectember.mobile.ui.theme.KetoAccent
import com.projectember.mobile.ui.theme.SuccessGreen
import com.projectember.mobile.ui.theme.WarningYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToKeto: () -> Unit,
    onNavigateToRecipes: () -> Unit,
    onNavigateToExercise: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToTrends: () -> Unit
) {
    val summary by viewModel.todaySummary.collectAsState()
    val targets by viewModel.targets.collectAsState()
    val lastWeight by viewModel.lastWeightEntry.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "🔥 Project Ember",
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
                lastWeightKg = lastWeight?.weightKg,
                lastWeightDate = lastWeight?.entryDate,
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
    lastWeightKg: Double?,
    lastWeightDate: String?,
    onNavigateToTrends: () -> Unit
) {
    val calRatio = if (caloriesTarget > 0) (summary.calories / caloriesTarget).toFloat() else 0f
    val calPct = calRatio.coerceIn(0f, 1f)
    val calColor = when {
        calRatio > 1f   -> ErrorRed
        calRatio >= 0.8f -> WarningYellow
        else             -> SuccessGreen
    }

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
                Text(
                    text = "Today",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
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
                        text = "Calories",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (caloriesTarget > 0)
                            "%.0f / %.0f kcal".format(summary.calories, caloriesTarget)
                        else
                            "%.0f kcal".format(summary.calories),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = calColor
                    )
                }
                if (caloriesTarget > 0) {
                    LinearProgressIndicator(
                        progress = { calPct },
                        modifier = Modifier.fillMaxWidth(),
                        color = calColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            // Macro row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MacroChip(
                    label = "P",
                    value = summary.proteinG,
                    target = proteinTarget,
                    unit = "g",
                    modifier = Modifier.weight(1f)
                )
                MacroChip(
                    label = "NC",
                    value = summary.netCarbsG,
                    target = netCarbsTarget,
                    unit = "g",
                    modifier = Modifier.weight(1f)
                )
                MacroChip(
                    label = "F",
                    value = summary.fatG,
                    target = fatTarget,
                    unit = "g",
                    modifier = Modifier.weight(1f)
                )
            }

            // Weight row
            if (lastWeightKg != null) {
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
                            "%.1f kg  ·  %s".format(lastWeightKg, lastWeightDate)
                        else
                            "%.1f kg".format(lastWeightKg),
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
    target: Double,
    unit: String,
    modifier: Modifier = Modifier
) {
    val color = if (target > 0) {
        val pct = value / target
        when {
            pct >= 0.8 -> SuccessGreen
            pct >= 0.5 -> WarningYellow
            else       -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "%.0f%s".format(value, unit),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
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
