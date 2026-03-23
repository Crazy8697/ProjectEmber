package com.projectember.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeNerdModeScreen(
    viewModel: RecipeNerdModeViewModel,
    onNavigateBack: () -> Unit
) {
    val clearRecipesState by viewModel.clearRecipesState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var newCategoryName by remember { mutableStateOf("") }
    var categoryToEdit by remember { mutableStateOf<String?>(null) }
    var editCategoryName by remember { mutableStateOf("") }
    var categoryToDelete by remember { mutableStateOf<String?>(null) }
    var showClearRecipesConfirm1 by remember { mutableStateOf(false) }
    var showClearRecipesConfirm2 by remember { mutableStateOf(false) }

    // ── Clear recipes feedback ────────────────────────────────────────────────
    LaunchedEffect(clearRecipesState) {
        when (val s = clearRecipesState) {
            is NerdModeOpState.Success -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.clearClearRecipesState()
            }
            is NerdModeOpState.Error -> {
                snackbarHostState.showSnackbar("Clear failed: ${s.message}")
                viewModel.clearClearRecipesState()
            }
            else -> Unit
        }
    }

    val isBusy = clearRecipesState is NerdModeOpState.InProgress

    if (categoryToEdit != null) {
        AlertDialog(
            onDismissRequest = { categoryToEdit = null },
            title = { Text("Edit Category") },
            text = {
                OutlinedTextField(
                    value = editCategoryName,
                    onValueChange = { editCategoryName = it },
                    label = { Text("Category name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val oldName = categoryToEdit ?: return@TextButton
                    if (editCategoryName.isNotBlank()) {
                        viewModel.renameCategory(oldName, editCategoryName)
                        categoryToEdit = null
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { categoryToEdit = null }) { Text("Cancel") }
            }
        )
    }

    if (categoryToDelete != null) {
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Delete Category?") },
            text = {
                Text("This deletes the category \"${categoryToDelete ?: ""}\" from category management.")
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = categoryToDelete ?: return@TextButton
                    viewModel.deleteCategory(name)
                    categoryToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (showClearRecipesConfirm1) {
        AlertDialog(
            onDismissRequest = { showClearRecipesConfirm1 = false },
            title = { Text("Warning: Delete Recipe Book?") },
            text = {
                Text(
                    "This will erase your recipe book.\n\n" +
                        "What will be erased:\n- All saved recipes in the recipe library.\n\n" +
                        "What will NOT be erased:\n- Keto log entries and other app settings.\n\n" +
                        "This is destructive and cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearRecipesConfirm1 = false
                    showClearRecipesConfirm2 = true
                }) { Text("Continue", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearRecipesConfirm1 = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearRecipesConfirm2) {
        AlertDialog(
            onDismissRequest = { showClearRecipesConfirm2 = false },
            title = { Text("Final confirmation") },
            text = { Text("Delete all recipes from the recipe book now?") },
            confirmButton = {
                TextButton(onClick = {
                    showClearRecipesConfirm2 = false
                    viewModel.clearRecipes()
                }) { Text("Delete Recipe Book", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearRecipesConfirm2 = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Recipe Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
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
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ── Info banner ───────────────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Manage recipe categories and recipe cleanup actions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }

            // ── Category management ──────────────────────────────────────────
            NerdModeSection(title = "Category Management") {
                Text(
                    text = "Create, edit, and delete categories used by recipe add/edit screens.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                categories.forEach { category ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(category, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            categoryToEdit = category
                            editCategoryName = category
                        }) { Text("Edit") }
                        TextButton(onClick = { categoryToDelete = category }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    HorizontalDivider()
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("New category") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        viewModel.addCategory(newCategoryName)
                        newCategoryName = ""
                    },
                    enabled = newCategoryName.isNotBlank() && !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add Category") }
            }

            // ── Clear recipes (destructive) ─────────────────────────────────
            NerdModeSection(title = "Clear Recipes") {
                Text(
                    text = "Warning: this deletes the recipe book (all saved recipes).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showClearRecipesConfirm1 = true },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (clearRecipesState is NerdModeOpState.InProgress) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Delete Recipe Book")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Reusable section card ─────────────────────────────────────────────────────

@Composable
private fun NerdModeSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

