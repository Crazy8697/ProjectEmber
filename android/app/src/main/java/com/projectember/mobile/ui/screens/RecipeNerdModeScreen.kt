package com.projectember.mobile.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.projectember.mobile.data.recipe.DuplicateHandling
import com.projectember.mobile.data.recipe.RecipeCandidateItem
import com.projectember.mobile.data.recipe.RecipeImportPreview
import com.projectember.mobile.data.recipe.RecipeParseError
import java.io.File

private enum class ImportMethod { FILE, PASTE }

private fun recipeExportFileName() = "ember_recipes_${System.currentTimeMillis()}.json"

/** Template shown to users who click "Load Template" in the paste import flow. */
private val RECIPE_IMPORT_TEMPLATE = """
{
  "schemaVersion": 1,
  "appVersion": "0.3",
  "exportedAt": "2026-03-18T12:00:00Z",
  "recipeCount": 1,
  "recipes": [
    {
      "name": "Goblin Electrolyte Coffee",
      "category": "Drink",
      "description": "Butter coffee with light electrolytes and almond milk.",
      "servings": 1,
      "calories": 65,
      "proteinG": 1,
      "fatG": 6,
      "netCarbsG": 1,
      "totalCarbsG": 2,
      "fiberG": 1,
      "sodiumMg": 250,
      "potassiumMg": 220,
      "magnesiumMg": 8,
      "waterMl": 590,
      "ketoNotes": "Light electrolyte support drink. Not a full correction dose.",
      "ingredientsRaw": "Coffee\t590ml\nSalt\t1/16 tsp\nNoSalt\t1/16 tsp\nSalted Butter\t1/2 tbsp\nAlmond Milk (unsweetened)\t1/3 cup\nCinnamon\tpinch"
    }
  ]
}
""".trimIndent()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeNerdModeScreen(
    viewModel: RecipeNerdModeViewModel,
    onNavigateBack: () -> Unit
) {
    val exportState by viewModel.exportState.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val clearRecipesState by viewModel.clearRecipesState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val duplicateHandling by viewModel.duplicateHandling.collectAsState()
    val pendingShareBytes by viewModel.pendingShareBytes.collectAsState()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var importMethod by remember { mutableStateOf(ImportMethod.FILE) }
    var pasteText by remember { mutableStateOf("") }
    var showTemplateReplaceDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var categoryToEdit by remember { mutableStateOf<String?>(null) }
    var editCategoryName by remember { mutableStateOf("") }
    var categoryToDelete by remember { mutableStateOf<String?>(null) }
    var showClearRecipesConfirm1 by remember { mutableStateOf(false) }
    var showClearRecipesConfirm2 by remember { mutableStateOf(false) }

    // ── Template replace confirmation dialog ──────────────────────────────────
    if (showTemplateReplaceDialog) {
        AlertDialog(
            onDismissRequest = { showTemplateReplaceDialog = false },
            title = { Text("Replace JSON?") },
            text = { Text("This will replace your current JSON content with the template. Any unsaved changes will be lost. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    pasteText = RECIPE_IMPORT_TEMPLATE
                    showTemplateReplaceDialog = false
                }) {
                    Text("Replace")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTemplateReplaceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── SAF file-save launcher (Export) ──────────────────────────────────────
    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportToUri(uri)
    }

    // ── SAF file-open launcher (Import) ──────────────────────────────────────
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.loadImportFromUri(uri)
    }

    // ── Share intent for export ───────────────────────────────────────────────
    LaunchedEffect(pendingShareBytes) {
        val bytes = pendingShareBytes ?: return@LaunchedEffect
        try {
            val cacheFile = File(
                context.cacheDir,
                recipeExportFileName()
            )
            cacheFile.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_SUBJECT, "Project Ember Recipes")
                putExtra(Intent.EXTRA_TEXT, "Recipe export from Project Ember.")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Recipes"))
        } catch (e: Exception) {
            snackbarHostState.showSnackbar("Share failed: ${e.message ?: "Unknown error"}")
        } finally {
            viewModel.clearPendingShareBytes()
        }
    }

    // ── Export state feedback ─────────────────────────────────────────────────
    LaunchedEffect(exportState) {
        when (val s = exportState) {
            is NerdModeOpState.Success -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.clearExportState()
            }
            is NerdModeOpState.Error -> {
                snackbarHostState.showSnackbar("Export error: ${s.message}")
                viewModel.clearExportState()
            }
            else -> Unit
        }
    }

    // ── Import state feedback ─────────────────────────────────────────────────
    LaunchedEffect(importState) {
        when (val s = importState) {
            is NerdModeOpState.Success -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.clearImportState()
            }
            is NerdModeOpState.Error -> {
                snackbarHostState.showSnackbar("Import error: ${s.message}")
                viewModel.clearImportState()
            }
            else -> Unit
        }
    }

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

    val isBusy = exportState is NerdModeOpState.InProgress ||
        importState is NerdModeOpState.InProgress ||
        clearRecipesState is NerdModeOpState.InProgress

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
                    text = "Manage recipe categories and recipe import/export tools.",
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

            // ── Export section ────────────────────────────────────────────────
            NerdModeSection(title = "Export Recipes") {
                Text(
                    text = "Export your full recipe library as a JSON file.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            exportFileLauncher.launch(recipeExportFileName())
                        },
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save to File")
                    }
                    OutlinedButton(
                        onClick = { viewModel.buildShareExport() },
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (exportState is NerdModeOpState.InProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share")
                    }
                }
            }

            // ── Import section ────────────────────────────────────────────────
            NerdModeSection(title = "Import Recipes") {
                Text(
                    text = "Import recipes from a JSON file or by pasting JSON directly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Method toggle
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImportMethodButton(
                        label = "From File",
                        selected = importMethod == ImportMethod.FILE,
                        onClick = { importMethod = ImportMethod.FILE },
                        modifier = Modifier.weight(1f)
                    )
                    ImportMethodButton(
                        label = "Paste JSON",
                        selected = importMethod == ImportMethod.PASTE,
                        onClick = { importMethod = ImportMethod.PASTE },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                when (importMethod) {
                    ImportMethod.FILE -> {
                        Button(
                            onClick = { importFileLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (importState is NerdModeOpState.InProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Reading…")
                            } else {
                                Icon(
                                    Icons.Default.FileUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Choose File")
                            }
                        }
                    }

                    ImportMethod.PASTE -> {
                        // ── Helper buttons ────────────────────────────────────
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (pasteText.isBlank()) {
                                        pasteText = RECIPE_IMPORT_TEMPLATE
                                    } else {
                                        showTemplateReplaceDialog = true
                                    }
                                },
                                enabled = !isBusy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Load Template")
                            }
                            OutlinedButton(
                                onClick = { pasteText = "" },
                                enabled = !isBusy && pasteText.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Clear")
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        // ── Helper text ───────────────────────────────────────
                        Text(
                            text = "Paste recipe JSON here. Use 'Load Template' to see the expected format.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // ── Text input ────────────────────────────────────────
                        OutlinedTextField(
                            value = pasteText,
                            onValueChange = { pasteText = it },
                            label = { Text("Paste JSON here") },
                            placeholder = { Text("{ \"recipes\": [ … ] }") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            maxLines = 20,
                            enabled = !isBusy
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // ── Validate button ───────────────────────────────────
                        Button(
                            onClick = { viewModel.validatePasteImport(pasteText) },
                            enabled = !isBusy && pasteText.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (importState is NerdModeOpState.InProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Validating…")
                            } else {
                                Text("Validate JSON")
                            }
                        }
                    }
                }
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

            // ── Import preview (shows after successful validation) ─────────────
            if (preview != null) {
                ImportPreviewSection(
                    preview = preview!!,
                    duplicateHandling = duplicateHandling,
                    onDuplicateHandlingChange = viewModel::setDuplicateHandling,
                    onCommit = { viewModel.commitImport() },
                    onCancel = { viewModel.cancelImport() },
                    isBusy = isBusy
                )
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

// ── Import method toggle button ───────────────────────────────────────────────

@Composable
private fun ImportMethodButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

// ── Import preview section ────────────────────────────────────────────────────

@Composable
private fun ImportPreviewSection(
    preview: RecipeImportPreview,
    duplicateHandling: DuplicateHandling,
    onDuplicateHandlingChange: (DuplicateHandling) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
    isBusy: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Import Preview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Summary row
            PreviewSummaryRow(preview)

            // Invalid entries (if any)
            if (preview.invalid.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Entries that will be skipped (${preview.invalid.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(4.dp))
                preview.invalid.forEach { err ->
                    InvalidEntryRow(err)
                }
            }

            // Valid recipes list (summary)
            if (preview.valid.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Valid recipes (${preview.valid.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                preview.valid.take(10).forEach { item ->
                    ValidRecipeRow(item)
                }
                if (preview.valid.size > 10) {
                    Text(
                        text = "…and ${preview.valid.size - 10} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }

            // Duplicate handling (only shown when there are duplicates)
            if (preview.duplicates.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Duplicate handling (${preview.duplicates.size} found)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "A duplicate matches an existing recipe by name and category.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                DuplicateHandling.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = duplicateHandling == option,
                            onClick = { onDuplicateHandlingChange(option) }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = when (option) {
                                    DuplicateHandling.SKIP -> "Skip duplicates"
                                    DuplicateHandling.OVERWRITE -> "Overwrite existing"
                                    DuplicateHandling.IMPORT_AS_NEW -> "Import as new"
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = when (option) {
                                    DuplicateHandling.SKIP -> "Existing recipes are kept unchanged"
                                    DuplicateHandling.OVERWRITE -> "Existing recipes are replaced with imported data"
                                    DuplicateHandling.IMPORT_AS_NEW -> "Imported recipes are added alongside existing ones"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = onCancel,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onCommit,
                    enabled = !isBusy && preview.valid.isNotEmpty(),
                    modifier = Modifier.weight(2f)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Importing…")
                    } else {
                        Text("Commit Import")
                    }
                }
            }
        }
    }
}

// ── Preview summary ───────────────────────────────────────────────────────────

@Composable
private fun PreviewSummaryRow(preview: RecipeImportPreview) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SummaryLine(
            label = "Found",
            value = "${preview.totalFound} recipe${if (preview.totalFound != 1) "s" else ""}"
        )
        SummaryLine(
            label = "Valid",
            value = "${preview.valid.size}",
            iconOk = preview.valid.isNotEmpty()
        )
        if (preview.invalid.isNotEmpty()) {
            SummaryLine(
                label = "Will be skipped",
                value = "${preview.invalid.size}",
                isError = true
            )
        }
        if (preview.duplicates.isNotEmpty()) {
            SummaryLine(
                label = "Duplicates",
                value = "${preview.duplicates.size}"
            )
        }
    }
}

@Composable
private fun SummaryLine(
    label: String,
    value: String,
    iconOk: Boolean = false,
    isError: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isError) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
        } else if (iconOk) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
        if (isError || iconOk) Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

// ── Invalid entry row ─────────────────────────────────────────────────────────

@Composable
private fun InvalidEntryRow(err: RecipeParseError) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .size(14.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = err.reason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

// ── Valid recipe row ──────────────────────────────────────────────────────────

@Composable
private fun ValidRecipeRow(item: RecipeCandidateItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (item.isDuplicate) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = buildString {
                append(item.candidateRecipe.name)
                if (item.candidateRecipe.category.isNotBlank() &&
                    item.candidateRecipe.category != "General"
                ) {
                    append(" (${item.candidateRecipe.category})")
                }
                if (item.isDuplicate) append(" — duplicate")
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (item.isDuplicate) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}
