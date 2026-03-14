package com.projectember.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectember.mobile.data.local.entities.WeightEntry
import com.projectember.mobile.data.local.entities.KetoEntry
import com.projectember.mobile.data.local.entities.effectiveCalories
import com.projectember.mobile.ui.theme.KetoBorder
import com.projectember.mobile.ui.theme.KetoAccent
import com.projectember.mobile.ui.theme.KetoAccentLight
import com.projectember.mobile.ui.theme.OnSurface
import com.projectember.mobile.ui.theme.OnSurfaceVariant
import com.projectember.mobile.ui.theme.SurfaceMid
import com.projectember.mobile.ui.theme.SuccessGreen
import com.projectember.mobile.ui.theme.WarningYellow
import com.projectember.mobile.ui.theme.ErrorRed
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ── Palette constants (desktop-matched) ─────────────────────────────────────
private val KetoCard    = SurfaceMid
private val KetoBorderC = KetoBorder
private val KetoMuted   = OnSurfaceVariant

/** Limit targets (calories, net carbs, fat, sodium): green < 80 %, yellow 80–100 %, red > 100 % */
private fun limitStatusColor(value: Double, target: Double): Color {
    if (target <= 0) return Color.Unspecified
    val pct = value / target
    return when {
        pct > 1.0  -> ErrorRed
        pct >= 0.8 -> WarningYellow
        else       -> SuccessGreen
    }
}

