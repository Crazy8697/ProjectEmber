package com.projectember.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.projectember.mobile.data.local.WeightUnit
import com.projectember.mobile.data.local.entities.WeightEntry
import com.projectember.mobile.ui.theme.KetoAccent
import com.projectember.mobile.ui.theme.OnSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightHistoryScreen(
    viewModel: WeightHistoryViewModel,
    onNavigateBack: () -> Unit
) {
    val entries by viewModel.allEntries.collectAsState()
    val unitPrefs by viewModel.unitPreferences.collectAsState()
    val weightUnit = unitPrefs.weightUnit
    var showLogDialog by remember { mutableStateOf(false) }

    // Log weight dialog
    if (showLogDialog) {
        var weightInput by remember { mutableStateOf("") }
        var weightError by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = {
                showLogDialog = false
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
                        viewModel.logWeight(value, weightUnit)
                        showLogDialog = false
                        weightInput = ""
                        weightError = false
                    } else {
                        weightError = true
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLogDialog = false
                    weightInput = ""
                    weightError = false
                }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weight History") },
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
                onClick = { showLogDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Log Weight")
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

            // ── Chart ─────────────────────────────────────────────────────────
            if (entries.isNotEmpty()) {
                item {
                    WeightHistoryChart(entries = entries, weightUnit = weightUnit)
                }
            }

            // ── Entries list ──────────────────────────────────────────────────
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
                            text = "No weight entries yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = OnSurfaceVariant
                        )
                        Text(
                            text = "Tap + to log your weight",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariant
                        )
                    }
                }
            } else {
                item {
                    Text(
                        text = "History",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurfaceVariant
                    )
                }
                items(entries, key = { it.id }) { entry ->
                    WeightEntryRow(
                        entry = entry,
                        weightUnit = weightUnit,
                        onDelete = { viewModel.deleteEntry(entry) },
                        onEdit = { newValue -> viewModel.updateEntry(entry, newValue, weightUnit) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun WeightHistoryChart(entries: List<WeightEntry>, weightUnit: WeightUnit) {
    // `entries` arrives newest-first. Take the 30 most-recent and reverse to
    // oldest-first for the left-to-right bar chart.
    val chartEntries = entries.take(30).reversed()
    if (chartEntries.isEmpty()) return

    val symbol = weightUnit.symbol
    val chartData = chartEntries.map { entry ->
        val label = entry.entryDate.substring(5) // "MM-dd"
        label to weightUnit.fromKg(entry.weightKg).toFloat()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val displayValues = chartEntries.map { weightUnit.fromKg(it.weightKg) }
            val minVal = displayValues.min().toFloat()
            val maxVal = displayValues.max().toFloat()
            val latestWeight = displayValues.last()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "Weight ($symbol)",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurfaceVariant
                )
                Text(
                    text = "%.1f $symbol".format(latestWeight),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = KetoAccent
                )
            }
            if (maxVal > minVal) {
                Text(
                    text = "range: %.1f – %.1f $symbol".format(minVal, maxVal),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            WeeklyTrendCard(
                title = "",
                data = chartData,
                barColor = KetoAccent,
                unit = symbol,
                targetValue = null
            )
        }
    }
}

@Composable
private fun WeightEntryRow(
    entry: WeightEntry,
    weightUnit: WeightUnit,
    onDelete: () -> Unit,
    onEdit: (Double) -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    val displayWeight = weightUnit.fromKg(entry.weightKg)
    val symbol = weightUnit.symbol

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Entry") },
            text = { Text("Delete weight entry for ${entry.entryDate}?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showEditDialog) {
        var editInput by remember { mutableStateOf("%.1f".format(displayWeight)) }
        var editError by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Weight") },
            text = {
                OutlinedTextField(
                    value = editInput,
                    onValueChange = {
                        editInput = it
                        editError = false
                    },
                    label = { Text("Weight ($symbol)") },
                    isError = editError,
                    supportingText = if (editError) {
                        { Text("Enter a valid weight greater than 0") }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = editInput.toDoubleOrNull()
                    if (value != null && value > 0) {
                        onEdit(value)
                        showEditDialog = false
                    } else {
                        editError = true
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = entry.entryDate,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "%.1f $symbol".format(displayWeight),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = KetoAccent
                )
                IconButton(
                    onClick = { showEditDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

