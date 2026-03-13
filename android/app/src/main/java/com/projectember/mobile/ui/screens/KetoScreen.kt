package com.projectember.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectember.mobile.data.local.entities.KetoEntry
import com.projectember.mobile.data.local.entities.effectiveCalories
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Status color helpers
private val StatusGreen = Color(0xFF00C853)
private val StatusYellow = Color(0xFFFFB300)
private val StatusRed = Color(0xFFD32F2F)
private val StatusNeutral = Color.Unspecified

/** For "limit" targets (calories, net carbs, fat, sodium): green < 80%, yellow 80-100%, red > 100% */
private fun limitStatusColor(value: Double, target: Double): Color {
    if (target <= 0) return StatusNeutral
    val pct = value / target
    return when {
        pct > 1.0 -> StatusRed
        pct >= 0.8 -> StatusYellow
        else -> StatusGreen
    }
}

/** For "goal" targets (protein, water, potassium, magnesium): green >= 80%, yellow 50-80%, red < 50% */
private fun goalStatusColor(value: Double, target: Double): Color {
    if (target <= 0) return StatusNeutral
    val pct = value / target
    return when {
        pct >= 0.8 -> StatusGreen
        pct >= 0.5 -> StatusYellow
        else -> StatusRed
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KetoScreen(
    viewModel: KetoViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAddEntry: () -> Unit,
    onNavigateToEditEntry: (Int) -> Unit,
    onNavigateToTargets: () -> Unit,
    onNavigateToTrends: () -> Unit
) {
    val selectedDateEntries by viewModel.selectedDateEntries.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val targets by viewModel.targets.collectAsState()

    var showHelp by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    val todayCalories = selectedDateEntries.sumOf { it.effectiveCalories() }
    val todayProtein = selectedDateEntries.sumOf { it.proteinG }
    val todayFat = selectedDateEntries.sumOf { it.fatG }
    val todayCarbs = selectedDateEntries.sumOf { it.netCarbsG }
    val todayWater = selectedDateEntries.sumOf { it.waterMl }
    val todaySodium = selectedDateEntries.sumOf { it.sodiumMg }
    val todayPotassium = selectedDateEntries.sumOf { it.potassiumMg }
    val todayMagnesium = selectedDateEntries.sumOf { it.magnesiumMg }

    val nakRatio: String = if (todayPotassium > 0) {
        "%.1f : 1".format(todaySodium / todayPotassium)
    } else {
        "— : —"
    }

    if (showHelp) {
        KetoHelpDialog(onDismiss = { showHelp = false })
    }

    if (showDatePicker) {
        val initialMillis = try {
            LocalDate.parse(selectedDate, dateFormatter).toEpochDay() * 86_400_000L
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = LocalDate.ofEpochDay(millis / 86_400_000L)
                        viewModel.setSelectedDate(date.format(dateFormatter))
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Keto Tracker") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToTrends) {
                        Icon(Icons.Default.ShowChart, contentDescription = "Trends")
                    }
                    IconButton(onClick = { showHelp = true }) {
                        Icon(Icons.Default.Help, contentDescription = "Help")
                    }
                    IconButton(onClick = onNavigateToTargets) {
                        Icon(Icons.Default.Settings, contentDescription = "Targets")
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

            // Summary card with tappable date selector
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        TextButton(
                            onClick = { showDatePicker = true },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = selectedDate,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        StatusMacroRow(
                            label = "Calories",
                            value = "%.0f / %.0f kcal".format(todayCalories, targets.caloriesKcal),
                            statusColor = limitStatusColor(todayCalories, targets.caloriesKcal)
                        )
                        StatusMacroRow(
                            label = "Protein",
                            value = "%.1f / %.0f g".format(todayProtein, targets.proteinG),
                            statusColor = goalStatusColor(todayProtein, targets.proteinG)
                        )
                        StatusMacroRow(
                            label = "Fat",
                            value = "%.1f / %.0f g".format(todayFat, targets.fatG),
                            statusColor = limitStatusColor(todayFat, targets.fatG)
                        )
                        StatusMacroRow(
                            label = "Net Carbs",
                            value = "%.1f / %.0f g".format(todayCarbs, targets.netCarbsG),
                            statusColor = limitStatusColor(todayCarbs, targets.netCarbsG)
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(8.dp))

                        // Hydration progress
                        HydrationRow(todayWater = todayWater, targetWater = targets.waterMl)

                        Spacer(modifier = Modifier.height(8.dp))
                        StatusMacroRow(
                            label = "Sodium",
                            value = "%.0f / %.0f mg".format(todaySodium, targets.sodiumMg),
                            statusColor = limitStatusColor(todaySodium, targets.sodiumMg)
                        )
                        StatusMacroRow(
                            label = "Potassium",
                            value = "%.0f / %.0f mg".format(todayPotassium, targets.potassiumMg),
                            statusColor = goalStatusColor(todayPotassium, targets.potassiumMg)
                        )
                        StatusMacroRow(
                            label = "Magnesium",
                            value = "%.0f / %.0f mg".format(todayMagnesium, targets.magnesiumMg),
                            statusColor = goalStatusColor(todayMagnesium, targets.magnesiumMg)
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(8.dp))

                        // Na:K ratio
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Na:K Ratio",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = nakRatio,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Entries",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (selectedDateEntries.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No entries for this date",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Tap + to log a keto entry.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(selectedDateEntries) { entry ->
                    KetoEntryCard(entry = entry, onEditEntry = onNavigateToEditEntry)
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun HydrationRow(todayWater: Double, targetWater: Double) {
    val progress = if (targetWater > 0) (todayWater / targetWater).coerceIn(0.0, 1.0).toFloat() else 0f
    val pct = (progress * 100).toInt()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Water",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "%.0f / %.0f mL  ($pct%%)".format(todayWater, targetWater),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = goalStatusColor(todayWater, targetWater).takeIf { it != Color.Unspecified }
                    ?: MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = goalStatusColor(todayWater, targetWater).takeIf { it != Color.Unspecified }
                ?: MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
        )
    }
}

@Composable
private fun StatusMacroRow(label: String, value: String, statusColor: Color) {
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
            color = if (statusColor != Color.Unspecified) statusColor
                    else MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun KetoHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Keto Field Guide") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HelpSection(
                    title = "Net Carbs",
                    body = "Total carbohydrates minus dietary fiber. On keto, aim to stay below your daily target (commonly 20–50 g) to maintain ketosis."
                )
                HelpSection(
                    title = "Hydration",
                    body = "Keto can increase water loss. Aim for at least 2–3 L/day. The progress bar shows how close you are to your target."
                )
                HelpSection(
                    title = "Electrolytes",
                    body = "Reduced carbs lower insulin, causing kidneys to excrete more sodium, potassium, and magnesium. Replenish these to avoid cramps and fatigue.\n• Sodium: 2,000–3,000 mg/day\n• Potassium: 3,000–4,500 mg/day\n• Magnesium: 300–500 mg/day"
                )
                HelpSection(
                    title = "Na:K Ratio",
                    body = "Sodium to potassium ratio shown as Na ÷ K. A value above 1.0 means more sodium than potassium, which is associated with elevated blood pressure. Aim for a ratio below 1.0 (more potassium than sodium) when possible."
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        }
    )
}

@Composable
private fun HelpSection(title: String, body: String) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(body, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KetoEntryCard(entry: KetoEntry, onEditEntry: (Int) -> Unit) {
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = onCardColor.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MacroDetail(label = "Protein", value = "%.1f g".format(entry.proteinG), color = onCardColor)
                    MacroDetail(label = "Fat", value = "%.1f g".format(entry.fatG), color = onCardColor)
                    MacroDetail(label = "Net Carbs", value = "%.1f g".format(entry.netCarbsG), color = onCardColor)
                }

                if (entry.waterMl > 0 || entry.sodiumMg > 0 || entry.potassiumMg > 0 || entry.magnesiumMg > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MacroDetail(label = "Water", value = "%.0f mL".format(entry.waterMl), color = onCardColor)
                        MacroDetail(label = "Na", value = "%.0f mg".format(entry.sodiumMg), color = onCardColor)
                        MacroDetail(label = "K", value = "%.0f mg".format(entry.potassiumMg), color = onCardColor)
                        MacroDetail(label = "Mg", value = "%.0f mg".format(entry.magnesiumMg), color = onCardColor)
                    }
                }

                if (!entry.notes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = entry.notes, style = MaterialTheme.typography.bodySmall, color = onCardColor)
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = entry.eventTimestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = onCardColor.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onEditEntry(entry.id) }) {
                        Text("Edit", color = onCardColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventTypeBadge(type: String) {
    val (bg, fg) = when (type.lowercase()) {
        "exercise" -> Pair(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
        "meal" -> Pair(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
        "drink" -> Pair(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary)
        "snack" -> Pair(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        else -> Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Surface(shape = MaterialTheme.shapes.extraSmall, color = bg) {
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
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = color)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
    }
}