/** Goal targets (protein, water, potassium, magnesium): green >= 80 %, yellow 50–80 %, red < 50 % */
private fun goalStatusColor(value: Double, target: Double): Color {
    if (target <= 0) return Color.Unspecified
    val pct = value / target
    return when {
        pct >= 0.8 -> SuccessGreen
        pct >= 0.5 -> WarningYellow
        else       -> ErrorRed
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KetoScreen(
    viewModel: KetoViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAddEntry: () -> Unit,
    onNavigateToEditEntry: (Int) -> Unit,
    onNavigateToEditExercise: (Int) -> Unit,
    onNavigateToTargets: () -> Unit,
    onNavigateToTrends: (String) -> Unit,
    onNavigateToLogExercise: (String) -> Unit
) {
    val selectedDateEntries by viewModel.selectedDateEntries.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val targets by viewModel.targets.collectAsState()
    val lastWeightEntry by viewModel.lastWeightEntry.collectAsState()

    var showHelp by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showWeightDialog by remember { mutableStateOf(false) }
    var weightInput by remember { mutableStateOf("") }

    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    val todayCalories  = selectedDateEntries.sumOf { it.effectiveCalories() }
    val todayProtein   = selectedDateEntries.sumOf { it.proteinG }
    val todayFat       = selectedDateEntries.sumOf { it.fatG }
    val todayCarbs     = selectedDateEntries.sumOf { it.netCarbsG }
    val todayWater     = selectedDateEntries.sumOf { it.waterMl }
    val todaySodium    = selectedDateEntries.sumOf { it.sodiumMg }
    val todayPotassium = selectedDateEntries.sumOf { it.potassiumMg }
    val todayMagnesium = selectedDateEntries.sumOf { it.magnesiumMg }

    val hydrationPct = if (targets.waterMl > 0)
        (todayWater / targets.waterMl * 100).toInt() else 0

    val nakRatio: String = if (todayPotassium > 0)
        "%.1f : 1".format(todaySodium / todayPotassium)
    else "--"

    if (showHelp) {
        KetoHelpDialog(onDismiss = { showHelp = false })
    }

    if (showWeightDialog) {
        var weightError by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = {
                showWeightDialog = false
                weightInput = ""
                weightError = false
            },
            title = { Text("Log Weight") },
            text = {
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = {
                        weightInput = it
                        weightError = false
                    },
                    label = { Text("Weight (kg)") },
                    isError = weightError,
                    supportingText = if (weightError) {
                        { Text("Enter a valid weight greater than 0") }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val kg = weightInput.toDoubleOrNull()
                    if (kg != null && kg > 0) {
                        viewModel.logWeight(kg)
                        showWeightDialog = false
                        weightInput = ""
                        weightError = false
                    } else {
                        weightError = true
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showWeightDialog = false
                    weightInput = ""
                    weightError = false
                }) { Text("Cancel") }
            }
        )
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
                    IconButton(onClick = { onNavigateToTrends("") }) {
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
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // ── Date selector ────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showDatePicker = true },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = selectedDate,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = KetoAccent
                        )
                    }
                    Text(
                        text = "tap to change",
                        style = MaterialTheme.typography.labelSmall,
                        color = KetoMuted
                    )
                }
            }

            // ── Metric blocks row 1: Calories | Protein ──────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricBlock(
                        modifier = Modifier.weight(1f),
                        label = "CALORIES",
                        value = "%.0f".format(todayCalories),
                        unit = " kcal",
                        targetLabel = "target %.0f".format(targets.caloriesKcal),
                        diff = todayCalories - targets.caloriesKcal,
                        statusColor = limitStatusColor(todayCalories, targets.caloriesKcal),
                        onClick = { onNavigateToTrends("calories") }
                    )
                    MetricBlock(
                        modifier = Modifier.weight(1f),
                        label = "PROTEIN",
                        value = "%.1f".format(todayProtein),
                        unit = "g",
                        targetLabel = "target %.0f".format(targets.proteinG),
                        diff = todayProtein - targets.proteinG,
                        statusColor = goalStatusColor(todayProtein, targets.proteinG),
                        onClick = { onNavigateToTrends("protein") }
                    )
                }
            }

            // ── Metric blocks row 2: Fat | Net Carbs ─────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricBlock(
                        modifier = Modifier.weight(1f),
                        label = "FAT",
                        value = "%.1f".format(todayFat),
                        unit = "g",
                        targetLabel = "target %.0f".format(targets.fatG),
                        diff = todayFat - targets.fatG,
                        statusColor = limitStatusColor(todayFat, targets.fatG),
                        onClick = { onNavigateToTrends("fat") }
                    )
                    MetricBlock(
                        modifier = Modifier.weight(1f),
                        label = "NET CARBS",
                        value = "%.1f".format(todayCarbs),
                        unit = "g",
                        targetLabel = "target %.0f".format(targets.netCarbsG),
                        diff = todayCarbs - targets.netCarbsG,
                        statusColor = limitStatusColor(todayCarbs, targets.netCarbsG),
                        onClick = { onNavigateToTrends("net_carbs") }
                    )
                }
            }

            // ── Metric blocks row 3: Hydration % | Na:K Ratio ────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HydrationBlock(
                        modifier = Modifier.weight(1f),
                        todayWater = todayWater,
                        targetWater = targets.waterMl,
                        hydrationPct = hydrationPct,
                        onClick = { onNavigateToTrends("hydration") }
                    )
                    NakRatioBlock(
                        modifier = Modifier.weight(1f),
                        nakRatio = nakRatio,
                        todaySodium = todaySodium,
                        todayPotassium = todayPotassium,
                        onClick = { onNavigateToTrends("sodium") }
                    )
                }
            }

            // ── Metric blocks row 4: Magnesium | Weight ──────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricBlock(
                        modifier = Modifier.weight(1f),
                        label = "MAGNESIUM",
                        value = "%.0f".format(todayMagnesium),
                        unit = "mg",
                        targetLabel = "target %.0f".format(targets.magnesiumMg),
                        diff = todayMagnesium - targets.magnesiumMg,
                        statusColor = goalStatusColor(todayMagnesium, targets.magnesiumMg),
                        onClick = { onNavigateToTrends("magnesium") }
                    )
                    WeightBlock(
                        modifier = Modifier.weight(1f),
                        lastEntry = lastWeightEntry,
                        onClick = {
                            weightInput = lastWeightEntry?.weightKg?.let { "%.1f".format(it) } ?: ""
                            showWeightDialog = true
                        }
                    )
                }
            }

            // ── Quick-action: Log Exercise ────────────────────────────────────
            item {
                OutlinedButton(
                    onClick = { onNavigateToLogExercise(selectedDate) },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, KetoBorderC)
                ) {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Log Exercise")
                }
            }

            // ── Entries header ────────────────────────────────────────────────
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
                    KetoEntryCard(
                        entry = entry,
                        onEditEntry = { id ->
                            // Negative ids mark exercise entries mapped from exercise_entries table.
                            // Route them to the Exercise edit screen; positive ids go to Keto edit.
                        if (id < 0) onNavigateToEditExercise(kotlin.math.abs(id))
                            else onNavigateToEditEntry(id)
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

// ── Metric block ─────────────────────────────────────────────────────────────
@Composable
private fun MetricBlock(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    unit: String,
    targetLabel: String,
    diff: Double,
    statusColor: Color,
    onClick: () -> Unit
) {
    val valueColor = if (statusColor != Color.Unspecified) statusColor else OnSurface
    val diffColor  = when {
        diff == 0.0  -> KetoMuted
        diff < 0     -> ErrorRed
        else         -> SuccessGreen
    }
    val diffText = if (diff >= 0) "+%.1f".format(diff) else "%.1f".format(diff)

    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = KetoCard),
        border = BorderStroke(1.dp, KetoBorderC)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = KetoMuted,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value + unit,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = targetLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = KetoMuted,
                    fontSize = 10.sp
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelSmall,
                    color = KetoMuted,
                    fontSize = 10.sp
                )
                Text(
                    text = diffText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = diffColor,
                    fontSize = 10.sp
                )
            }
        }
    }
}

