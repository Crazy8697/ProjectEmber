package com.projectember.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectember.mobile.data.local.UnitPreferences
import com.projectember.mobile.data.local.WeightUnit
import com.projectember.mobile.data.local.entities.ManualHealthEntry
import com.projectember.mobile.data.local.entities.WeightEntry
import com.projectember.mobile.ui.theme.KetoAccent
import com.projectember.mobile.ui.theme.ErrorRed
import com.projectember.mobile.ui.theme.OnSurfaceVariant
import com.projectember.mobile.ui.theme.SuccessGreen
import com.projectember.mobile.ui.theme.WarningYellow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val METRIC_OPTIONS = listOf(
    Triple("calories",  "C",    "Calories"),
    Triple("protein",   "P",    "Protein"),
    Triple("fat",       "F",    "Fat"),
    Triple("net_carbs", "NC",   "Net Carbs"),
    Triple("hydration", "H2O",  "Water"),
    Triple("sodium",    "Na",   "Sodium"),
    Triple("potassium", "K",    "Potassium"),
    Triple("magnesium", "Mg",   "Magnesium"),
    Triple("nak_ratio", "Na:K", "Na:K Ratio"),
    Triple("weight",    "Wt",   "Weight")
)

private fun DayTotals.selectMetric(metric: String): Float = when (metric) {
    "calories"  -> calories.toFloat()
    "protein"   -> proteinG.toFloat()
    "fat"       -> fatG.toFloat()
    "net_carbs" -> netCarbsG.toFloat()
    "hydration" -> waterMl.toFloat()
    "sodium"    -> sodiumMg.toFloat()
    "potassium" -> potassiumMg.toFloat()
    "magnesium" -> magnesiumMg.toFloat()
    "nak_ratio" -> nakRatio?.toFloat() ?: 0f
    "weight"    -> weightKg?.toFloat() ?: 0f
    else        -> calories.toFloat()
}

private fun metricUnit(metric: String): String = when (metric) {
    "calories"  -> "kcal"
    "hydration" -> "mL"
    "sodium", "potassium", "magnesium" -> "mg"
    "nak_ratio" -> ":1"
    "weight"    -> "kg"
    else        -> "g"
}

private fun metricBarColor(metric: String) = ketoMetricGraphColor(metric)

