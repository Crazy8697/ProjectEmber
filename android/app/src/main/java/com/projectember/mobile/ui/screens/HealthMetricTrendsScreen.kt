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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectember.mobile.data.local.HealthMetric
import com.projectember.mobile.data.local.entities.ManualHealthEntry
import com.projectember.mobile.ui.theme.OnSurfaceVariant

/**
 * Trends / history screen for a single health metric.
 *
 * Shows:
 * - The metric label as the title.
 * - A simple trend graph (when graphEnabled and enough data points).
 * - A chronological list of all manual entries for this metric.
 * - FAB to add a new manual entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthMetricTrendsScreen(
    metric: HealthMetric,
    viewModel: HealthMetricTrendsViewModel,
    onNavigateBack: () -> Unit,
) {
    val entries by viewModel.entries.collectAsState()
    val graphEnabled by viewModel.graphEnabled.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var entryToDelete by remember { mutableStateOf<ManualHealthEntry?>(null) }

    // Add / edit dialog
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

    // Delete confirmation
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(metric.displayName + " History") },
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
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add entry")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // ── Trend graph (only when graphEnabled and ≥ 2 data points) ──────
            if (graphEnabled && entries.size >= 2) {
                item {
                    HealthMetricTrendGraph(
                        metric = metric,
                        entries = entries,
                    )
                }
            }

            // ── Entry list ────────────────────────────────────────────────────
            if (entries.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No entries yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = OnSurfaceVariant
                        )
                        Text(
                            text = "Tap + to add a manual reading.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariant
                        )
                    }
                }
            } else {
                item {
                    Text(
                        text = "Manual entries",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                }
                items(entries, key = { it.id }) { entry ->
                    HealthMetricEntryRow(
                        metric = metric,
                        entry = entry,
                        onDelete = { entryToDelete = entry }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

// ── Entry row ─────────────────────────────────────────────────────────────────

@Composable
private fun HealthMetricEntryRow(
    metric: HealthMetric,
    entry: ManualHealthEntry,
    onDelete: () -> Unit,
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
                Text(
                    text = "Manual",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ── Trend graph ────────────────────────────────────────────────────────────────

@Composable
private fun HealthMetricTrendGraph(
    metric: HealthMetric,
    entries: List<ManualHealthEntry>,
) {
    // Only use the last 30 entries for graph readability; list is newest-first so reverse.
    val graphEntries = entries.reversed().takeLast(30)
    val values = graphEntries.map { it.value1.toFloat() }
    if (values.isEmpty()) return

    val minVal = values.min()
    val maxVal = values.max()
    val range = if (maxVal > minVal) maxVal - minVal else 1f

    val lineColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "TREND",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            ) {
                val w = size.width
                val h = size.height
                val n = values.size
                if (n < 2) return@Canvas

                for (i in 0 until n - 1) {
                    val x1 = w * i / (n - 1)
                    val y1 = h - h * ((values[i] - minVal) / range)
                    val x2 = w * (i + 1) / (n - 1)
                    val y2 = h - h * ((values[i + 1] - minVal) / range)
                    drawLine(
                        color = lineColor,
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                // Draw dots
                for (i in 0 until n) {
                    val x = w * i / (n - 1)
                    val y = h - h * ((values[i] - minVal) / range)
                    drawCircle(color = lineColor, radius = 3.dp.toPx(), center = Offset(x, y))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = graphEntries.firstOrNull()?.let {
                        "${it.entryDate.takeLast(5)}"
                    } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant,
                    fontSize = 10.sp
                )
                Text(
                    text = graphEntries.lastOrNull()?.let {
                        "${it.entryDate.takeLast(5)}"
                    } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant,
                    fontSize = 10.sp,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

// ── Add entry dialog ──────────────────────────────────────────────────────────

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

/** Returns (label1, label2, unit1, unit2) for each metric type. */
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

/** Format a ManualHealthEntry value for display in the entry list. */
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