// ── Hydration block ──────────────────────────────────────────────────────────
@Composable
private fun HydrationBlock(
    modifier: Modifier = Modifier,
    todayWater: Double,
    targetWater: Double,
    hydrationPct: Int,
    onClick: () -> Unit
) {
    val statusColor = goalStatusColor(todayWater, targetWater)
    val barColor = if (statusColor != Color.Unspecified) statusColor else KetoAccent
    val progress = if (targetWater > 0)
        (todayWater / targetWater).coerceIn(0.0, 1.0).toFloat() else 0f

    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = KetoCard),
        border = BorderStroke(1.dp, KetoBorderC)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "HYDRATION %",
                style = MaterialTheme.typography.labelSmall,
                color = KetoMuted,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$hydrationPct%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (statusColor != Color.Unspecified) statusColor else OnSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "%.0f / %.0f mL".format(todayWater, targetWater),
                style = MaterialTheme.typography.labelSmall,
                color = KetoMuted,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = barColor,
                trackColor = KetoBorderC
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0%", style = MaterialTheme.typography.labelSmall, color = KetoMuted, fontSize = 9.sp)
                Text("100%", style = MaterialTheme.typography.labelSmall, color = KetoMuted, fontSize = 9.sp)
            }
        }
    }
}

// ── Na:K ratio block ─────────────────────────────────────────────────────────
@Composable
private fun NakRatioBlock(
    modifier: Modifier = Modifier,
    nakRatio: String,
    todaySodium: Double,
    todayPotassium: Double,
    onClick: () -> Unit
) {
    // If potassium is 0 but sodium > 0 → treat as max ratio (fully Na-heavy)
    // If both are 0 → neutral (no data)
    val ratio: Double
    val position: Float
    val ratioColor: Color
    val dotColor: Color
    when {
        todayPotassium > 0 -> {
            ratio = todaySodium / todayPotassium
            // position: 0.0 = K-heavy, 0.5 = ideal (1:1), 1.0 = Na-heavy (scale up to 2:1)
            position = (ratio / 2.0).coerceIn(0.0, 1.0).toFloat()
            ratioColor = when {
                ratio <= 1.0 -> SuccessGreen
                ratio <= 2.0 -> WarningYellow
                else         -> ErrorRed
            }
            dotColor = ratioColor
        }
        todaySodium > 0 -> {
            // Sodium present but no potassium – worst case Na-heavy
            ratio = Double.MAX_VALUE
            position = 1.0f
            ratioColor = ErrorRed
            dotColor = ErrorRed
        }
        else -> {
            // No data
            ratio = 0.0
            position = 0.0f
            ratioColor = OnSurface
            dotColor = KetoMuted
        }
    }

    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = KetoCard),
        border = BorderStroke(1.dp, KetoBorderC)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "NA:K RATIO",
                style = MaterialTheme.typography.labelSmall,
                color = KetoMuted,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = nakRatio,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = ratioColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when {
                        todayPotassium <= 0 && todaySodium > 0 -> "no potassium logged"
                        todayPotassium <= 0                    -> "need data"
                        else                                   -> "Na \u00f7 K"
                    },
                style = MaterialTheme.typography.labelSmall,
                color = KetoMuted,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Balance indicator bar: left = K-heavy, center = balanced, right = Na-heavy
            Box(modifier = Modifier.fillMaxWidth()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                ) {
                    // Track
                    drawRoundRect(
                        color = KetoBorderC,
                        size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                    )
                    // Indicator dot
                    val x = position * size.width
                    val r = size.height
                    drawCircle(color = dotColor, radius = r, center = Offset(x, size.height / 2))
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("K", style = MaterialTheme.typography.labelSmall, color = SuccessGreen, fontSize = 9.sp)
                Text("Na", style = MaterialTheme.typography.labelSmall, color = ErrorRed, fontSize = 9.sp)
            }
        }
    }
}

