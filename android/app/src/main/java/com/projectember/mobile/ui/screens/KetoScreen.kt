package com.projectember.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectember.mobile.data.local.entities.KetoEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KetoScreen(
    viewModel: KetoViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAddEntry: () -> Unit
) {
    val recentEntries by viewModel.recentEntries.collectAsState()
    val todayEntries by viewModel.todayEntries.collectAsState()

    // Exercise entries subtract from the daily calorie total
    val todayCalories = todayEntries.sumOf { entry ->
        if (entry.eventType.equals("exercise", ignoreCase = true)) -entry.calories else entry.calories
    }
    val todayProtein = todayEntries.sumOf { it.proteinG }
    val todayFat = todayEntries.sumOf { it.fatG }
    val todayCarbs = todayEntries.sumOf { it.netCarbsG }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Keto Tracker") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddEntry,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Entry")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Today's summary card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Today — ${viewModel.today}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        MacroRow(label = "Calories", value = "%.0f kcal".format(todayCalories))
                        MacroRow(label = "Protein", value = "%.1f g".format(todayProtein))
                        MacroRow(label = "Fat", value = "%.1f g".format(todayFat))
                        MacroRow(label = "Net Carbs", value = "%.1f g".format(todayCarbs))
                    }
                }
            }

            item {
                Text(
                    text = "Recent Entries",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (recentEntries.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No entries yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Tap + to log your first keto entry.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(recentEntries) { entry ->
                    KetoEntryCard(entry = entry)
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun MacroRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KetoEntryCard(entry: KetoEntry) {
    val isExercise = entry.eventType.equals("exercise", ignoreCase = true)
    var expanded by remember { mutableStateOf(false) }

    val cardColor = if (isExercise)
        MaterialTheme.colorScheme.tertiaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    val onCardColor = if (isExercise)
        MaterialTheme.colorScheme.onTertiaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row: label + event type badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = onCardColor,
                    modifier = Modifier.weight(1f)
                )
                EventTypeBadge(type = entry.eventType)
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Calories + time row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Unicode minus (U+2212) for display; arithmetic negation is done in todayCalories
                val caloriesText = if (isExercise)
                    "−%.0f kcal".format(entry.calories)
                else
                    "%.0f kcal".format(entry.calories)
                Text(
                    text = caloriesText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = onCardColor
                )
                // Show only HH:mm — timestamp is always "yyyy-MM-dd HH:mm" (positions 11–15)
                val timeText = if (entry.eventTimestamp.length >= 16)
                    entry.eventTimestamp.substring(11, 16)
                else
                    entry.eventTimestamp
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = onCardColor
                )
            }

            // Expanded detail: macros, notes, full timestamp
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = onCardColor.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MacroDetail(
                        label = "Protein",
                        value = "%.1f g".format(entry.proteinG),
                        color = onCardColor
                    )
                    MacroDetail(
                        label = "Fat",
                        value = "%.1f g".format(entry.fatG),
                        color = onCardColor
                    )
                    MacroDetail(
                        label = "Net Carbs",
                        value = "%.1f g".format(entry.netCarbsG),
                        color = onCardColor
                    )
                }

                if (!entry.notes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = entry.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = onCardColor
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = entry.eventTimestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = onCardColor.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun EventTypeBadge(type: String) {
    val (bg, fg) = when (type.lowercase()) {
        "exercise" -> Pair(
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary
        )
        "meal" -> Pair(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary
        )
        "drink" -> Pair(
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.onSecondary
        )
        "snack" -> Pair(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        else -> Pair(
            MaterialTheme.colorScheme.outline,
            MaterialTheme.colorScheme.surface
        )
    }
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = bg
    ) {
        Text(
            text = type.uppercase(),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = fg
        )
    }
}

@Composable
private fun MacroDetail(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.7f)
        )
    }
}
