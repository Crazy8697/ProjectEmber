package com.projectember.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectember.mobile.data.local.HealthMetric
import com.projectember.mobile.data.local.entities.ExerciseCategory
import com.projectember.mobile.data.local.entities.ExerciseEntry
import com.projectember.mobile.sync.HealthConnectManager
import com.projectember.mobile.ui.theme.KetoAccent
import com.projectember.mobile.ui.theme.OnSurfaceVariant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseScreen(
    viewModel: ExerciseViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAddEntry: (String) -> Unit,
    onNavigateToEditEntry: (Int) -> Unit
) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val entries by viewModel.selectedDateEntries.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val activityData by viewModel.activityData.collectAsState()
    val enabledMetrics by viewModel.enabledMetrics.collectAsState()

    val categoryMap = remember(categories) { categories.associateBy { it.id } }

    // Refresh HC activity summary whenever the screen is entered
    LaunchedEffect(Unit) { viewModel.refreshActivitySummary() }

    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    if (showDatePicker) {
        val initialMillis = try {
            LocalDate.parse(selectedDate, dateFormatter).toEpochDay() * 86_400_000L
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
        val dpState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    dpState.selectedDateMillis?.let { millis ->
                        val date = LocalDate.ofEpochDay(millis / 86_400_000L)
                        viewModel.setSelectedDate(date.format(dateFormatter))
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = dpState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exercise") },
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
                onClick = { onNavigateToAddEntry(selectedDate) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Log Exercise")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // ── Date selector ─────────────────────────────────────────────────
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
                        color = OnSurfaceVariant
                    )
                }
            }

            // ── Health Connect activity summary cards ─────────────────────────
            val anyActivityMetricEnabled = listOf(
                HealthMetric.STEPS,
                HealthMetric.DISTANCE,
                HealthMetric.ACTIVE_CALORIES,
            ).any { enabledMetrics[it] != false }

            if (anyActivityMetricEnabled) {
                item {
                    Text(
                        text = "TODAY'S ACTIVITY",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                when (val state = activityData) {
                    is ActivityDataState.NotInstalled -> {
                        // HC not available; silently skip activity cards
                    }
                    is ActivityDataState.Loading -> {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .height(16.dp)
                                        .padding(0.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Loading activity…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariant
                                )
                            }
                        }
                    }
                    is ActivityDataState.Error -> {
                        item {
                            Text(
                                text = "Could not load activity data",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                    is ActivityDataState.Ready -> {
                        val granted = state.grantedPermissions
                        val summary = state.summary

                        if (enabledMetrics[HealthMetric.STEPS] != false) {
                            item {
                                ActivityMetricCard(
                                    label = "STEPS",
                                    value = when {
                                        HealthConnectManager.PERM_STEPS !in granted ->
                                            null to "Permission needed"
                                        summary.stepsToday != null ->
                                            "%.0f".format(summary.stepsToday.toDouble()) to null
                                        else -> null to "No data yet"
                                    },
                                    permissionNeeded = HealthConnectManager.PERM_STEPS !in granted
                                )
                            }
                        }

                        if (enabledMetrics[HealthMetric.DISTANCE] != false) {
                            item {
                                ActivityMetricCard(
                                    label = "DISTANCE",
                                    value = when {
                                        HealthConnectManager.PERM_DISTANCE !in granted ->
                                            null to "Permission needed"
                                        summary.distanceMeters != null ->
                                            "%.2f km".format(summary.distanceMeters / 1000.0) to null
                                        else -> null to "No data yet"
                                    },
                                    permissionNeeded = HealthConnectManager.PERM_DISTANCE !in granted
                                )
                            }
                        }

                        if (enabledMetrics[HealthMetric.ACTIVE_CALORIES] != false) {
                            item {
                                val activeGranted = HealthConnectManager.PERM_ACTIVE_CALORIES in granted
                                val totalGranted = HealthConnectManager.PERM_TOTAL_CALORIES in granted
                                val permNeeded = !activeGranted && !totalGranted
                                val valueStr = when {
                                    permNeeded -> null
                                    else -> buildString {
                                        summary.activeCaloriesKcal?.let {
                                            append("%.0f kcal active".format(it))
                                        }
                                        if (summary.activeCaloriesKcal != null && summary.totalCaloriesKcal != null) append(" · ")
                                        summary.totalCaloriesKcal?.let {
                                            append("%.0f kcal total".format(it))
                                        }
                                    }.ifEmpty { null }
                                }
                                ActivityMetricCard(
                                    label = "CALORIES BURNED",
                                    value = when {
                                        permNeeded -> null to "Permission needed"
                                        valueStr != null -> valueStr to null
                                        else -> null to "No data yet"
                                    },
                                    permissionNeeded = permNeeded
                                )
                            }
                        }
                    }
                }
            }

            // ── Section divider ───────────────────────────────────────────────
            item {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

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
                            text = "No activity logged",
                            style = MaterialTheme.typography.titleMedium,
                            color = OnSurfaceVariant
                        )
                        Text(
                            text = "Tap + to log an exercise",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariant
                        )
                    }
                }
            } else {
                items(entries, key = { it.id }) { entry ->
                    ExerciseEntryCard(
                        entry = entry,
                        category = categoryMap[entry.categoryId],
                        onClick = { onNavigateToEditEntry(entry.id) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun ExerciseEntryCard(
    entry: ExerciseEntry,
    category: ExerciseCategory?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Main title: the DB category name (e.g. "Cardio", "Strength")
                Text(
                    text = category?.name ?: entry.type,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                // Meta row: time + activity type + optional duration/calories
                val metaParts = buildList {
                    add(entry.entryTime)
                    add(entry.type)
                    entry.durationMinutes?.let { add("$it min") }
                    entry.caloriesBurned?.let {
                        add("${it.toBigDecimal().stripTrailingZeros().toPlainString()} kcal")
                    }
                }
                Text(
                    text = metaParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )

                // Health Connect import badge
                if (entry.notes == HealthConnectManager.HC_IMPORT_NOTE) {
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = "Health Connect",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Subtype pill — only shown when a subtype is present
                if (!entry.subtype.isNullOrBlank()) {
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                entry.subtype!!,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = OnSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/**
 * A compact card that shows a single Health Connect activity metric.
 *
 * @param label  Short all-caps label (e.g. "STEPS")
 * @param value  Pair of (data string, empty-state string). Exactly one should be non-null.
 * @param permissionNeeded  When true the card is styled subtly to indicate a permission issue.
 */
@Composable
private fun ActivityMetricCard(
    label: String,
    value: Pair<String?, String?>,
    permissionNeeded: Boolean = false,
) {
    val (dataValue, emptyLabel) = value
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (permissionNeeded)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (permissionNeeded)
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (dataValue != null) {
                Text(
                    text = dataValue,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Text(
                    text = emptyLabel ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (permissionNeeded)
                        MaterialTheme.colorScheme.error
                    else
                        OnSurfaceVariant
                )
            }
        }
    }
}
