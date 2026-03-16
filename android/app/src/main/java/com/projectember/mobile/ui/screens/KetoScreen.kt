package com.projectember.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import com.projectember.mobile.data.local.entities.effectiveFat
import com.projectember.mobile.data.local.entities.effectiveMagnesium
import com.projectember.mobile.data.local.entities.effectiveNetCarbs
import com.projectember.mobile.data.local.entities.effectivePotassium
import com.projectember.mobile.data.local.entities.effectiveProtein
import com.projectember.mobile.data.local.entities.effectiveSodium
import com.projectember.mobile.data.local.entities.effectiveWater
import com.projectember.mobile.ui.theme.KetoBorder
import com.projectember.mobile.ui.theme.KetoAccent
import com.projectember.mobile.ui.theme.KetoAccentLight
import com.projectember.mobile.ui.theme.SuccessGreen
import com.projectember.mobile.ui.theme.WarningYellow
import com.projectember.mobile.ui.theme.ErrorRed
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ── Palette constants (desktop-matched) ─────────────────────────────────────

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
    onNavigateToLogExercise: (String) -> Unit,
    onNavigateToWeightHistory: () -> Unit
) {
    val selectedDateEntries by viewModel.selectedDateEntries.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val targets by viewModel.targets.collectAsState()
    val lastWeightEntry by viewModel.lastWeightEntry.collectAsState()
    val unitPrefs by viewModel.unitPreferences.collectAsState()
    val weightUnit = unitPrefs.weightUnit
    val foodUnit   = unitPrefs.foodWeightUnit
    val volUnit    = unitPrefs.volumeUnit
    val weightMetricEnabled by viewModel.weightMetricEnabled.collectAsState()

    var showHelp by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showWeightDialog by remember { mutableStateOf(false) }
    var weightInput by remember { mutableStateOf("") }

    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // Food-only entries — exercise entries are displayed in the list but excluded from
    // macro totals so that the numbers match the Home dashboard (which is also food-only).
    val foodEntries = selectedDateEntries.filter {
        !it.eventType.equals("exercise", ignoreCase = true)
    }
    val todayCalories  = foodEntries.sumOf { it.effectiveCalories() }
    val todayProtein   = foodEntries.sumOf { it.effectiveProtein() }
    val todayFat       = foodEntries.sumOf { it.effectiveFat() }
    val todayCarbs     = foodEntries.sumOf { it.effectiveNetCarbs() }
    val todayWater     = foodEntries.sumOf { it.effectiveWater() }
    val todaySodium    = foodEntries.sumOf { it.effectiveSodium() }
    val todayPotassium = foodEntries.sumOf { it.effectivePotassium() }
    val todayMagnesium = foodEntries.sumOf { it.effectiveMagnesium() }

    // Exercise calories burned today — shown as a subtitle on the CALORIES block so the
    // user can still see exercise impact without the number conflicting with the Home total.
    val todayExerciseBurned = selectedDateEntries
        .filter { it.eventType.equals("exercise", ignoreCase = true) }
        .sumOf { it.calories * it.servings }

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
                    label = { Text("Weight (${weightUnit.symbol})") },
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
                    val value = weightInput.toDoubleOrNull()
                    if (value != null && value > 0) {
                        viewModel.logWeight(weightUnit.toKg(value))
                        showWeightDialog = false
                        weightInput = ""
                        weightError = false
                    } else {
                        weightError = true
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showWeightDialog = false
                        weightInput = ""
                        weightError = false
                        onNavigateToWeightHistory()
                    }) { Text("History") }
                    TextButton(onClick = {
                        showWeightDialog = false
                        weightInput = ""
                        weightError = false
                    }) { Text("Cancel") }
                }
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Metric blocks row 1: Calories | Protein ──────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // When exercise is burned, show net calories as the primary value so the
                    // number is always unambiguous. The food intake and burn are shown as a
                    // clearly-labeled subtitle. When no exercise, food == net, so no change.
                    val netCalories = todayCalories - todayExerciseBurned
                    val displayCalories = if (todayExerciseBurned > 0) netCalories else todayCalories
                    MetricBlock(
                        modifier = Modifier.weight(1f),
                        label = if (todayExerciseBurned > 0) "CALORIES (net)" else "CALORIES",
                        value = "%.0f".format(displayCalories),
                        unit = " kcal",
                        targetLabel = "target %.0f".format(targets.caloriesKcal),
                        diff = displayCalories - targets.caloriesKcal,
                        statusColor = targetRangeStatusColor(displayCalories, targets.caloriesKcal),
                        onClick = { onNavigateToTrends("calories") },
                        burnedLabel = if (todayExerciseBurned > 0)
                            "food %.0f \u2212 %.0f burned".format(todayCalories, todayExerciseBurned)
                        else null
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
                        statusColor = targetRangeStatusColor(todayFat, targets.fatG),
                        onClick = { onNavigateToTrends("fat") }
                    )
                    MetricBlock(
                        modifier = Modifier.weight(1f),
                        label = "NET CARBS",
                        value = "%.1f".format(todayCarbs),
                        unit = "g",
                        targetLabel = "target %.0f".format(targets.netCarbsG),
                        diff = todayCarbs - targets.netCarbsG,
                        statusColor = strictLimitStatusColor(todayCarbs, targets.netCarbsG),
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
                        volUnit = volUnit,
                        onClick = { onNavigateToTrends("hydration") }
                    )
                    NakRatioBlock(
                        modifier = Modifier.weight(1f),
                        nakRatio = nakRatio,
                        todaySodium = todaySodium,
                        todayPotassium = todayPotassium,
                        onClick = { onNavigateToTrends("nak_ratio") }
                    )
                }
            }

            // ── Metric blocks row 4: Magnesium | Weight (if enabled) ─────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricBlock(
                        modifier = if (weightMetricEnabled) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                        label = "MAGNESIUM",
                        value = "%.0f".format(todayMagnesium),
                        unit = "mg",
                        targetLabel = "target %.0f".format(targets.magnesiumMg),
                        diff = todayMagnesium - targets.magnesiumMg,
                        statusColor = goalStatusColor(todayMagnesium, targets.magnesiumMg),
                        onClick = { onNavigateToTrends("magnesium") }
                    )
                    if (weightMetricEnabled) {
                        WeightBlock(
                            modifier = Modifier.weight(1f),
                            lastEntry = lastWeightEntry,
                            weightUnit = weightUnit,
                            onClick = { onNavigateToTrends("weight") },
                            onLongClick = {
                                weightInput = lastWeightEntry?.weightKg
                                    ?.let { "%.1f".format(weightUnit.fromKg(it)) } ?: ""
                                showWeightDialog = true
                            }
                        )
                    }
                }
            }

            // ── Quick-action: Log Exercise ────────────────────────────────────
            item {
                OutlinedButton(
                    onClick = { onNavigateToLogExercise(selectedDate) },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                        foodUnit = foodUnit,
                        volUnit = volUnit,
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
    onClick: () -> Unit,
    /** Optional extra line shown below the target row, e.g. "−250 burned" for calories. */
    burnedLabel: String? = null
) {
    val valueColor = if (statusColor != Color.Unspecified) statusColor.accessible() else MaterialTheme.colorScheme.onSurface
    // Use the same status color for the diff indicator so it always matches the metric health.
    val diffColor  = if (statusColor != Color.Unspecified) statusColor.accessible() else MaterialTheme.colorScheme.onSurfaceVariant
    val diffText = if (diff >= 0) "+%.1f".format(diff) else "%.1f".format(diff)

    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            if (burnedLabel != null) {
                Text(
                    text = burnedLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = SuccessGreen,
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
    volUnit: com.projectember.mobile.data.local.VolumeUnit = com.projectember.mobile.data.local.VolumeUnit.ML,
    onClick: () -> Unit
) {
    val statusColor = goalStatusColor(todayWater, targetWater)
    val barColor = if (statusColor != Color.Unspecified) statusColor else KetoAccent
    val progress = if (targetWater > 0)
        (todayWater / targetWater).coerceIn(0.0, 1.0).toFloat() else 0f
    val volSym = volUnit.symbol
    val displayToday  = volUnit.fromMl(todayWater)
    val displayTarget = volUnit.fromMl(targetWater)

    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "HYDRATION %",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$hydrationPct%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (statusColor != Color.Unspecified) statusColor.accessible() else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "%.1f / %.1f $volSym".format(displayToday, displayTarget),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                Text("100%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
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
            // Slider visual order matches the card name "Na:K":
            // Na is on the left, K is on the right.
            // position 0.0 = Na-heavy (dot left), 1.0 = K-heavy (dot right).
            // Scale: ratio 0→2 maps to position 1.0→0.0 (inverted).
            position = (1.0 - (ratio / 2.0).coerceIn(0.0, 1.0)).toFloat()
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
            position = 0.0f   // far left (Na side)
            ratioColor = ErrorRed
            dotColor = ErrorRed
        }
        else -> {
            // No data
            ratio = 0.0
            position = 0.5f   // center (no data → neutral)
            ratioColor = MaterialTheme.colorScheme.onSurface
            dotColor = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "NA:K RATIO",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = nakRatio,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = ratioColor.accessible()
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when {
                        todayPotassium <= 0 && todaySodium > 0 -> "no potassium logged"
                        todayPotassium <= 0                    -> "need data"
                        else                                   -> "Na \u00f7 K"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
                // Explicit target reference
                Text(
                    text = "target \u22641.0",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            // Balance indicator bar: left = Na-heavy, right = K-heavy (matches "Na:K" label order)
            val trackColor = MaterialTheme.colorScheme.outlineVariant
            Box(modifier = Modifier.fillMaxWidth()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                ) {
                    // Track
                    drawRoundRect(
                        color = trackColor,
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
                Text("Na", style = MaterialTheme.typography.labelSmall, color = ErrorRed, fontSize = 9.sp)
                Text("K", style = MaterialTheme.typography.labelSmall, color = SuccessGreen, fontSize = 9.sp)
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
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = "\u26a0\ufe0f Not medical advice. This tracker is for informational purposes only. Consult a healthcare professional before making dietary changes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun KetoEntryCard(
    entry: KetoEntry,
    onEditEntry: (Int) -> Unit,
    foodUnit: com.projectember.mobile.data.local.FoodWeightUnit = com.projectember.mobile.data.local.FoodWeightUnit.G,
    volUnit: com.projectember.mobile.data.local.VolumeUnit = com.projectember.mobile.data.local.VolumeUnit.ML
) {
    val foodSym = foodUnit.symbol
    val volSym  = volUnit.symbol
    val isExercise   = entry.eventType.equals("exercise",   ignoreCase = true)
    val isSupplement = entry.eventType.equals("supplement", ignoreCase = true)
    var expanded by remember { mutableStateOf(false) }

    // Format servings consistently: show as integer when whole, one decimal otherwise.
    val servingsLabel = if (entry.servings == entry.servings.toLong().toDouble())
        "${entry.servings.toLong()} srv"
    else
        "%.1f srv".format(entry.servings)

    // Exercise: dark card with a subtle green accent border; all others: standard card
    val cardBorder = if (isExercise)
        BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f))
    else
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                    color = MaterialTheme.colorScheme.onSurface,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    isExercise -> Text(
                        text = "\u2212%.0f kcal".format(entry.calories * entry.servings),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = SuccessGreen
                    )
                    else -> Text(
                        text = "%.0f kcal".format(entry.calories * entry.servings),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                val timeText = if (entry.eventTimestamp.length >= 16)
                    entry.eventTimestamp.substring(11, 16)
                else
                    entry.eventTimestamp
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isSupplement && !isExercise) {
                        Text(
                            text = servingsLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MacroDetail(label = "Protein",   value = "%.1f $foodSym".format(foodUnit.fromG(entry.proteinG  * entry.servings)))
                    MacroDetail(label = "Fat",       value = "%.1f $foodSym".format(foodUnit.fromG(entry.fatG      * entry.servings)))
                    MacroDetail(label = "Net Carbs", value = "%.1f $foodSym".format(foodUnit.fromG(entry.netCarbsG * entry.servings)))
                    if (!isSupplement && !isExercise) {
                        MacroDetail(
                            label = "Servings",
                            value = servingsLabel
                        )
                    }
                }

                if (entry.waterMl > 0 || entry.sodiumMg > 0 || entry.potassiumMg > 0 || entry.magnesiumMg > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MacroDetail(label = "Water", value = "%.1f $volSym".format(volUnit.fromMl(entry.waterMl    * entry.servings)))
                        MacroDetail(label = "Na",    value = "%.0f mg".format(entry.sodiumMg   * entry.servings))
                        MacroDetail(label = "K",     value = "%.0f mg".format(entry.potassiumMg* entry.servings))
                        MacroDetail(label = "Mg",    value = "%.0f mg".format(entry.magnesiumMg* entry.servings))
                    }
                }

                if (!entry.notes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = entry.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = entry.eventTimestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
        "supplement" -> Pair(KetoAccentLight,                              MaterialTheme.colorScheme.onSurface)
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
private fun MacroDetail(label: String, value: String, color: Color = Color.Unspecified) {
    val textColor = if (color != Color.Unspecified) color else MaterialTheme.colorScheme.onSurface
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = textColor)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.7f))
    }
}

// ── Weight block ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeightBlock(
    modifier: Modifier = Modifier,
    lastEntry: WeightEntry?,
    weightUnit: com.projectember.mobile.data.local.WeightUnit = com.projectember.mobile.data.local.WeightUnit.KG,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val weightSym = weightUnit.symbol
    Card(
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "WEIGHT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (lastEntry != null) "%.1f $weightSym".format(weightUnit.fromKg(lastEntry.weightKg)) else "--",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (lastEntry != null) lastEntry.entryDate else "--",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
    }
}
