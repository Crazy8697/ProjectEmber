package com.projectember.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.projectember.mobile.ui.theme.KetoAccent
import com.projectember.mobile.ui.theme.OnSurface

private val EVENT_TYPES = AddKetoEntryViewModel.EVENT_TYPES

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddKetoEntryScreen(
    viewModel: AddKetoEntryViewModel,
    onNavigateBack: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditMode) "Edit Entry" else "Add Entry") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EVENT_TYPES.take(3).forEach { type ->
                        val isSelected = viewModel.eventType == type
                        AssistChip(
                            onClick = { viewModel.onEventTypeChange(type) },
                            label = { Text(type) },
                            modifier = Modifier.wrapContentWidth(),
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (isSelected) KetoAccent else MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = if (isSelected) OnSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EVENT_TYPES.drop(3).forEach { type ->
                        val isSelected = viewModel.eventType == type
                        AssistChip(
                            onClick = { viewModel.onEventTypeChange(type) },
                            label = { Text(type) },
                            modifier = Modifier.wrapContentWidth(),
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (isSelected) KetoAccent else MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = if (isSelected) OnSurface else MaterialTheme.colorScheme.onSurfaceVariant
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

            // ── Type-adaptive fields ─────────────────────────────────────────
            when (viewModel.eventType) {
                "exercise" -> ExerciseFields(viewModel)
                "supplement" -> SupplementFields(viewModel)
                else -> if (viewModel.eventType.isNotBlank()) NutritionFields(viewModel)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.save(onSuccess = onNavigateBack) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (viewModel.isEditMode) "Save Changes" else "Save Entry")
            }

            if (viewModel.isEditMode) {
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete Entry")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ── Nutrition fields (Meal / Drink / Snack) ──────────────────────────────────
@Composable
private fun NutritionFields(viewModel: AddKetoEntryViewModel) {
    OutlinedTextField(
        value = viewModel.calories,
        onValueChange = viewModel::onCaloriesChange,
        label = { Text("Calories (kcal)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    OutlinedTextField(
        value = viewModel.proteinG,
        onValueChange = viewModel::onProteinGChange,
        label = { Text("Protein (g)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    OutlinedTextField(
        value = viewModel.fatG,
        onValueChange = viewModel::onFatGChange,
        label = { Text("Fat (g)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    OutlinedTextField(
        value = viewModel.netCarbsG,
        onValueChange = viewModel::onNetCarbsGChange,
        label = { Text("Net Carbs (g)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    OutlinedTextField(
        value = viewModel.waterMl,
        onValueChange = viewModel::onWaterMlChange,
        label = { Text("Water (mL)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    OutlinedTextField(
        value = viewModel.sodiumMg,
        onValueChange = viewModel::onSodiumMgChange,
        label = { Text("Sodium (mg)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    OutlinedTextField(
        value = viewModel.potassiumMg,
        onValueChange = viewModel::onPotassiumMgChange,
        label = { Text("Potassium (mg)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    OutlinedTextField(
        value = viewModel.magnesiumMg,
        onValueChange = viewModel::onMagnesiumMgChange,
        label = { Text("Magnesium (mg)") },
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
