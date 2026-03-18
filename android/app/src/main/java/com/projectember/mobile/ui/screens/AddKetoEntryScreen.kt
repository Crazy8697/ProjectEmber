package com.projectember.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.projectember.mobile.ui.theme.KetoAccent
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val EVENT_TYPES = AddKetoEntryViewModel.EVENT_TYPES

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddKetoEntryScreen(
    viewModel: AddKetoEntryViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit = {},
    onNavigateToRecipes: () -> Unit = {}
) {
    val unitPrefs by viewModel.unitPreferences.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Entry") },
            text = {
                Text(
                    "Are you sure you want to delete \"${viewModel.label}\"? " +
                        "This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteEntry(onSuccess = onNavigateBack)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Date picker ───────────────────────────────────────────────────────────
    if (showDatePicker) {
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val initialMillis = try {
            LocalDate.parse(viewModel.entryDate, dateFormatter).toEpochDay() * 86_400_000L
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
                        viewModel.onDateChange(date.format(dateFormatter))
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

    // ── Time picker ───────────────────────────────────────────────────────────
    if (showTimePicker) {
        val timeParts = viewModel.entryTime.split(":").mapNotNull { it.toIntOrNull() }
        val tpState = rememberTimePickerState(
            initialHour = timeParts.getOrNull(0) ?: 0,
            initialMinute = timeParts.getOrNull(1) ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Time") },
            text = { TimePicker(state = tpState) },
            confirmButton = {
                TextButton(onClick = {
                    showTimePicker = false
                    viewModel.onTimeChange("%02d:%02d".format(tpState.hour, tpState.minute))
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditMode) "Edit Entry" else "Add Entry") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Home") },
                                leadingIcon = {
                                    Icon(Icons.Default.Home, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    onNavigateToHome()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Recipe Book") },
                                leadingIcon = {
                                    Icon(Icons.Default.MenuBook, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    onNavigateToRecipes()
                                }
                            )
                            if (viewModel.canSaveToRecipeBook) {
                                DropdownMenuItem(
                                    text = { Text("Save to Recipe Book") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Bookmark, contentDescription = null)
                                    },
                                    onClick = {
                                        showMenu = false
                                        viewModel.saveToRecipeBook(
                                            onSuccess = { name ->
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("\"$name\" saved to Recipe Book")
                                                }
                                            },
                                            onError = {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Could not save to Recipe Book")
                                                }
                                            }
                                        )
                                    }
                                )
                            }
                            if (viewModel.isEditMode) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Delete Entry",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }
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

            // ── Label ────────────────────────────────────────────────────────
            OutlinedTextField(
                value = viewModel.label,
                onValueChange = viewModel::onLabelChange,
                label = { Text("Label *") },
                isError = viewModel.labelError != null,
                supportingText = {
                    viewModel.labelError?.let { Text(it) }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // ── Event type ───────────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Event Type *",
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    EVENT_TYPES.forEach { type ->
                        val isSelected = viewModel.eventType == type
                        AssistChip(
                            onClick = { viewModel.onEventTypeChange(type) },
                            label = { Text(type) },
                            modifier = Modifier.wrapContentWidth(),
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (isSelected) KetoAccent
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
                viewModel.eventTypeError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // ── Date & Time ──────────────────────────────────────────────────
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = "Date & Time",
                style = MaterialTheme.typography.labelLarge
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Date",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            viewModel.entryDate,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = KetoAccent
                        )
                    }
                }
                TextButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Time",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            viewModel.entryTime,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = KetoAccent
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── Type-adaptive fields ─────────────────────────────────────────
            if (viewModel.isRecipeDerived) {
                RecipeDerivedFields(viewModel, unitPrefs)
            } else {
                when (viewModel.eventType) {
                    "exercise"   -> ExerciseFields(viewModel)
                    "supplement" -> SupplementFields(viewModel)
                    else -> if (viewModel.eventType.isNotBlank()) NutritionFields(viewModel, unitPrefs)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.save(onSuccess = onNavigateBack) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (viewModel.isEditMode) "Save Changes" else "Save Entry")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ── Nutrition fields (Meal / Drink / Snack) ──────────────────────────────────
@Composable
private fun NutritionFields(
    viewModel: AddKetoEntryViewModel,
    unitPrefs: com.projectember.mobile.data.local.UnitPreferences
) {
    val foodSym = unitPrefs.foodWeightUnit.symbol
    val volSym  = unitPrefs.volumeUnit.symbol

    // ── Macros ───────────────────────────────────────────────────────────────
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Nutrition",
            style = MaterialTheme.typography.labelLarge
        )
        OutlinedTextField(
            value = viewModel.servings,
            onValueChange = viewModel::onServingsChange,
            label = { Text("Servings") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(120.dp),
            singleLine = true
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = viewModel.calories,
            onValueChange = viewModel::onCaloriesChange,
            label = { Text("Calories (kcal)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        OutlinedTextField(
            value = viewModel.proteinG,
            onValueChange = viewModel::onProteinGChange,
            label = { Text("Protein ($foodSym)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
            singleLine = true
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = viewModel.fatG,
            onValueChange = viewModel::onFatGChange,
            label = { Text("Fat ($foodSym)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        OutlinedTextField(
            value = viewModel.netCarbsG,
            onValueChange = viewModel::onNetCarbsGChange,
            label = { Text("Net Carbs ($foodSym)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
            singleLine = true
        )
    }

    // ── Minerals & Hydration ─────────────────────────────────────────────────
    Text(
        text = "Minerals & Hydration",
        style = MaterialTheme.typography.labelLarge
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = viewModel.waterMl,
            onValueChange = viewModel::onWaterMlChange,
            label = { Text("Water ($volSym)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        OutlinedTextField(
            value = viewModel.sodiumMg,
            onValueChange = viewModel::onSodiumMgChange,
            label = { Text("Sodium (mg)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
            singleLine = true
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = viewModel.potassiumMg,
            onValueChange = viewModel::onPotassiumMgChange,
            label = { Text("Potassium (mg)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        OutlinedTextField(
            value = viewModel.magnesiumMg,
            onValueChange = viewModel::onMagnesiumMgChange,
            label = { Text("Magnesium (mg)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
            singleLine = true
        )
    }

    OutlinedTextField(
        value = viewModel.notes,
        onValueChange = viewModel::onNotesChange,
        label = { Text("Notes (optional)") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 4
    )
}

// ── Exercise fields ──────────────────────────────────────────────────────────
@Composable
private fun ExerciseFields(viewModel: AddKetoEntryViewModel) {
    OutlinedTextField(
        value = viewModel.calories,
        onValueChange = viewModel::onCaloriesChange,
        label = { Text("Calories burned (kcal)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    OutlinedTextField(
        value = viewModel.distanceKm,
        onValueChange = viewModel::onDistanceKmChange,
        label = { Text("Distance (km, optional)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    OutlinedTextField(
        value = viewModel.notes,
        onValueChange = viewModel::onNotesChange,
        label = { Text("Notes (optional)") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 4
    )
    // Optional advanced fields
    OutlinedTextField(
        value = viewModel.activitySubtype,
        onValueChange = viewModel::onActivitySubtypeChange,
        label = { Text("Activity type (optional, e.g. run, cycle, swim)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    OutlinedTextField(
        value = viewModel.pace,
        onValueChange = viewModel::onPaceChange,
        label = { Text("Pace (optional, e.g. 5:30 /km)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    OutlinedTextField(
        value = viewModel.steps,
        onValueChange = viewModel::onStepsChange,
        label = { Text("Steps (optional)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

// ── Supplement fields ────────────────────────────────────────────────────────
@Composable
private fun SupplementFields(viewModel: AddKetoEntryViewModel) {
    OutlinedTextField(
        value = viewModel.notes,
        onValueChange = viewModel::onNotesChange,
        label = { Text("Notes (e.g. dosage, brand, purpose)") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 4
    )
    OutlinedTextField(
        value = viewModel.sodiumMg,
        onValueChange = viewModel::onSodiumMgChange,
        label = { Text("Sodium (mg, optional)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    OutlinedTextField(
        value = viewModel.potassiumMg,
        onValueChange = viewModel::onPotassiumMgChange,
        label = { Text("Potassium (mg, optional)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    OutlinedTextField(
        value = viewModel.magnesiumMg,
        onValueChange = viewModel::onMagnesiumMgChange,
        label = { Text("Magnesium (mg, optional)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

// ── Recipe-derived fields (servings-only edit + read-only nutrition) ─────────
@Composable
private fun RecipeDerivedFields(
    viewModel: AddKetoEntryViewModel,
    unitPrefs: com.projectember.mobile.data.local.UnitPreferences
) {
    val foodSym = unitPrefs.foodWeightUnit.symbol
    val volSym  = unitPrefs.volumeUnit.symbol

    // Servings consumed — the only editable field for recipe-derived entries
    OutlinedTextField(
        value = viewModel.servings,
        onValueChange = viewModel::onServingsChange,
        label = { Text("Servings consumed") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Read-only per-serving snapshot
    Text(
        text = "Nutrition per serving (from recipe)",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    val perServingCalories = viewModel.calories.toDoubleOrNull() ?: 0.0
    val perServingProtein  = viewModel.proteinG.toDoubleOrNull() ?: 0.0
    val perServingFat      = viewModel.fatG.toDoubleOrNull() ?: 0.0
    val perServingCarbs    = viewModel.netCarbsG.toDoubleOrNull() ?: 0.0
    val consumed           = viewModel.servings.toDoubleOrNull()?.coerceAtLeast(0.1) ?: 1.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ReadOnlyRow("Calories",  "%.0f kcal".format(perServingCalories), "%.0f kcal".format(perServingCalories * consumed))
            ReadOnlyRow("Protein",   "%.1f $foodSym".format(perServingProtein),  "%.1f $foodSym".format(perServingProtein  * consumed))
            ReadOnlyRow("Fat",       "%.1f $foodSym".format(perServingFat),      "%.1f $foodSym".format(perServingFat      * consumed))
            ReadOnlyRow("Net Carbs", "%.1f $foodSym".format(perServingCarbs),    "%.1f $foodSym".format(perServingCarbs    * consumed))

            val hasMinerals = (viewModel.waterMl.toDoubleOrNull() ?: 0.0) > 0 ||
                (viewModel.sodiumMg.toDoubleOrNull() ?: 0.0) > 0 ||
                (viewModel.potassiumMg.toDoubleOrNull() ?: 0.0) > 0 ||
                (viewModel.magnesiumMg.toDoubleOrNull() ?: 0.0) > 0
            if (hasMinerals) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                val perSodium     = viewModel.sodiumMg.toDoubleOrNull()     ?: 0.0
                val perPotassium  = viewModel.potassiumMg.toDoubleOrNull()  ?: 0.0
                val perMagnesium  = viewModel.magnesiumMg.toDoubleOrNull()  ?: 0.0
                val perWater      = viewModel.waterMl.toDoubleOrNull()      ?: 0.0
                if (perSodium    > 0) ReadOnlyRow("Sodium",    "%.0f mg".format(perSodium),        "%.0f mg".format(perSodium    * consumed))
                if (perPotassium > 0) ReadOnlyRow("Potassium", "%.0f mg".format(perPotassium),     "%.0f mg".format(perPotassium * consumed))
                if (perMagnesium > 0) ReadOnlyRow("Magnesium", "%.0f mg".format(perMagnesium),     "%.0f mg".format(perMagnesium * consumed))
                if (perWater     > 0) ReadOnlyRow("Water",     "%.1f $volSym".format(perWater),    "%.1f $volSym".format(perWater * consumed))
            }
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    // Notes still editable
    OutlinedTextField(
        value = viewModel.notes,
        onValueChange = viewModel::onNotesChange,
        label = { Text("Notes (optional)") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 4
    )
}

@Composable
private fun ReadOnlyRow(label: String, perServing: String, total: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1.2f)
        )
        Text(
            text = perServing,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = total,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
    }
}

