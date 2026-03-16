package com.projectember.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditExerciseScreen(
    viewModel: AddEditExerciseViewModel,
    onNavigateBack: () -> Unit
) {
    val categories by viewModel.categories.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var showManageCategoriesDialog by remember { mutableStateOf(false) }

    // ── Manage categories dialog ──────────────────────────────────────────────
    if (showManageCategoriesDialog) {
        var actionMessage by remember { mutableStateOf<String?>(null) }
        var isActionError by remember { mutableStateOf(true) }
        // Track which category is currently being renamed (id → current draft name)
        var renamingCategoryId by remember { mutableStateOf<Int?>(null) }
        var renameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                showManageCategoriesDialog = false
                actionMessage = null
                renamingCategoryId = null
                renameInput = ""
            },
            title = { Text("Manage Categories") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    actionMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isActionError) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary
                        )
                    }
                    val customCategories = categories.filter { !it.isBuiltIn }
                    if (customCategories.isEmpty()) {
                        Text(
                            text = "No custom categories yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        customCategories.forEach { cat ->
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = cat.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = {
                                            renamingCategoryId = if (renamingCategoryId == cat.id) null else cat.id
                                            renameInput = cat.name
                                            actionMessage = null
                                        }
                                    ) { Text("Rename") }
                                    TextButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                when (val result = viewModel.deleteCategory(cat.id)) {
                                                    is AddEditExerciseViewModel.DeleteCategoryResult.Deleted -> {
                                                        actionMessage = null
                                                        if (renamingCategoryId == cat.id) {
                                                            renamingCategoryId = null
                                                            renameInput = ""
                                                        }
                                                    }
                                                    is AddEditExerciseViewModel.DeleteCategoryResult.BuiltInProtected -> {
                                                        isActionError = true
                                                        actionMessage = "\"${cat.name}\" is a built-in category and cannot be deleted."
                                                    }
                                                    is AddEditExerciseViewModel.DeleteCategoryResult.InUse -> {
                                                        isActionError = true
                                                        actionMessage = "\"${cat.name}\" is used by ${result.entryCount} " +
                                                            "exercise ${if (result.entryCount == 1) "entry" else "entries"} and cannot be deleted."
                                                    }
                                                }
                                            }
                                        }
                                    ) {
                                        Text("Delete", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                                // Inline rename field — only shown for the category being renamed
                                if (renamingCategoryId == cat.id) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = renameInput,
                                            onValueChange = { renameInput = it },
                                            label = { Text("New name") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = {
                                            coroutineScope.launch {
                                                val newName = renameInput.trim()
                                                val error = viewModel.renameCategory(cat.id, renameInput)
                                                if (error != null) {
                                                    isActionError = true
                                                    actionMessage = error
                                                } else {
                                                    isActionError = false
                                                    actionMessage = "\"${cat.name}\" renamed to \"$newName\"."
                                                    renamingCategoryId = null
                                                    renameInput = ""
                                                }
                                            }
                                        }) { Text("Save") }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showManageCategoriesDialog = false
                    actionMessage = null
                    renamingCategoryId = null
                    renameInput = ""
                }) { Text("Done") }
            }
        )
    }

    // ── Delete confirmation ───────────────────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Entry") },
            text = { Text("Delete this exercise entry? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteEntry(onSuccess = onNavigateBack)
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
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

    // ── New category dialog ───────────────────────────────────────────────────
    if (showNewCategoryDialog) {
        var newCategoryName by remember { mutableStateOf("") }
        var newCategoryError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = {
                showNewCategoryDialog = false
                newCategoryName = ""
                newCategoryError = null
            },
            title = { Text("New Category") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = {
                            newCategoryName = it
                            newCategoryError = null
                        },
                        label = { Text("Category name") },
                        singleLine = true,
                        isError = newCategoryError != null,
                        supportingText = newCategoryError?.let { err ->
                            { Text(err, color = MaterialTheme.colorScheme.error) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = newCategoryName.trim()
                    if (trimmed.isBlank()) {
                        newCategoryError = "Category name cannot be empty"
                        return@TextButton
                    }
                    coroutineScope.launch {
                        val error = viewModel.createCategoryValidated(trimmed)
                        if (error != null) {
                            newCategoryError = error
                        } else {
                            showNewCategoryDialog = false
                            newCategoryName = ""
                        }
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNewCategoryDialog = false
                    newCategoryName = ""
                    newCategoryError = null
                }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (viewModel.isEditMode) "Edit Exercise" else "Log Exercise")
                },
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
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ── Category picker ───────────────────────────────────────────────
            SectionLabel("Category *")
            Text(
                text = "How to group this activity in your log",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = viewModel.selectedCategoryId == cat.id
                    AssistChip(
                        onClick = { viewModel.onCategorySelected(cat.id) },
                        label = { Text(cat.name) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (isSelected) KetoAccent
                                else MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                // "+ New Category" chip
                AssistChip(
                    onClick = { showNewCategoryDialog = true },
                    label = { Text("+ New") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = KetoAccent
                    )
                )
                // "Manage" chip — for deleting custom categories
                if (categories.any { !it.isBuiltIn }) {
                    AssistChip(
                        onClick = { showManageCategoriesDialog = true },
                        label = { Text("Manage") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            viewModel.categoryError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── Activity type ─────────────────────────────────────────────────
            SectionLabel("Activity Type *")
            Text(
                text = "The specific exercise performed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AddEditExerciseViewModel.EXERCISE_TYPES.forEach { t ->
                    val isSelected = viewModel.type == t
                    AssistChip(
                        onClick = { viewModel.onTypeSelected(t) },
                        label = { Text(t) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (isSelected) KetoAccent
                                else MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            viewModel.typeError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // ── Subtype (conditional) ─────────────────────────────────────────
            val subtypes = AddEditExerciseViewModel.EXERCISE_TYPES_MAP[viewModel.type]
            if (!subtypes.isNullOrEmpty()) {
                SectionLabel("Subtype (optional)")

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // "None" chip — always shown first so subtype can be explicitly cleared
                    val noneSelected = viewModel.subtype.isBlank()
                    AssistChip(
                        onClick = { viewModel.clearSubtype() },
                        label = { Text("None") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (noneSelected) KetoAccent
                                else MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = if (noneSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    subtypes.forEach { st ->
                        val isSelected = viewModel.subtype == st
                        AssistChip(
                            onClick = { viewModel.onSubtypeSelected(st) },
                            label = { Text(st) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (isSelected) KetoAccent
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── Date and Time ─────────────────────────────────────────────────
            SectionLabel("Date & Time")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Date", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text("Time", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            // ── Optional metrics ──────────────────────────────────────────────
            SectionLabel("Details (optional)")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = viewModel.durationMinutes,
                    onValueChange = viewModel::onDurationChange,
                    label = { Text("Duration (min)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = viewModel.caloriesBurned,
                    onValueChange = viewModel::onCaloriesBurnedChange,
                    label = { Text("Calories burned") },
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

            Spacer(modifier = Modifier.height(8.dp))

            // ── Save button ───────────────────────────────────────────────────
            Button(
                onClick = {
                    viewModel.save(
                        onSuccess = onNavigateBack,
                        onValidationFailed = {
                            coroutineScope.launch { scrollState.animateScrollTo(0) }
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (viewModel.isEditMode) "Save Changes" else "Save Exercise")
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

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
