package com.projectember.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectember.mobile.ui.theme.KetoBorder
import com.projectember.mobile.ui.theme.KetoAccent
import com.projectember.mobile.ui.theme.SurfaceMid
import com.projectember.mobile.ui.theme.OnSurface
import com.projectember.mobile.ui.theme.OnSurfaceVariant
import com.projectember.mobile.ui.theme.SuccessGreen
import com.projectember.mobile.ui.theme.WarningYellow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val KetoCard    = SurfaceMid
private val KetoBorderC = KetoBorder
private val KetoMuted   = OnSurfaceVariant

private val METRIC_OPTIONS = listOf(
    Triple("calories",  "C",    "Calories"),
    Triple("protein",   "P",    "Protein"),
    Triple("fat",       "F",    "Fat"),
    Triple("net_carbs", "NC",   "Net Carbs"),
    Triple("hydration", "H2O",  "Water"),
    Triple("sodium",    "Na",   "Sodium"),
    Triple("potassium", "K",    "Potassium"),
    Triple("magnesium", "Mg",   "Magnesium")
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
    else        -> calories.toFloat()
}

private fun metricUnit(metric: String): String = when (metric) {
    "calories"  -> "kcal"
    "hydration" -> "mL"
    "sodium", "potassium", "magnesium" -> "mg"
    else        -> "g"
}

private fun metricBarColor(metric: String): Color = when (metric) {
    "calories"  -> KetoAccent
    "protein"   -> SuccessGreen
    "fat"       -> WarningYellow
    "net_carbs" -> Color(0xFFFF4D4D)
    "hydration" -> KetoAccent
    "sodium"    -> WarningYellow
    "potassium" -> SuccessGreen
    "magnesium" -> Color(0xFF4A8FE8)
    else        -> KetoAccent
}

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
    onNavigateBack: () -> Unit
) {
    val trendsData   by viewModel.trendsData.collectAsState()
    val trendsMetric by viewModel.trendsMetric.collectAsState()
    val fromDate     by viewModel.trendsFromDate.collectAsState()
    val toDate       by viewModel.trendsToDate.collectAsState()
    val mode         by viewModel.trendsMode.collectAsState()
    val rollingDays  by viewModel.trendsRollingDays.collectAsState()
    val targets      by viewModel.targets.collectAsState()

    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker   by remember { mutableStateOf(false) }

    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // Apply initial metric from navigation argument once
    LaunchedEffect(initialMetric) {
        if (initialMetric.isNotBlank()) {
            viewModel.setTrendsMetric(initialMetric)
        }
    }

    // Build chart data
    val selector: (DayTotals) -> Float = { it.selectMetric(trendsMetric) }
    val rawData = trendsData.map { it.label to selector(it) }
    val chartData: List<Pair<String, Float>> = when {
        mode == "rolling" && trendsData.isNotEmpty() ->
            trendsData.rollingAvg(rollingDays, selector)
        else -> rawData
    }
    val targetValue: Float? = when (trendsMetric) {
        "calories"  -> targets.caloriesKcal.toFloat()
        "protein"   -> targets.proteinG.toFloat()
        "fat"       -> targets.fatG.toFloat()
        "net_carbs" -> targets.netCarbsG.toFloat()
        "hydration" -> targets.waterMl.toFloat()
        "sodium"    -> targets.sodiumMg.toFloat()
        "potassium" -> targets.potassiumMg.toFloat()
        "magnesium" -> targets.magnesiumMg.toFloat()
        else        -> null
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trends") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
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
                colors = CardDefaults.cardColors(containerColor = KetoCard),
                border = BorderStroke(1.dp, KetoBorderC)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "METRIC",
                        style = MaterialTheme.typography.labelSmall,
                        color = KetoMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // First row of 4 metrics
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
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(abbr, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(fullName, fontSize = 8.sp, color = if (selected) OnSurface.copy(alpha = 0.8f) else KetoMuted)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = KetoAccent,
                                    selectedLabelColor = OnSurface
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    // Second row of 4 metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        METRIC_OPTIONS.drop(4).forEach { (key, abbr, fullName) ->
                            val selected = trendsMetric == key
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setTrendsMetric(key) },
                                label = {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(abbr, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(fullName, fontSize = 8.sp, color = if (selected) OnSurface.copy(alpha = 0.8f) else KetoMuted)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = KetoAccent,
                                    selectedLabelColor = OnSurface
                                )
                            )
                        }
                    }
                }
            }

            // ── Date range ───────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = KetoCard),
                border = BorderStroke(1.dp, KetoBorderC)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("FROM", style = MaterialTheme.typography.labelSmall, color = KetoMuted)
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
                    Text("—", color = KetoMuted)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TO", style = MaterialTheme.typography.labelSmall, color = KetoMuted)
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
                colors = CardDefaults.cardColors(containerColor = KetoCard),
                border = BorderStroke(1.dp, KetoBorderC)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text("MODE", style = MaterialTheme.typography.labelSmall, color = KetoMuted)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = mode == "daily",
                            onClick = { viewModel.setTrendsMode("daily") },
                            label = { Text("Daily") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = KetoAccent,
                                selectedLabelColor = OnSurface
                            )
                        )
                        FilterChip(
                            selected = mode == "rolling",
                            onClick = { viewModel.setTrendsMode("rolling") },
                            label = { Text("Rolling Avg") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = KetoAccent,
                                selectedLabelColor = OnSurface
                            )
                        )
                    }
                    if (mode == "rolling") {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Window:", style = MaterialTheme.typography.labelSmall, color = KetoMuted)
                            listOf(3, 7, 14).forEach { n ->
                                FilterChip(
                                    selected = rollingDays == n,
                                    onClick = { viewModel.setTrendsRollingDays(n) },
                                    label = { Text("${n}d") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = KetoAccent,
                                        selectedLabelColor = OnSurface
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // ── Chart card ───────────────────────────────────────────────────
            if (chartData.isNotEmpty()) {
                val metricLabel = METRIC_OPTIONS.firstOrNull { it.first == trendsMetric }?.third ?: trendsMetric
                val unit = metricUnit(trendsMetric)
                val barColor = metricBarColor(trendsMetric)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = KetoCard),
                    border = BorderStroke(1.dp, KetoBorderC)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val modeLabel = if (mode == "rolling") " (${rollingDays}d avg)" else ""
                        Text(
                            text = "$metricLabel ($unit)$modeLabel",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = OnSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        WeeklyTrendCard(
                            title = "",
                            data = chartData,
                            barColor = barColor,
                            unit = unit,
                            targetValue = targetValue
                        )
                    }
                }
            } else {
                Text(
                    text = "No data for the selected date range.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = KetoMuted,
                    modifier = Modifier.padding(vertical = 32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
