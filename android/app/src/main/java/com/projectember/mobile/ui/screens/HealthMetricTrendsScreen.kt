package com.projectember.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectember.mobile.data.local.HealthMetric
import com.projectember.mobile.data.local.entities.ManualHealthEntry
import com.projectember.mobile.ui.theme.OnSurfaceVariant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Unified Trends + History screen for a single health metric.
 *
 * Two sub-views toggled via the top-bar action button:
 *
 * **Graph/Trends view** (default — opened when tapping a metric card):
 * - Latest value summary card (manual priority, then HC).
 * - FROM / TO date range pickers (default: last 30 days).
 * - Line chart of all entries (manual + HC) in the selected date range.
 * - "History" action button in the top bar.
 *
 * **History/Edit view** (opened via "History" button):
 * - Chronological list of both manual Ember entries and HC imported entries.
 * - FAB to add a new manual entry — this is the edit surface for the metric.
 * - "Return to graph" action button in the top bar.
 * - Back arrow also returns to the graph view (not exit).
 * - Delete button shown only on manual entries (HC entries are read-only).
 *
 * Manual Ember entries take display priority.
 * HC historical data enriches the view but is never written back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthMetricTrendsScreen(
    metric: HealthMetric,
    viewModel: HealthMetricTrendsViewModel,
    onNavigateBack: () -> Unit,
) {
    val entries by viewModel.entries.collectAsState()
    val fromDate by viewModel.fromDate.collectAsState()
    val toDate by viewModel.toDate.collectAsState()
    val hcLoadState by viewModel.hcLoadState.collectAsState()

    // false = Graph/Trends view (default); true = History/Edit view
    var showHistory by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var entryToDelete by remember { mutableStateOf<ManualHealthEntry?>(null) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // Filter entries to the selected date range, oldest-first, for the chart.
    val graphEntries = remember(entries, fromDate, toDate) {
        entries
            .filter { it.entryDate >= fromDate && it.entryDate <= toDate }
            .reversed()
    }

    // ── Date pickers ──────────────────────────────────────────────────────────
    if (showFromPicker) {
        val initMillis = runCatching {
            LocalDate.parse(fromDate, dateFormatter).toEpochDay() * 86_400_000L
        }.getOrDefault(System.currentTimeMillis())
        val dpState = rememberDatePickerState(initialSelectedDateMillis = initMillis)
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showFromPicker = false
                    dpState.selectedDateMillis?.let { millis ->
                        val d = LocalDate.ofEpochDay(millis / 86_400_000L)
                        viewModel.setFromDate(d.format(dateFormatter))
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showFromPicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = dpState) }
    }

    if (showToPicker) {
        val initMillis = runCatching {
            LocalDate.parse(toDate, dateFormatter).toEpochDay() * 86_400_000L
        }.getOrDefault(System.currentTimeMillis())
        val dpState = rememberDatePickerState(initialSelectedDateMillis = initMillis)
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showToPicker = false
                    dpState.selectedDateMillis?.let { millis ->
                        val d = LocalDate.ofEpochDay(millis / 86_400_000L)
                        viewModel.setToDate(d.format(dateFormatter))
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showToPicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = dpState) }
    }

    // ── Add entry dialog ──────────────────────────────────────────────────────
    if (showAddDialog) {
        HealthMetricEntryDialog(
            metric = metric,
            onDismiss = { showAddDialog = false },
            onSave = { v1, v2, date, time ->
                viewModel.saveEntry(v1, v2, date, time)
                showAddDialog = false
            }
        )
    }

    // ── Delete confirmation ───────────────────────────────────────────────────
    entryToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Delete entry?") },
            text = { Text("Remove this ${metric.displayName} reading from ${entry.entryDate}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEntry(entry)
                    entryToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // ── Scaffold ──────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (showHistory) "${metric.displayName} History"
                        else "${metric.displayName} Trends"
                    )
                },
                navigationIcon = {
                    // In history view the back arrow returns to graph; in graph view it exits.
                    IconButton(
                        onClick = if (showHistory) ({ showHistory = false }) else onNavigateBack
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (showHistory) {
                        OutlinedButton(
                            onClick = { showHistory = false },
                            modifier = Modifier.padding(end = 8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) { Text("Return to graph") }
                    } else {
                        OutlinedButton(
                            onClick = { showHistory = true },
                            modifier = Modifier.padding(end = 8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) { Text("History") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (showHistory) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add entry")
                }
            }
        }
    ) { paddingValues ->
        if (showHistory) {
            HealthMetricHistoryContent(
                metric = metric,
                entries = entries,
                hcLoadState = hcLoadState,
                paddingValues = paddingValues,
                onDeleteRequest = { entryToDelete = it }
            )
        } else {
            HealthMetricGraphContent(
                metric = metric,
                latestEntry = entries.firstOrNull(),
                graphEntries = graphEntries,
                fromDate = fromDate,
                toDate = toDate,
                hcLoadState = hcLoadState,
                paddingValues = paddingValues,
                onShowFromPicker = { showFromPicker = true },
                onShowToPicker = { showToPicker = true },
            )
        }
    }
}

// ── Graph / Trends content ────────────────────────────────────────────────────

@Composable
private fun HealthMetricGraphContent(
    metric: HealthMetric,
    latestEntry: ManualHealthEntry?,
    graphEntries: List<ManualHealthEntry>,
    fromDate: String,
    toDate: String,
    hcLoadState: HcLoadState,
    paddingValues: PaddingValues,
    onShowFromPicker: () -> Unit,
    onShowToPicker: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // ── Latest value card ─────────────────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "LATEST",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (latestEntry != null) {
                        Text(
                            text = formatEntryValue(metric, latestEntry),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${latestEntry.entryDate}  ${latestEntry.entryTime}",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant
                        )
                        val sourceLabel = if (latestEntry.source == ManualHealthEntry.SOURCE_MANUAL)
                            "Manual · Ember only" else "Health Connect"
                        Text(
                            text = sourceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 10.sp
                        )
                    } else if (hcLoadState == HcLoadState.Loading) {
                        Text(
                            text = "Loading…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "No data yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tap  History → +  to add a manual reading",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // ── Date range selector ───────────────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "FROM",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant,
                            letterSpacing = 0.8.sp
                        )
                        TextButton(
                            onClick = onShowFromPicker,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = fromDate,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = "—",
                        color = OnSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "TO",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant,
                            letterSpacing = 0.8.sp
                        )
                        TextButton(
                            onClick = onShowToPicker,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = toDate,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // ── Trend chart ───────────────────────────────────────────────────────
        item {
            MetricTrendChart(
                metric = metric,
                graphEntries = graphEntries,
                hcLoadState = hcLoadState
            )
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun MetricTrendChart(
    metric: HealthMetric,
    graphEntries: List<ManualHealthEntry>,
    hcLoadState: HcLoadState = HcLoadState.Done,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "TREND",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (graphEntries.size < 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (hcLoadState == HcLoadState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            text = when {
                                graphEntries.isEmpty() -> "No data in selected range"
                                else -> "Need at least 2 data points to draw a trend"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val displayEntries = graphEntries.takeLast(30)
                val values = displayEntries.map { it.value1.toFloat() }
                val minVal = values.min()
                val maxVal = values.max()
                val range = if (maxVal > minVal) maxVal - minVal else 1f
                val lineColor = healthMetricGraphColor(metric)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val n = values.size
                    val padH = 8.dp.toPx()
                    val padV = 8.dp.toPx()
                    val drawW = w - 2 * padH
                    val drawH = h - 2 * padV

                    for (i in 0 until n - 1) {
                        val x1 = padH + drawW * i / (n - 1)
                        val y1 = padV + drawH * (1f - (values[i] - minVal) / range)
                        val x2 = padH + drawW * (i + 1) / (n - 1)
                        val y2 = padV + drawH * (1f - (values[i + 1] - minVal) / range)
                        drawLine(
                            color = lineColor,
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
                            strokeWidth = 2.5f.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                    for (i in 0 until n) {
                        val x = padH + drawW * i / (n - 1)
                        val y = padV + drawH * (1f - (values[i] - minVal) / range)
                        drawCircle(
                            color = lineColor,
                            radius = 4.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = displayEntries.firstOrNull()?.entryDate?.takeLast(5) ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "${displayEntries.size} readings",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant,
                        fontSize = 10.sp
                    )
                    Text(
                        text = displayEntries.lastOrNull()?.entryDate?.takeLast(5) ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant,
                        fontSize = 10.sp,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

// ── History / Edit content ────────────────────────────────────────────────────

@Composable
private fun HealthMetricHistoryContent(
    metric: HealthMetric,
    entries: List<ManualHealthEntry>,
    hcLoadState: HcLoadState,
    paddingValues: PaddingValues,
    onDeleteRequest: (ManualHealthEntry) -> Unit,
) {
    val hasManual = entries.any { it.source == ManualHealthEntry.SOURCE_MANUAL }
    val hasHc = entries.any { it.source == ManualHealthEntry.SOURCE_HEALTH_CONNECT }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        if (entries.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hcLoadState == HcLoadState.Loading) {
                        CircularProgressIndicator()
                        Text(
                            text = "Loading Health Connect history…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "No entries in selected range",
                            style = MaterialTheme.typography.titleMedium,
                            color = OnSurfaceVariant
                        )
                        Text(
                            text = "Tap + to add a manual reading.\n" +
                                "Health Connect history will appear here when available.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            item {
                val label = when {
                    hasManual && hasHc -> "ALL ENTRIES  (manual + Health Connect)"
                    hasHc -> "HEALTH CONNECT ENTRIES"
                    else -> "MANUAL ENTRIES"
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }
            // Use composite key to support both manual (positive id) and HC (id = 0) entries
            items(
                items = entries,
                key = { "${it.source}_${it.entryDate}_${it.entryTime}_${it.value1}" }
            ) { entry ->
                HealthMetricEntryRow(
                    metric = metric,
                    entry = entry,
                    onDelete = if (entry.source == ManualHealthEntry.SOURCE_MANUAL) {
                        { onDeleteRequest(entry) }
                    } else null
                )
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

// ── Entry row ─────────────────────────────────────────────────────────────────

@Composable
private fun HealthMetricEntryRow(
    metric: HealthMetric,
    entry: ManualHealthEntry,
    /** Non-null only for manual entries; null = read-only HC entry (no delete button). */
    onDelete: (() -> Unit)?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatEntryValue(metric, entry),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${entry.entryDate}  ${entry.entryTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
                val sourceLabel = if (entry.source == ManualHealthEntry.SOURCE_MANUAL)
                    "Manual · Ember only" else "Health Connect"
                Text(
                    text = sourceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                // Spacer to keep card height consistent
                Spacer(modifier = Modifier.size(48.dp))
            }
        }
    }
}

// ── Add / edit entry dialog ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthMetricEntryDialog(
    metric: HealthMetric,
    initialValue1: String = "",
    initialValue2: String = "",
    initialDate: String = java.time.LocalDate.now().toString(),
    onDismiss: () -> Unit,
    onSave: (value1: Double, value2: Double?, date: String, time: String) -> Unit,
) {
    var value1Input by remember { mutableStateOf(initialValue1) }
    var value2Input by remember { mutableStateOf(initialValue2) }
    var dateInput by remember { mutableStateOf(initialDate) }
    var value1Error by remember { mutableStateOf(false) }
    var value2Error by remember { mutableStateOf(false) }

    val (label1, label2, unit1, unit2) = metricInputSpec(metric)
    val needsValue2 = metric == HealthMetric.BLOOD_PRESSURE

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${metric.displayName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value1Input,
                    onValueChange = { value1Input = it; value1Error = false },
                    label = { Text(if (unit1.isNotEmpty()) "$label1 ($unit1)" else label1) },
                    isError = value1Error,
                    supportingText = if (value1Error) {
                        { Text("Enter a valid value greater than 0") }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (needsValue2) {
                    OutlinedTextField(
                        value = value2Input,
                        onValueChange = { value2Input = it; value2Error = false },
                        label = { Text(if (unit2.isNotEmpty()) "$label2 ($unit2)" else label2) },
                        isError = value2Error,
                        supportingText = if (value2Error) {
                            { Text("Enter a valid value greater than 0") }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = dateInput,
                    onValueChange = { dateInput = it },
                    label = { Text("Date (yyyy-MM-dd)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v1 = value1Input.toDoubleOrNull()
                val v2 = if (needsValue2) value2Input.toDoubleOrNull() else null
                val v1Valid = v1 != null && v1 > 0
                val v2Valid = !needsValue2 || (v2 != null && v2 > 0)
                value1Error = !v1Valid
                value2Error = needsValue2 && !v2Valid
                if (v1Valid && v2Valid) {
                    val time = java.time.LocalTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                    onSave(v1!!, if (needsValue2) v2 else null, dateInput, time)
                }
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private data class MetricInputSpec(
    val label1: String,
    val label2: String,
    val unit1: String,
    val unit2: String,
)

private fun metricInputSpec(metric: HealthMetric): MetricInputSpec = when (metric) {
    HealthMetric.HEART_RATE -> MetricInputSpec("Heart Rate", "", "bpm", "")
    HealthMetric.RESTING_HEART_RATE -> MetricInputSpec("Resting Heart Rate", "", "bpm", "")
    HealthMetric.BLOOD_PRESSURE -> MetricInputSpec("Systolic", "Diastolic", "mmHg", "mmHg")
    HealthMetric.BLOOD_GLUCOSE -> MetricInputSpec("Blood Glucose", "", "mmol/L", "")
    HealthMetric.BODY_TEMPERATURE -> MetricInputSpec("Temperature", "", "°C", "")
    HealthMetric.OXYGEN_SATURATION -> MetricInputSpec("SpO₂", "", "%", "")
    HealthMetric.RESPIRATORY_RATE -> MetricInputSpec("Respiratory Rate", "", "breaths/min", "")
    HealthMetric.SLEEP -> MetricInputSpec("Duration", "", "hours", "")
    HealthMetric.WEIGHT -> MetricInputSpec("Weight", "", "kg", "")
    HealthMetric.STEPS -> MetricInputSpec("Steps", "", "steps", "")
    HealthMetric.DISTANCE -> MetricInputSpec("Distance", "", "km", "")
    HealthMetric.ACTIVE_CALORIES -> MetricInputSpec("Active Calories", "", "kcal", "")
    HealthMetric.EXERCISE_SESSIONS -> MetricInputSpec("Duration", "", "minutes", "")
}

/** Format a [ManualHealthEntry] value for display in the entry list and graph cards. */
fun formatEntryValue(metric: HealthMetric, entry: ManualHealthEntry): String = when (metric) {
    HealthMetric.HEART_RATE, HealthMetric.RESTING_HEART_RATE ->
        "%.0f bpm".format(entry.value1)
    HealthMetric.BLOOD_PRESSURE ->
        if (entry.value2 != null)
            "%.0f / %.0f mmHg".format(entry.value1, entry.value2)
        else "%.0f mmHg".format(entry.value1)
    HealthMetric.BLOOD_GLUCOSE -> "%.1f mmol/L".format(entry.value1)
    HealthMetric.BODY_TEMPERATURE -> "%.1f °C".format(entry.value1)
    HealthMetric.OXYGEN_SATURATION -> "%.0f%%".format(entry.value1)
    HealthMetric.RESPIRATORY_RATE -> "%.0f breaths/min".format(entry.value1)
    HealthMetric.SLEEP -> {
        val totalMinutes = (entry.value1 * 60).toInt()
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        if (h > 0) "${h}h ${m}m" else "${m}m"
    }
    HealthMetric.WEIGHT -> "%.1f kg".format(entry.value1)
    HealthMetric.STEPS -> "%.0f steps".format(entry.value1)
    HealthMetric.DISTANCE -> "%.2f km".format(entry.value1)
    HealthMetric.ACTIVE_CALORIES -> "%.0f kcal".format(entry.value1)
    HealthMetric.EXERCISE_SESSIONS -> "%.0f min".format(entry.value1)
}
