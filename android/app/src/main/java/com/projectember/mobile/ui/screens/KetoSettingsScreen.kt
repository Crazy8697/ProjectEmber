package com.projectember.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.projectember.mobile.data.local.*
import androidx.compose.foundation.text.KeyboardOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KetoSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToJsonImport: (String?) -> Unit = {},
    vm: KetoSettingsViewModel? = null
) {
    val app = LocalContext.current.applicationContext as com.projectember.mobile.EmberApplication
    val viewModel: KetoSettingsViewModel = vm ?: viewModel(
        factory = KetoSettingsViewModel.factory(
            app.ketoTargetsStore,
            app.calorieAllocationStore,
            app.mealTimingStore,
            app.dailyRhythmStore,
        )
    )

    val allocation by viewModel.allocation.collectAsState()
    val targets = viewModel.targets
    val mealTiming by viewModel.mealTiming.collectAsState()
    val dailyRhythm by viewModel.dailyRhythm.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Keto Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
            Spacer(modifier = Modifier.height(8.dp))

            SettingsCardSection(title = "JSON Import") {
                Text(
                    text = "Import keto JSON data for this domain.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onNavigateToJsonImport("keto") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Open JSON Import") }
            }

            SettingsCardSection(title = "Daily Structure / Calorie Allocation") {
                Text(
                    text = "Allocate daily calories across meals. Save is enabled only when total is exactly 100%.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                AllocationSliderRow(
                    label = "Breakfast",
                    value = allocation.breakfastPct,
                    enabled = mealTiming.breakfastWindow != null,
                    onChange = { pct -> viewModel.updateAllocation(allocation.copy(breakfastPct = pct)) }
                )
                AllocationSliderRow(
                    label = "Lunch",
                    value = allocation.lunchPct,
                    enabled = mealTiming.lunchWindow != null,
                    onChange = { pct -> viewModel.updateAllocation(allocation.copy(lunchPct = pct)) }
                )
                AllocationSliderRow(
                    label = "Dinner",
                    value = allocation.dinnerPct,
                    enabled = mealTiming.dinnerWindow != null,
                    onChange = { pct -> viewModel.updateAllocation(allocation.copy(dinnerPct = pct)) }
                )
                AllocationSliderRow(
                    label = "Snack",
                    value = allocation.snackPct,
                    enabled = true,
                    onChange = { pct -> viewModel.updateAllocation(allocation.copy(snackPct = pct)) }
                )

                val total = allocation.totalPct()
                val totalColor = if (total == 100) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
                Text(
                    text = "Total: $total%",
                    style = MaterialTheme.typography.titleSmall,
                    color = totalColor,
                    fontWeight = FontWeight.SemiBold
                )

                val perMeal = allocation.perMealCalories(targets.caloriesKcal)
                Text(
                    text = "Preview (${targets.caloriesKcal.toInt()} kcal): " +
                        "Breakfast ${perMeal["breakfast"]?.toInt() ?: 0} kcal • " +
                        "Lunch ${perMeal["lunch"]?.toInt() ?: 0} kcal • " +
                        "Dinner ${perMeal["dinner"]?.toInt() ?: 0} kcal • " +
                        "Snack ${perMeal["snack"]?.toInt() ?: 0} kcal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = { viewModel.saveAllocationIfValid() },
                    enabled = total == 100,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Allocation")
                }
            }

            SettingsCardSection(title = "Daily Targets") {
                TargetsGrid(targets = targets) { newTargets -> viewModel.saveTargets(newTargets) }
            }

            SettingsCardSection(title = "Meal Timing") {
                Text(
                    text = "Breakfast, lunch, and dinner only. Disabled meals are excluded from pacing checkpoints.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                MealTimingEditorRow(
                    mealName = "Breakfast",
                    window = mealTiming.breakfastWindow,
                    defaultStartHour = 7,
                    defaultEndHour = 9,
                    onWindowChange = viewModel::setBreakfastWindow
                )
                HorizontalDivider()
                MealTimingEditorRow(
                    mealName = "Lunch",
                    window = mealTiming.lunchWindow,
                    defaultStartHour = 12,
                    defaultEndHour = 13,
                    onWindowChange = viewModel::setLunchWindow
                )
                HorizontalDivider()
                MealTimingEditorRow(
                    mealName = "Dinner",
                    window = mealTiming.dinnerWindow,
                    defaultStartHour = 18,
                    defaultEndHour = 20,
                    onWindowChange = viewModel::setDinnerWindow
                )
            }

            SettingsCardSection(title = "Daily Rhythm") {
                Text(
                    text = "Wake/sleep and eating style are used when meal timing is not configured.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                var showWakePicker by remember { mutableStateOf(false) }
                var showSleepPicker by remember { mutableStateOf(false) }

                TimeValueRow(
                    label = "Wake time",
                    value = formatTime(dailyRhythm.wakeHour, dailyRhythm.wakeMinute),
                    onClick = { showWakePicker = true }
                )
                TimeValueRow(
                    label = "Sleep time",
                    value = formatTime(dailyRhythm.sleepHour, dailyRhythm.sleepMinute),
                    onClick = { showSleepPicker = true }
                )

                if (showWakePicker) {
                    val tpState = rememberTimePickerState(
                        initialHour = dailyRhythm.wakeHour,
                        initialMinute = dailyRhythm.wakeMinute,
                        is24Hour = true
                    )
                    AlertDialog(
                        onDismissRequest = { showWakePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                showWakePicker = false
                                viewModel.setWakeTime(tpState.hour, tpState.minute)
                            }) { Text("OK") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showWakePicker = false }) { Text("Cancel") }
                        },
                        text = { TimePicker(state = tpState) }
                    )
                }

                if (showSleepPicker) {
                    val tpState = rememberTimePickerState(
                        initialHour = dailyRhythm.sleepHour,
                        initialMinute = dailyRhythm.sleepMinute,
                        is24Hour = true
                    )
                    AlertDialog(
                        onDismissRequest = { showSleepPicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                showSleepPicker = false
                                viewModel.setSleepTime(tpState.hour, tpState.minute)
                            }) { Text("OK") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSleepPicker = false }) { Text("Cancel") }
                        },
                        text = { TimePicker(state = tpState) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Eating Style", style = MaterialTheme.typography.labelMedium)
                EatingStyle.entries.forEach { style ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(style.displayName, modifier = Modifier.padding(vertical = 8.dp))
                        RadioButton(
                            selected = dailyRhythm.eatingStyle == style,
                            onClick = { viewModel.setEatingStyle(style) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsCardSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun AllocationSliderRow(
    label: String,
    value: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(if (enabled) "$value%" else "0% (disabled)")
        }
        Slider(
            value = if (enabled) value.toFloat() else 0f,
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..100f,
            steps = 99,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TargetsGrid(targets: KetoTargets, onSave: (KetoTargets) -> Unit) {
    var calories by rememberSaveable(targets) { mutableStateOf(targets.caloriesKcal.toInt().toString()) }
    var protein by rememberSaveable(targets) { mutableStateOf(targets.proteinG.toInt().toString()) }
    var fat by rememberSaveable(targets) { mutableStateOf(targets.fatG.toInt().toString()) }
    var netCarbs by rememberSaveable(targets) { mutableStateOf(targets.netCarbsG.toInt().toString()) }
    var water by rememberSaveable(targets) { mutableStateOf(targets.waterMl.toInt().toString()) }
    var sodium by rememberSaveable(targets) { mutableStateOf(targets.sodiumMg.toInt().toString()) }
    var potassium by rememberSaveable(targets) { mutableStateOf(targets.potassiumMg.toInt().toString()) }
    var magnesium by rememberSaveable(targets) { mutableStateOf(targets.magnesiumMg.toInt().toString()) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = calories, onValueChange = { calories = it }, label = { Text("Calories") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = protein, onValueChange = { protein = it }, label = { Text("Protein (g)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = fat, onValueChange = { fat = it }, label = { Text("Fat (g)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = netCarbs, onValueChange = { netCarbs = it }, label = { Text("Net Carbs (g)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = water, onValueChange = { water = it }, label = { Text("Water (ml)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = sodium, onValueChange = { sodium = it }, label = { Text("Sodium (mg)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = potassium, onValueChange = { potassium = it }, label = { Text("Potassium (mg)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = magnesium, onValueChange = { magnesium = it }, label = { Text("Magnesium (mg)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
        }

        Button(
            onClick = {
                onSave(
                    targets.copy(
                        caloriesKcal = calories.toDoubleOrNull() ?: targets.caloriesKcal,
                        proteinG = protein.toDoubleOrNull() ?: targets.proteinG,
                        fatG = fat.toDoubleOrNull() ?: targets.fatG,
                        netCarbsG = netCarbs.toDoubleOrNull() ?: targets.netCarbsG,
                        waterMl = water.toDoubleOrNull() ?: targets.waterMl,
                        sodiumMg = sodium.toDoubleOrNull() ?: targets.sodiumMg,
                        potassiumMg = potassium.toDoubleOrNull() ?: targets.potassiumMg,
                        magnesiumMg = magnesium.toDoubleOrNull() ?: targets.magnesiumMg
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save Targets") }
    }
}

@Composable
private fun TimeValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        TextButton(onClick = onClick) { Text(value) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealTimingEditorRow(
    mealName: String,
    window: MealWindow?,
    defaultStartHour: Int,
    defaultEndHour: Int,
    onWindowChange: (MealWindow?) -> Unit,
) {
    val isEnabled = window != null
    val startHour = window?.startHour ?: defaultStartHour
    val startMinute = window?.startMinute ?: 0
    val endHour = window?.endHour ?: defaultEndHour
    val endMinute = window?.endMinute ?: 0

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(mealName)
            Switch(
                checked = isEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        onWindowChange(MealWindow(startHour, startMinute, endHour, endMinute))
                    } else {
                        onWindowChange(null)
                    }
                }
            )
        }
        if (isEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showStartPicker = true }) {
                    Text(formatTime(startHour, startMinute))
                }
                Text("-")
                TextButton(onClick = { showEndPicker = true }) {
                    Text(formatTime(endHour, endMinute))
                }
            }
        }
    }

    if (showStartPicker) {
        val tpState = rememberTimePickerState(
            initialHour = startHour,
            initialMinute = startMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showStartPicker = false
                    onWindowChange(MealWindow(tpState.hour, tpState.minute, endHour, endMinute))
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = tpState) }
        )
    }

    if (showEndPicker) {
        val tpState = rememberTimePickerState(
            initialHour = endHour,
            initialMinute = endMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showEndPicker = false
                    onWindowChange(MealWindow(startHour, startMinute, tpState.hour, tpState.minute))
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = tpState) }
        )
    }
}

private fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