private fun List<DayTotals>.rollingAvg(n: Int, selector: (DayTotals) -> Float): List<Pair<String, Float>> =
    mapIndexed { index, day ->
        val start = maxOf(0, index - n + 1)
        val window = subList(start, index + 1)
        val avg = window.map(selector).average().toFloat()
        day.label to avg
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KetoTrendsScreen(
    viewModel: KetoViewModel,
    initialMetric: String = "",
    onNavigateBack: () -> Unit,
) {
    val trendsData   by viewModel.trendsData.collectAsState()
    val trendsMetric by viewModel.trendsMetric.collectAsState()
    val fromDate     by viewModel.trendsFromDate.collectAsState()
    val toDate       by viewModel.trendsToDate.collectAsState()
    val mode         by viewModel.trendsMode.collectAsState()
    val rollingDays  by viewModel.trendsRollingDays.collectAsState()
    val targets      by viewModel.targets.collectAsState()
    val unitPrefs    by viewModel.unitPreferences.collectAsState()
    val ketoManualEntries by viewModel.ketoManualEntriesForTrends.collectAsState()
    val weightEntriesForTrends by viewModel.weightEntriesForTrends.collectAsState()

    // false = Graph/Trends view (default); true = History/Edit view
    var showHistory by remember { mutableStateOf(false) }

    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker   by remember { mutableStateOf(false) }

    // Weight entry dialog state (used when trendsMetric == "weight" and showHistory == true)
    var showAddWeightDialog by remember { mutableStateOf(false) }

    // Keto metric manual entry dialog state (non-weight measurable metrics)
    var showAddKetoMetricDialog by remember { mutableStateOf(false) }
    var ketoManualEntryToDelete by remember { mutableStateOf<ManualHealthEntry?>(null) }
    var weightEntryToDelete by remember { mutableStateOf<WeightEntry?>(null) }

    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // Apply initial metric from navigation argument once
    LaunchedEffect(initialMetric) {
        if (initialMetric.isNotBlank()) {
            viewModel.setTrendsMetric(initialMetric)
        }
    }

    // Build chart data — for weight/hydration, convert stored values to selected display units
    val weightUnit: WeightUnit = unitPrefs.weightUnit
    val volUnit = unitPrefs.volumeUnit
    val foodUnit = unitPrefs.foodWeightUnit
    val selector: (DayTotals) -> Float = { day ->
        when (trendsMetric) {
            "weight"    -> weightUnit.fromKg(day.weightKg ?: 0.0).toFloat()
            "hydration" -> volUnit.fromMl(day.waterMl).toFloat()
            "protein"   -> foodUnit.fromG(day.proteinG).toFloat()
            "fat"       -> foodUnit.fromG(day.fatG).toFloat()
            "net_carbs" -> foodUnit.fromG(day.netCarbsG).toFloat()
            else        -> day.selectMetric(trendsMetric)
        }
    }
    val rawData = trendsData.map { it.label to selector(it) }
    val chartData: List<Pair<String, Float>> = when {
        mode == "rolling" && trendsData.isNotEmpty() ->
            trendsData.rollingAvg(rollingDays, selector)
        else -> rawData
    }
    // For weight, strip days with no recorded entry so the trend line never drops to zero.
    val displayChartData: List<Pair<String, Float>> =
        if (trendsMetric == "weight") chartData.filter { it.second > 0f } else chartData
    val targetValue: Float? = when (trendsMetric) {
        "calories"  -> targets.caloriesKcal.toFloat()
        "protein"   -> foodUnit.fromG(targets.proteinG).toFloat()
        "fat"       -> foodUnit.fromG(targets.fatG).toFloat()
        "net_carbs" -> foodUnit.fromG(targets.netCarbsG).toFloat()
        "hydration" -> volUnit.fromMl(targets.waterMl).toFloat()
        "sodium"    -> targets.sodiumMg.toFloat()
        "potassium" -> targets.potassiumMg.toFloat()
        "magnesium" -> targets.magnesiumMg.toFloat()
        "nak_ratio" -> 1.0f  // 1:1 is ideal Na:K balance
        "weight"    -> null   // No fixed target for body weight
        else        -> null
    }

    // Current displayed value: last meaningful data point in the chart
    val currentDisplayValue: Float? = displayChartData.lastOrNull { it.second > 0 }?.second

    // From date picker
    if (showFromPicker) {
        val initMillis = try {
            LocalDate.parse(fromDate, dateFormatter).toEpochDay() * 86_400_000L
        } catch (_: Exception) { System.currentTimeMillis() }
        val state = rememberDatePickerState(initialSelectedDateMillis = initMillis)
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showFromPicker = false
                    state.selectedDateMillis?.let { millis ->
                        val d = LocalDate.ofEpochDay(millis / 86_400_000L)
                        viewModel.setTrendsFromDate(d.format(dateFormatter))
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showFromPicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = state) }
    }

    // To date picker
    if (showToPicker) {
        val initMillis = try {
            LocalDate.parse(toDate, dateFormatter).toEpochDay() * 86_400_000L
        } catch (_: Exception) { System.currentTimeMillis() }
        val state = rememberDatePickerState(initialSelectedDateMillis = initMillis)
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showToPicker = false
                    state.selectedDateMillis?.let { millis ->
                        val d = LocalDate.ofEpochDay(millis / 86_400_000L)
                        viewModel.setTrendsToDate(d.format(dateFormatter))
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showToPicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = state) }
    }

    val metricLabel = METRIC_OPTIONS.firstOrNull { it.first == trendsMetric }?.third ?: trendsMetric

    // Weight entry dialog — only shown when trendsMetric == "weight"
    if (showAddWeightDialog) {
        var weightInput by remember { mutableStateOf("") }
        var weightError by remember { mutableStateOf(false) }
        var entryDate by remember { mutableStateOf(LocalDate.now().format(dateFormatter)) }
        var showEntryDatePicker by remember { mutableStateOf(false) }

        if (showEntryDatePicker) {
            val initMillis = try {
                LocalDate.parse(entryDate, dateFormatter).toEpochDay() * 86_400_000L
            } catch (_: Exception) { System.currentTimeMillis() }
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initMillis)
            DatePickerDialog(
                onDismissRequest = { showEntryDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        showEntryDatePicker = false
                        datePickerState.selectedDateMillis?.let { millis ->
                            entryDate = LocalDate.ofEpochDay(millis / 86_400_000L).format(dateFormatter)
                        }
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showEntryDatePicker = false }) { Text("Cancel") }
                }
            ) { DatePicker(state = datePickerState) }
        }

        AlertDialog(
            onDismissRequest = {
                showAddWeightDialog = false
                weightInput = ""
                weightError = false
            },
            title = { Text("Log Weight") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showEntryDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Date: $entryDate")
                    }
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
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = weightInput.toDoubleOrNull()
                    if (value != null && value > 0) {
                        viewModel.logWeightForDate(weightUnit.toKg(value), entryDate)
                        showAddWeightDialog = false
                        weightInput = ""
                        weightError = false
                    } else {
                        weightError = true
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddWeightDialog = false
                    weightInput = ""
                    weightError = false
                }) { Text("Cancel") }
            }
        )
    }

    // ── Keto metric manual entry dialog ──────────────────────────────────────
    if (showAddKetoMetricDialog) {
        KetoMetricManualEntryDialog(
            metric = trendsMetric,
            metricLabel = metricLabel,
            unitPrefs = unitPrefs,
            dateFormatter = dateFormatter,
            onDismiss = { showAddKetoMetricDialog = false },
            onSave = { value, date, time ->
                viewModel.saveKetoManualEntry(trendsMetric, value, date, time)
                showAddKetoMetricDialog = false
            }
        )
    }

    // ── Delete confirmation for keto manual entries ───────────────────────────
    ketoManualEntryToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { ketoManualEntryToDelete = null },
            title = { Text("Delete entry?") },
            text = { Text("Remove this $metricLabel reading from ${entry.entryDate}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteKetoManualEntry(entry)
                    ketoManualEntryToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { ketoManualEntryToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // ── Delete confirmation for weight entries ────────────────────────────────
    weightEntryToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { weightEntryToDelete = null },
            title = { Text("Delete entry?") },
            text = { Text("Remove weight entry from ${entry.entryDate}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteWeightEntry(entry)
                    weightEntryToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { weightEntryToDelete = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (showHistory) "$metricLabel History" else "Trends")
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
                when {
                    trendsMetric == "weight" ->
                        FloatingActionButton(onClick = { showAddWeightDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add weight entry")
                        }
                    viewModel.ketoMetricManualType(trendsMetric) != null ->
                        FloatingActionButton(onClick = { showAddKetoMetricDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add $metricLabel entry")
                        }
                    // nak_ratio is a computed ratio — no standalone manual entry
                }
            }
        }
    ) { paddingValues ->
        if (showHistory) {
            KetoMetricHistoryContent(
                trendsMetric = trendsMetric,
                metricLabel = metricLabel,
                trendsData = trendsData,
                unitPrefs = unitPrefs,
                paddingValues = paddingValues,
                manualEntries = ketoManualEntries,
                weightEntries = weightEntriesForTrends,
                onDeleteManualEntry = { ketoManualEntryToDelete = it },
                onDeleteWeightEntry = { weightEntryToDelete = it },
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ── Metric selector ──────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "METRIC",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Row 1: Calories, Protein, Fat, Net Carbs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        METRIC_OPTIONS.take(4).forEach { (key, abbr, fullName) ->
                            val selected = trendsMetric == key
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setTrendsMetric(key) },
                                label = {
                                     Column(
                                         modifier = Modifier.fillMaxWidth(),
                                         horizontalAlignment = Alignment.CenterHorizontally
                                     ) {
                                         Text(abbr, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                         Text(fullName, fontSize = 8.sp, color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
                                     }
                                 },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = KetoAccent,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    // Row 2: Water, Sodium, Potassium, Magnesium
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        METRIC_OPTIONS.subList(4, 8).forEach { (key, abbr, fullName) ->
                            val selected = trendsMetric == key
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setTrendsMetric(key) },
                                label = {
                                     Column(
                                         modifier = Modifier.fillMaxWidth(),
                                         horizontalAlignment = Alignment.CenterHorizontally
                                     ) {
                                         Text(abbr, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                         Text(fullName, fontSize = 8.sp, color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
                                     }
                                 },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = KetoAccent,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    // Row 3: Na:K Ratio, Weight
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Na:K Ratio chip
                        val nakOption = METRIC_OPTIONS.first { it.first == "nak_ratio" }
                        val (nakKey, nakAbbr, nakFullName) = nakOption
                        val nakSelected = trendsMetric == nakKey
                        FilterChip(
                            selected = nakSelected,
                            onClick = { viewModel.setTrendsMetric(nakKey) },
                            label = {
                                 Column(
                                     modifier = Modifier.fillMaxWidth(),
                                     horizontalAlignment = Alignment.CenterHorizontally
                                 ) {
                                     Text(nakAbbr, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                     Text(nakFullName, fontSize = 8.sp, color = if (nakSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
                                 }
                             },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFF8C42),
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                        // Weight chip
                        val wtOption = METRIC_OPTIONS.first { it.first == "weight" }
                        val (wtKey, wtAbbr, wtFullName) = wtOption
                        val wtSelected = trendsMetric == wtKey
                        FilterChip(
                            selected = wtSelected,
                            onClick = { viewModel.setTrendsMetric(wtKey) },
                            label = {
                                 Column(
                                     modifier = Modifier.fillMaxWidth(),
                                     horizontalAlignment = Alignment.CenterHorizontally
                                 ) {
                                     Text(wtAbbr, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                     Text(wtFullName, fontSize = 8.sp, color = if (wtSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
                                 }
                             },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF9C69E2),
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                        // Two empty spacers to keep chip width consistent with rows above
                        Spacer(modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // ── Date range ───────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("FROM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(
                            onClick = { showFromPicker = true },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = fromDate,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = KetoAccent
                            )
                        }
                    }
                    Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(
                            onClick = { showToPicker = true },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = toDate,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = KetoAccent
                            )
                        }
                    }
                }
            }

            // ── Mode + rolling average selector ─────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text("MODE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = mode == "daily",
                            onClick = { viewModel.setTrendsMode("daily") },
                            label = { Text("Daily") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = KetoAccent,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                        FilterChip(
                            selected = mode == "rolling",
                            onClick = { viewModel.setTrendsMode("rolling") },
                            label = { Text("Rolling Avg") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = KetoAccent,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                    if (mode == "rolling") {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Window:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            listOf(3, 7, 14).forEach { n ->
                                FilterChip(
                                    selected = rollingDays == n,
                                    onClick = { viewModel.setTrendsRollingDays(n) },
                                    label = { Text("${n}d") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = KetoAccent,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // ── Chart card ───────────────────────────────────────────────────
            // For weight metric: treat all-null weightKg as "no weight data" even if there are
            // date slots.  We check the source trendsData rather than the converted float values
            // so that a hypothetical 0.0 kg entry (not physically meaningful but technically
            // possible) would still render rather than triggering the empty state.
            val hasData = displayChartData.isNotEmpty() &&
                (trendsMetric != "weight" || trendsData.any { it.weightKg != null }) &&
                (trendsMetric != "nak_ratio" || trendsData.any { it.nakRatio != null })
            if (hasData) {
                val metricLabel = METRIC_OPTIONS.firstOrNull { it.first == trendsMetric }?.third ?: trendsMetric
                val unit = when (trendsMetric) {
                    "weight"    -> weightUnit.symbol
                    "hydration" -> volUnit.symbol
                    "protein", "fat", "net_carbs" -> foodUnit.symbol
                    else        -> metricUnit(trendsMetric)
                }
                val barColor = metricBarColor(trendsMetric)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val modeLabel = if (mode == "rolling") " (${rollingDays}d avg)" else ""
                        Text(
                            text = "$metricLabel ($unit)$modeLabel",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        // ── Numeric summary ──────────────────────────────────
                        if (currentDisplayValue != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                // Current value
                                val valueText = when (trendsMetric) {
                                    "nak_ratio" -> "%.2f:1".format(currentDisplayValue)
                                    "weight"    -> "%.1f $unit".format(currentDisplayValue)
                                    "calories"  -> "%.0f $unit".format(currentDisplayValue)
                                    else        -> "%.1f $unit".format(currentDisplayValue)
                                }
                                Text(
                                    text = valueText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = barColor
                                )
                                // Target + diff (where meaningful)
                                if (targetValue != null && targetValue > 0) {
                                    val diff = currentDisplayValue - targetValue
                                    val diffText = if (diff >= 0)
                                        "+%.1f".format(diff) else "%.1f".format(diff)
                                    // Normalize deviation by target for tolerance-band comparisons.
                                    val deviation = diff / targetValue
                                    val diffColor = when (trendsMetric) {
                                        // Two-sided target: ±15 % green, ±30 % yellow, outside red
                                        "calories", "fat" -> when {
                                            kotlin.math.abs(deviation) <= 0.15f -> SuccessGreen
                                            kotlin.math.abs(deviation) <= 0.30f -> WarningYellow
                                            else                                 -> ErrorRed
                                        }
                                        // Strict upper limit: clearly over = red, just over = yellow
                                        "net_carbs", "sodium" -> when {
                                            deviation > 0.20f -> ErrorRed
                                            deviation > 0f    -> WarningYellow
                                            else              -> SuccessGreen
                                        }
                                        // Na:K ratio: target 1.0; >2.0 = red, 1.0–2.0 = yellow
                                        "nak_ratio" -> when {
                                            currentDisplayValue > 2.0f -> ErrorRed
                                            currentDisplayValue > 1.0f -> WarningYellow
                                            else                        -> SuccessGreen
                                        }
                                        // Goal metrics: -15 % green, -40 % yellow, further red
                                        else -> when {
                                            deviation >= -0.15f -> SuccessGreen
                                            deviation >= -0.40f -> WarningYellow
                                            else                -> ErrorRed
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "target %.1f".format(targetValue),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            text = diffText,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = diffColor.accessible(),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        WeeklyTrendCard(
                            title = "",
                            data = displayChartData,
                            barColor = barColor,
                            unit = unit,
                            targetValue = targetValue,
                            useLineChart = (trendsMetric == "weight"),
                        )
                    }
                }
            } else {
                Text(
                    text = "No data for the selected date range.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
        } // end else (graph view)
    }
}

// ── History / Edit content for Keto metrics ───────────────────────────────────

@Composable
private fun KetoMetricHistoryContent(
    trendsMetric: String,
    metricLabel: String,
    trendsData: List<DayTotals>,
    unitPrefs: UnitPreferences,
    paddingValues: PaddingValues,
    manualEntries: List<ManualHealthEntry> = emptyList(),
    weightEntries: List<WeightEntry> = emptyList(),
    onDeleteManualEntry: ((ManualHealthEntry) -> Unit)? = null,
    onDeleteWeightEntry: ((WeightEntry) -> Unit)? = null,
) {
    val weightUnit = unitPrefs.weightUnit
    val volUnit = unitPrefs.volumeUnit
    val foodUnit = unitPrefs.foodWeightUnit

    // For weight metric, use raw WeightEntry list; for all others use DayTotals aggregates.
    val historyRows = if (trendsMetric == "weight") {
        emptyList() // weight renders from weightEntries below
    } else {
        // Build a list of days that have a non-zero value for this metric, newest first.
        trendsData
            .filter { day ->
                when (trendsMetric) {
                    "nak_ratio" -> day.nakRatio != null
                    else        -> day.selectMetric(trendsMetric) > 0f
                }
            }
            .sortedByDescending { it.date }
    }

    val unit = when (trendsMetric) {
        "weight"    -> weightUnit.symbol
        "hydration" -> volUnit.symbol
        "protein", "fat", "net_carbs" -> foodUnit.symbol
        else        -> metricUnit(trendsMetric)
    }

    // Format a ManualHealthEntry value for display
    fun formatManualValue(entry: ManualHealthEntry): String = when (trendsMetric) {
        "weight"    -> "%.1f %s".format(weightUnit.fromKg(entry.value1), weightUnit.symbol)
        "hydration" -> "%.0f %s".format(volUnit.fromMl(entry.value1), volUnit.symbol)
        "protein", "fat", "net_carbs" ->
            "%.1f %s".format(foodUnit.fromG(entry.value1), foodUnit.symbol)
        "calories"  -> "%.0f kcal".format(entry.value1)
        "sodium", "potassium", "magnesium" -> "%.0f mg".format(entry.value1)
        else        -> "%.1f".format(entry.value1)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // ── Manual entries section (non-weight keto metrics) ─────────────────
        if (manualEntries.isNotEmpty()) {
            item {
                Text(
                    text = "MANUAL ENTRIES  ($unit)",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }
            items(manualEntries, key = { "manual_${it.id}" }) { entry ->
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = formatManualValue(entry),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = ketoMetricGraphColor(trendsMetric)
                            )
                            Text(
                                text = "${entry.entryDate}  ${entry.entryTime}",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant
                            )
                            Text(
                                text = "Manual · Ember only",
                                style = MaterialTheme.typography.labelSmall,
                                color = OnSurfaceVariant.copy(alpha = 0.6f),
                                fontSize = 10.sp
                            )
                        }
                        if (onDeleteManualEntry != null) {
                            IconButton(onClick = { onDeleteManualEntry(entry) }) {
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
            }
        }

        // ── Weight history section (raw WeightEntry rows) ────────────────────
        if (trendsMetric == "weight") {
            if (weightEntries.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No $metricLabel entries in selected range",
                            style = MaterialTheme.typography.titleMedium,
                            color = OnSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Tap + to log a weight entry.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                item {
                    Text(
                        text = "WEIGHT HISTORY  ($unit)",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                }
                items(weightEntries, key = { "weight_${it.id}" }) { entry ->
                    val displayValue = "%.1f %s".format(
                        weightUnit.fromKg(entry.weightKg), weightUnit.symbol
                    )
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
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayValue,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ketoMetricGraphColor(trendsMetric)
                                )
                                Text(
                                    text = entry.entryDate,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariant
                                )
                                if (entry.source != null) {
                                    Text(
                                        text = "Health Connect",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OnSurfaceVariant.copy(alpha = 0.6f),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            // Only manual entries (source == null) can be deleted
                            if (onDeleteWeightEntry != null && entry.source == null) {
                                IconButton(onClick = { onDeleteWeightEntry(entry) }) {
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
                }
            }
        } else {
        // ── Food-diary aggregate section ──────────────────────────────────────
        if (historyRows.isEmpty() && manualEntries.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "No $metricLabel entries in selected range",
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = when {
                            trendsMetric == "nak_ratio" ->
                                "Log food entries with sodium and potassium in the Keto diary to see data here."
                            else ->
                                "Tap + to add a manual reading, or log food entries in the Keto diary."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (historyRows.isNotEmpty()) {
            item {
                Text(
                    text = "$metricLabel FOOD DIARY  ($unit)",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }

            items(historyRows, key = { "diary_${it.date}" }) { day ->
                val displayValue: String = when (trendsMetric) {
                    "hydration" -> "%.0f %s".format(volUnit.fromMl(day.waterMl), volUnit.symbol)
                    "protein"   -> "%.1f %s".format(foodUnit.fromG(day.proteinG), foodUnit.symbol)
                    "fat"       -> "%.1f %s".format(foodUnit.fromG(day.fatG), foodUnit.symbol)
                    "net_carbs" -> "%.1f %s".format(foodUnit.fromG(day.netCarbsG), foodUnit.symbol)
                    "calories"  -> "%.0f kcal".format(day.calories)
                    "sodium"    -> "%.0f mg".format(day.sodiumMg)
                    "potassium" -> "%.0f mg".format(day.potassiumMg)
                    "magnesium" -> "%.0f mg".format(day.magnesiumMg)
                    "nak_ratio" -> "%.2f:1".format(day.nakRatio ?: 0.0)
                    else        -> "%.1f".format(day.selectMetric(trendsMetric))
                }

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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayValue,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = ketoMetricGraphColor(trendsMetric)
                            )
                            Text(
                                text = day.date,
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant
                            )
                        }
                        Text(
                            text = day.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant
                        )
                    }
                }
            }
        }
        } // end else (non-weight metrics)

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

// ── Keto metric manual entry dialog ──────────────────────────────────────────

/**
 * A metric-specific manual entry dialog for keto non-weight metrics.
 * Shows a date picker and a numeric value field labelled with the correct unit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KetoMetricManualEntryDialog(
    metric: String,
    metricLabel: String,
    unitPrefs: UnitPreferences,
    dateFormatter: DateTimeFormatter,
    onDismiss: () -> Unit,
    onSave: (value: Double, date: String, time: String) -> Unit,
) {
    val unit = when (metric) {
        "weight"    -> unitPrefs.weightUnit.symbol
        "hydration" -> unitPrefs.volumeUnit.symbol
        "protein", "fat", "net_carbs" -> unitPrefs.foodWeightUnit.symbol
        "calories"  -> "kcal"
        "sodium", "potassium", "magnesium" -> "mg"
        else        -> metricUnit(metric)
    }

    var valueInput by remember { mutableStateOf("") }
    var valueError by remember { mutableStateOf(false) }
    var entryDate by remember { mutableStateOf(LocalDate.now().format(dateFormatter)) }
    var entryTime by remember {
        mutableStateOf(
            java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val initMillis = try {
            LocalDate.parse(entryDate, dateFormatter).toEpochDay() * 86_400_000L
        } catch (_: Exception) { System.currentTimeMillis() }
        val dpState = rememberDatePickerState(initialSelectedDateMillis = initMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    dpState.selectedDateMillis?.let { millis ->
                        entryDate = LocalDate.ofEpochDay(millis / 86_400_000L).format(dateFormatter)
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = dpState) }
    }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("Log $metricLabel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Date: $entryDate")
                }
                OutlinedTextField(
                    value = valueInput,
                    onValueChange = {
                        valueInput = it
                        valueError = false
                    },
                    label = { Text("$metricLabel ($unit)") },
                    isError = valueError,
                    supportingText = if (valueError) {
                        { Text("Enter a valid value greater than 0") }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = valueInput.toDoubleOrNull()
                if (v != null && v > 0) {
                    // Convert to canonical storage units
                    val stored = when (metric) {
                        "weight"    -> unitPrefs.weightUnit.toKg(v)
                        "hydration" -> unitPrefs.volumeUnit.toMl(v)
                        "protein", "fat", "net_carbs" -> unitPrefs.foodWeightUnit.toG(v)
                        else        -> v  // calories, sodium, potassium, magnesium stored as-is
                    }
                    onSave(stored, entryDate, entryTime)
                } else {
                    valueError = true
                }
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
