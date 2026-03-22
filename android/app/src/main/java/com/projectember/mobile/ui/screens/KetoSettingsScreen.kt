package com.projectember.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.projectember.mobile.data.local.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KetoSettingsScreen(
    onNavigateBack: () -> Unit,
    vm: KetoSettingsViewModel? = null
) {
    val app = LocalContext.current.applicationContext as com.projectember.mobile.EmberApplication
    val viewModel: KetoSettingsViewModel = vm ?: viewModel(
        factory = KetoSettingsViewModel.factory(
            app.ketoTargetsStore,
            app.calorieAllocationStore,
            app.mealTimingStore,
            app.dailyRhythmStore,
            app.recipeCategoryStore
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

            // JSON Import section (links to existing JsonImportScreen)
            Text("JSON Import", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Advanced: import/export keto settings and other data. Normal logging happens on the tracker screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { /* navigate to JsonImport - Nav handled by parent */ }) { Text("Import JSON") }
                OutlinedButton(onClick = { /* TODO: paste JSON dialog */ }) { Text("Paste JSON") }
            }

            // Daily Structure / Calorie Allocation
            Text("Daily Structure / Calorie Allocation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Allocate your daily calories across meals. Total must equal 100%.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            AllocationRow(label = "Breakfast", value = allocation.breakfastPct) { newPct ->
                viewModel.updateAllocation(allocation.copy(breakfastPct = newPct))
            }
            AllocationRow(label = "Lunch", value = allocation.lunchPct) { newPct ->
                viewModel.updateAllocation(allocation.copy(lunchPct = newPct))
            }
            AllocationRow(label = "Dinner", value = allocation.dinnerPct) { newPct ->
                viewModel.updateAllocation(allocation.copy(dinnerPct = newPct))
            }
            AllocationRow(label = "Snack", value = allocation.snackPct) { newPct ->
                viewModel.updateAllocation(allocation.copy(snackPct = newPct))
            }

            Text("Total: ${allocation.totalPct()}%", style = MaterialTheme.typography.bodyMedium)
            val perMeal = allocation.perMealCalories(targets.caloriesKcal)
            Text("Preview: Breakfast ${perMeal["breakfast"]?.toInt()} kcal • Lunch ${perMeal["lunch"]?.toInt()} kcal • Dinner ${perMeal["dinner"]?.toInt()} kcal • Snack ${perMeal["snack"]?.toInt()} kcal", style = MaterialTheme.typography.bodySmall)

            val allocationValid = allocation.totalPct() == 100 &&
                (mealTiming.breakfastWindow != null || allocation.breakfastPct == 0) &&
                (mealTiming.lunchWindow != null || allocation.lunchPct == 0) &&
                (mealTiming.dinnerWindow != null || allocation.dinnerPct == 0)

            Button(onClick = { viewModel.saveAllocationIfValid() }, enabled = allocationValid, modifier = Modifier.fillMaxWidth()) {
                Text("Save Allocation")
            }

            // Daily Targets (dense layout)
            Text("Daily Targets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            TargetsGrid(targets = targets) { newTargets -> viewModel.saveTargets(newTargets) }

            // Meal Timing (Breakfast/Lunch/Dinner only)
            Text("Meal Timing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Optional: configure windows for anchor meals. Disabled meals do not participate in pacing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Reuse existing MealTiming UI patterns from SettingsViewModel where possible
            MealTimingRow("Breakfast", mealTiming.breakfastWindow) { viewModel.setBreakfastWindow(it) }
            MealTimingRow("Lunch", mealTiming.lunchWindow) { viewModel.setLunchWindow(it) }
            MealTimingRow("Dinner", mealTiming.dinnerWindow) { viewModel.setDinnerWindow(it) }

            // Daily Rhythm
            Text("Daily Rhythm", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Wake, sleep, and eating style used as a fallback when meal timing is not configured.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = "Wake ${dailyRhythm.wakeHour}:${dailyRhythm.wakeMinute}", onValueChange = {}, readOnly = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = "Sleep ${dailyRhythm.sleepHour}:${dailyRhythm.sleepMinute}", onValueChange = {}, readOnly = true, modifier = Modifier.weight(1f))
            }

            // Recipe categories management
            Text("Recipe Categories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val recipeCats by viewModel.recipeCategories.collectAsState()
            recipeCats.forEach { cat ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(cat, modifier = Modifier.weight(1f))
                    Row {
                        TextButton(onClick = { /* TODO: rename dialog */ }) { Text("Edit") }
                        TextButton(onClick = { viewModel.deleteRecipeCategory(cat) }) { Text("Delete") }
                    }
                }
            }
            OutlinedTextField(value = "", onValueChange = {}, label = { Text("Add category") }, singleLine = true)
            Button(onClick = { /* TODO: add category action */ }, modifier = Modifier.fillMaxWidth()) { Text("Add Category") }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AllocationRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedTextField(value = value.toString(), onValueChange = { v -> onChange(v.toIntOrNull() ?: 0) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(100.dp), singleLine = true)
    }
}

@Composable
private fun TargetsGrid(targets: KetoTargets, onSave: (KetoTargets) -> Unit) {
    // Two-column dense layout
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = targets.caloriesKcal.toInt().toString(), onValueChange = {}, label = { Text("Calories") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = targets.proteinG.toInt().toString(), onValueChange = {}, label = { Text("Protein (g)") }, modifier = Modifier.weight(1f), singleLine = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = targets.fatG.toInt().toString(), onValueChange = {}, label = { Text("Fat (g)") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = targets.netCarbsG.toInt().toString(), onValueChange = {}, label = { Text("Net Carbs (g)") }, modifier = Modifier.weight(1f), singleLine = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = targets.waterMl.toInt().toString(), onValueChange = {}, label = { Text("Water (ml)") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = targets.sodiumMg.toInt().toString(), onValueChange = {}, label = { Text("Sodium (mg)") }, modifier = Modifier.weight(1f), singleLine = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = targets.potassiumMg.toInt().toString(), onValueChange = {}, label = { Text("Potassium (mg)") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = targets.magnesiumMg.toInt().toString(), onValueChange = {}, label = { Text("Magnesium (mg)") }, modifier = Modifier.weight(1f), singleLine = true)
        }
        Button(onClick = { onSave(targets) }, modifier = Modifier.fillMaxWidth()) { Text("Save Targets") }
    }
}

@Composable
private fun MealTimingRow(label: String, window: MealWindow?, onChange: (MealWindow?) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        if (window == null) {
            Text("Disabled", modifier = Modifier.weight(1f))
            TextButton(onClick = { onChange(MealWindow(8, 0, 9, 0)) }) { Text("Enable") }
        } else {
            Text("${window.startHour}:${window.startMinute} - ${window.endHour}:${window.endMinute}", modifier = Modifier.weight(1f))
            TextButton(onClick = { onChange(null) }) { Text("Disable") }
        }
    }
}

