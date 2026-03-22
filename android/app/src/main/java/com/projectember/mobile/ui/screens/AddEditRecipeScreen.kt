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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.projectember.mobile.ui.theme.KetoAccent
import kotlinx.coroutines.launch

// categories are provided by the ViewModel (may be customized by user)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditRecipeScreen(
    viewModel: AddEditRecipeViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit = {}
) {
    val unitPrefs by viewModel.unitPreferences.collectAsState()
    val foodSym = unitPrefs.foodWeightUnit.symbol
    val volSym  = unitPrefs.volumeUnit.symbol
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Recipe") },
            text = {
                Text(
                    "Are you sure you want to delete \"${viewModel.name}\"? " +
                        "This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteRecipe(onSuccess = onNavigateBack)
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
                title = { Text(if (viewModel.isEditMode) "Edit Recipe" else "Add Recipe") },
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
                            if (viewModel.isEditMode) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Delete Recipe",
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
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ── Name ─────────────────────────────────────────────────────────
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Recipe Name *") },
                isError = viewModel.nameError != null,
                supportingText = {
                    viewModel.nameError?.let { Text(it) }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // ── Category ─────────────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                            val cats by viewModel.categories.collectAsState()
                            cats.forEach { cat ->
                                val isSelected = viewModel.category == cat
                                AssistChip(
                                    onClick = { viewModel.onCategoryChange(cat) },
                                    label = { Text(cat) },
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

            // ── Macros ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total Recipe Nutrition",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "Enter totals for the whole recipe",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                    value = viewModel.totalCarbsG,
                    onValueChange = viewModel::onTotalCarbsGChange,
                    label = { Text("Total Carbs ($foodSym)") },
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
                    value = viewModel.fiberG,
                    onValueChange = viewModel::onFiberGChange,
                    label = { Text("Fiber ($foodSym)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                // Net Carbs is derived; show a read-only hint so the user knows it is computed
                val derivedNetCarbs = run {
                    val tc = viewModel.totalCarbsG.toDoubleOrNull() ?: 0.0
                    val fb = viewModel.fiberG.toDoubleOrNull() ?: 0.0
                    maxOf(0.0, tc - fb)
                }
                OutlinedTextField(
                    value = if (viewModel.totalCarbsG.isNotBlank() || viewModel.fiberG.isNotBlank())
                        "%.1f".format(derivedNetCarbs) else "",
                    onValueChange = {},
                    label = { Text("Net Carbs (g)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    readOnly = true,
                    enabled = false
                )
            }

            // ── Extended Nutrition ───────────────────────────────────────────
            Text(
                text = "Minerals & Hydration (total recipe)",
                style = MaterialTheme.typography.labelLarge
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = viewModel.sodiumMg,
                    onValueChange = viewModel::onSodiumMgChange,
                    label = { Text("Sodium (mg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = viewModel.potassiumMg,
                    onValueChange = viewModel::onPotassiumMgChange,
                    label = { Text("Potassium (mg)") },
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
                    value = viewModel.magnesiumMg,
                    onValueChange = viewModel::onMagnesiumMgChange,
                    label = { Text("Magnesium (mg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = viewModel.waterMl,
                    onValueChange = viewModel::onWaterMlChange,
                    label = { Text("Water ($volSym)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            // ── Notes / Instructions ─────────────────────────────────────────
            OutlinedTextField(
                value = viewModel.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Notes / Instructions (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )

            // ── Ingredients ──────────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Ingredients",
                    style = MaterialTheme.typography.labelLarge
                )

                viewModel.ingredients.forEachIndexed { index, ingredient ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = ingredient.name,
                            onValueChange = { viewModel.updateIngredientName(index, it) },
                            label = { Text("Ingredient") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = ingredient.amount,
                            onValueChange = { viewModel.updateIngredientAmount(index, it) },
                            label = { Text("Amount") },
                            modifier = Modifier.width(100.dp),
                            singleLine = true
                        )
                        IconButton(onClick = { viewModel.removeIngredient(index) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove ingredient",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.addIngredient() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ Add Ingredient")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Save ────────────────────────────────────────────────────────
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
                Text(if (viewModel.isEditMode) "Save Changes" else "Save Recipe")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