// ── Help dialog ──────────────────────────────────────────────────────────────
@Composable
private fun KetoHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Keto Field Guide") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    color = KetoCard,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, KetoBorderC)
                ) {
                    Text(
                        text = "\u26a0\ufe0f Not medical advice. This tracker is for informational purposes only. Consult a healthcare professional before making dietary changes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = KetoMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
                HelpSection(
                    title = "Net Carbs",
                    body = "Total carbohydrates minus dietary fiber. On keto, aim to stay below your daily target (commonly 20\u201350 g) to maintain ketosis."
                )
                HelpSection(
                    title = "Hydration",
                    body = "Keto can increase water loss. Aim for at least 2\u20133 L/day. The progress bar shows how close you are to your target."
                )
                HelpSection(
                    title = "Electrolytes",
                    body = "Reduced carbs lower insulin, causing kidneys to excrete more sodium, potassium, and magnesium. Replenish these to avoid cramps and fatigue.\n\u2022 Sodium: 2,000\u20133,000 mg/day\n\u2022 Potassium: 3,000\u20134,500 mg/day\n\u2022 Magnesium: 300\u2013500 mg/day"
                )
                HelpSection(
                    title = "Na:K Ratio",
                    body = "Sodium to potassium ratio shown as Na \u00f7 K. A value above 1.0 means more sodium than potassium, which is associated with elevated blood pressure. Aim for a ratio below 1.0 (more potassium than sodium) when possible."
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

// ── Entry card ────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KetoEntryCard(entry: KetoEntry, onEditEntry: (Int) -> Unit) {
    val isExercise   = entry.eventType.equals("exercise",   ignoreCase = true)
    val isSupplement = entry.eventType.equals("supplement", ignoreCase = true)
    var expanded by remember { mutableStateOf(false) }

    // Exercise: dark card with a subtle green accent border; all others: standard card
    val cardBorder = if (isExercise)
        BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f))
    else
        BorderStroke(1.dp, KetoBorderC)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = KetoCard),
        border = cardBorder,
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
                    color = OnSurface,
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
                when {
                    isSupplement -> Text(
                        text = "Tap for details",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = KetoMuted
                    )
                    isExercise -> Text(
                        text = "\u2212%.0f kcal".format(entry.calories),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = SuccessGreen
                    )
                    else -> Text(
                        text = "%.0f kcal".format(entry.calories),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = OnSurface
                    )
                }
                val timeText = if (entry.eventTimestamp.length >= 16)
                    entry.eventTimestamp.substring(11, 16)
                else
                    entry.eventTimestamp
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = KetoMuted
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = KetoBorderC)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MacroDetail(label = "Protein",    value = "%.1f g".format(entry.proteinG),  color = OnSurface)
                    MacroDetail(label = "Fat",        value = "%.1f g".format(entry.fatG),      color = OnSurface)
                    MacroDetail(label = "Net Carbs",  value = "%.1f g".format(entry.netCarbsG), color = OnSurface)
                }

                if (entry.waterMl > 0 || entry.sodiumMg > 0 || entry.potassiumMg > 0 || entry.magnesiumMg > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MacroDetail(label = "Water", value = "%.0f mL".format(entry.waterMl),    color = OnSurface)
                        MacroDetail(label = "Na",    value = "%.0f mg".format(entry.sodiumMg),   color = OnSurface)
                        MacroDetail(label = "K",     value = "%.0f mg".format(entry.potassiumMg),color = OnSurface)
                        MacroDetail(label = "Mg",    value = "%.0f mg".format(entry.magnesiumMg),color = OnSurface)
                    }
                }

                if (!entry.notes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = entry.notes, style = MaterialTheme.typography.bodySmall, color = OnSurface)
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = entry.eventTimestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = KetoMuted
                )

                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onEditEntry(entry.id) }) {
                        Text("Edit", color = KetoAccent)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventTypeBadge(type: String) {
    val (bg, fg) = when (type.lowercase()) {
        "exercise"   -> Pair(MaterialTheme.colorScheme.tertiary,           MaterialTheme.colorScheme.onTertiary)
        "meal"       -> Pair(MaterialTheme.colorScheme.primary,            MaterialTheme.colorScheme.onPrimary)
        "drink"      -> Pair(MaterialTheme.colorScheme.secondary,          MaterialTheme.colorScheme.onSecondary)
        "snack"      -> Pair(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        "supplement" -> Pair(KetoAccentLight,                              OnSurface)
        else         -> Pair(MaterialTheme.colorScheme.surfaceVariant,     MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun MacroDetail(label: String, value: String, color: Color = OnSurface) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = color)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
    }
}

// ── Weight block ──────────────────────────────────────────────────────────────
@Composable
private fun WeightBlock(
    modifier: Modifier = Modifier,
    lastEntry: WeightEntry?,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = KetoCard),
        border = BorderStroke(1.dp, KetoBorderC)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "WEIGHT",
                style = MaterialTheme.typography.labelSmall,
                color = KetoMuted,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (lastEntry != null) "%.1f".format(lastEntry.weightKg) else "--",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = OnSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (lastEntry != null) lastEntry.entryDate else "tap to log",
                style = MaterialTheme.typography.labelSmall,
                color = KetoMuted,
                fontSize = 10.sp
            )
        }
    }
}
