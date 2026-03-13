package com.projectember.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.sp
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
    onNavigateToTargets: () -> Unit,
    onNavigateToTrends: (String) -> Unit
) {
    val selectedDateEntries by viewModel.selectedDateEntries.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val targets by viewModel.targets.collectAsState()

    var showHelp by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

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
                        todayPotassium = todayPotassium,
                        onClick = { onNavigateToTrends("sodium") }
                    )
                }
            }

            // ── Daily Totals section ─────────────────────────────────────────
            item {
                SummaryTableCard(
                    title = "Daily Totals",
                    rows = listOf(
                        SummaryRow("Calories",  "%.0f".format(todayCalories),  "%.0f".format(targets.caloriesKcal),  todayCalories - targets.caloriesKcal,  false),
                        SummaryRow("Protein",   "%.1fg".format(todayProtein),  "%.0fg".format(targets.proteinG),    todayProtein - targets.proteinG,   true),
                        SummaryRow("Fat",       "%.1fg".format(todayFat),      "%.0fg".format(targets.fatG),        todayFat - targets.fatG,           false),
                        SummaryRow("Net Carbs", "%.1fg".format(todayCarbs),    "%.0fg".format(targets.netCarbsG),   todayCarbs - targets.netCarbsG,    false)
                    )
                )
            }

            // ── Electrolytes + Water section ─────────────────────────────────
            item {
                SummaryTableCard(
                    title = "Electrolytes + Water",
                    rows = listOf(
                        SummaryRow("Water",     "%.0f mL".format(todayWater),    "%.0f mL".format(targets.waterMl),    todayWater - targets.waterMl,       true),
                        SummaryRow("Sodium",    "%.0f mg".format(todaySodium),   "%.0f mg".format(targets.sodiumMg),   todaySodium - targets.sodiumMg,     false),
                        SummaryRow("Potassium", "%.0f mg".format(todayPotassium),"%.0f mg".format(targets.potassiumMg),todayPotassium - targets.potassiumMg,true),
                        SummaryRow("Magnesium", "%.0f mg".format(todayMagnesium),"%.0f mg".format(targets.magnesiumMg),todayMagnesium - targets.magnesiumMg,true)
                    )
                )
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
                    KetoEntryCard(entry = entry, onEditEntry = onNavigateToEditEntry)
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
        }
    }
}

// ── Na:K ratio block ─────────────────────────────────────────────────────────
@Composable
private fun NakRatioBlock(
    modifier: Modifier = Modifier,
    nakRatio: String,
    todayPotassium: Double,
    onClick: () -> Unit
) {
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
                color = OnSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (todayPotassium <= 0) "need potassium" else "Na \u00f7 K",
                style = MaterialTheme.typography.labelSmall,
                color = KetoMuted,
                fontSize = 10.sp
            )
        }
    }
}

// ── Summary table card ────────────────────────────────────────────────────────
private data class SummaryRow(
    val metric: String,
    val total: String,
    val target: String,
    val diff: Double,
    val isGoal: Boolean
)

@Composable
private fun SummaryTableCard(title: String, rows: List<SummaryRow>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = KetoCard),
        border = BorderStroke(1.dp, KetoBorderC)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = KetoAccent
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("METRIC", Modifier.weight(2f), style = MaterialTheme.typography.labelSmall, color = KetoMuted)
                Text("TOTAL",  Modifier.weight(2f), style = MaterialTheme.typography.labelSmall, color = KetoMuted)
                Text("TARGET", Modifier.weight(2f), style = MaterialTheme.typography.labelSmall, color = KetoMuted)
                Text("DIFF",   Modifier.weight(2f), style = MaterialTheme.typography.labelSmall, color = KetoMuted)
            }
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = KetoBorderC)
            rows.forEach { row ->
                Spacer(modifier = Modifier.height(6.dp))
                val diffColor = when {
                    row.diff == 0.0 -> KetoMuted
                    row.isGoal      -> if (row.diff >= 0) SuccessGreen else ErrorRed
                    else            -> if (row.diff <= 0) SuccessGreen else ErrorRed
                }
                val diffText = if (row.diff >= 0) "+%.1f".format(row.diff) else "%.1f".format(row.diff)
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(row.metric, Modifier.weight(2f), style = MaterialTheme.typography.bodySmall, color = OnSurface)
                    Text(row.total,  Modifier.weight(2f), style = MaterialTheme.typography.bodySmall, color = OnSurface)
                    Text(row.target, Modifier.weight(2f), style = MaterialTheme.typography.bodySmall, color = KetoMuted)
                    Text(diffText,   Modifier.weight(2f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = diffColor)
                }
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
                    "\u2212%.0f kcal".format(entry.calories)
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
private fun MacroDetail(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = color)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
    }
}
