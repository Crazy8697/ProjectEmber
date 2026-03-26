package com.projectember.mobile.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.projectember.mobile.data.recipe.DuplicateHandling
import com.projectember.mobile.data.recipe.RecipeCandidateItem
import com.projectember.mobile.data.recipe.RecipeImportPreview
import com.projectember.mobile.data.recipe.RecipeParseError
import com.projectember.mobile.ui.theme.KetoAccent
import kotlinx.coroutines.launch
import java.io.File

private enum class RecipeJsonImportMethod { FILE, PASTE }

private fun recipeExportFileName() = "ember_recipes_${System.currentTimeMillis()}.json"

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

// categories are provided by the ViewModel (may be customized by user)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditRecipeScreen(
    viewModel: AddEditRecipeViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit = {}
) {
    val unitPrefs by viewModel.unitPreferences.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val exportState by viewModel.exportState.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val duplicateHandling by viewModel.duplicateHandling.collectAsState()
    val pendingShareBytes by viewModel.pendingShareBytes.collectAsState()
    val foodSym = unitPrefs.foodWeightUnit.symbol
    val volSym  = unitPrefs.volumeUnit.symbol
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var importMethod by remember { mutableStateOf(RecipeJsonImportMethod.FILE) }
    var pasteText by remember { mutableStateOf("") }
    var showTemplateReplaceDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val isBusy = exportState is NerdModeOpState.InProgress || importState is NerdModeOpState.InProgress

    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportToUri(uri)
    }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.loadImportFromUri(uri)
    }

    if (showTemplateReplaceDialog) {
        AlertDialog(
            onDismissRequest = { showTemplateReplaceDialog = false },
            title = { Text("Replace JSON?") },
            text = { Text("This replaces current pasted JSON with the template.") },
            confirmButton = {
                TextButton(onClick = {
                    pasteText = RECIPE_IMPORT_TEMPLATE
                    showTemplateReplaceDialog = false
                }) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = { showTemplateReplaceDialog = false }) { Text("Cancel") }
            }
        )
    }

    LaunchedEffect(pendingShareBytes) {
        val bytes = pendingShareBytes ?: return@LaunchedEffect
        try {
            val cacheFile = File(context.cacheDir, recipeExportFileName())
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
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data -> Snackbar(snackbarData = data) }
        },
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Category", style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { categoryExpanded = !categoryExpanded }) {
                        Text(if (categoryExpanded) "Collapse" else "Change")
                    }
                }
                AssistChip(
                    onClick = { categoryExpanded = true },
                    label = { Text(viewModel.category) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = KetoAccent,
                        labelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                if (categoryExpanded) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        categories.forEach { cat ->
                            val isSelected = viewModel.category == cat
                            AssistChip(
                                onClick = {
                                    viewModel.onCategoryChange(cat)
                                    categoryExpanded = false
                                },
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
                } else {
                    Text(
                        text = "Tap Change to browse all categories.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Additional tags ───────────────────────────────────────────────
            val taggableCategories = categories.filter { it != viewModel.category }
            if (taggableCategories.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Also applies to",
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        taggableCategories.forEach { cat ->
                            val isTagged = cat in viewModel.additionalTags
                            AssistChip(
                                onClick = { viewModel.onTagToggle(cat) },
                                label = { Text(cat) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (isTagged) KetoAccent.copy(alpha = 0.75f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = if (isTagged) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
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

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Recipe JSON Import / Export",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "Use existing recipe JSON schema to export or import recipes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { exportFileLauncher.launch(recipeExportFileName()) },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save File")
                        }
                        OutlinedButton(
                            onClick = { viewModel.buildShareExport() },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ImportMethodButton(
                            label = "From File",
                            selected = importMethod == RecipeJsonImportMethod.FILE,
                            onClick = { importMethod = RecipeJsonImportMethod.FILE },
                            modifier = Modifier.weight(1f)
                        )
                        ImportMethodButton(
                            label = "Paste JSON",
                            selected = importMethod == RecipeJsonImportMethod.PASTE,
                            onClick = { importMethod = RecipeJsonImportMethod.PASTE },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    when (importMethod) {
                        RecipeJsonImportMethod.FILE -> {
                            Button(
                                onClick = {
                                    importFileLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                },
                                enabled = !isBusy,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Choose Import File")
                            }
                        }

                        RecipeJsonImportMethod.PASTE -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        if (pasteText.isBlank()) pasteText = RECIPE_IMPORT_TEMPLATE
                                        else showTemplateReplaceDialog = true
                                    },
                                    enabled = !isBusy,
                                    modifier = Modifier.weight(1f)
                                ) { Text("Template") }
                                OutlinedButton(
                                    onClick = { pasteText = "" },
                                    enabled = !isBusy && pasteText.isNotBlank(),
                                    modifier = Modifier.weight(1f)
                                ) { Text("Clear") }
                            }
                            Text(
                                text = "Paste recipe JSON, then validate before import.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = pasteText,
                                onValueChange = { pasteText = it },
                                label = { Text("Paste JSON here") },
                                placeholder = { Text("{ \"recipes\": [ ... ] }") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp),
                                maxLines = 20,
                                enabled = !isBusy
                            )
                            Button(
                                onClick = { viewModel.validatePasteImport(pasteText) },
                                enabled = !isBusy && pasteText.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Validate JSON")
                            }
                        }
                    }

                    if (preview != null) {
                        ImportPreviewSection(
                            preview = preview!!,
                            duplicateHandling = duplicateHandling,
                            onDuplicateHandlingChange = viewModel::setDuplicateHandling,
                            onCommit = viewModel::commitImport,
                            onCancel = viewModel::cancelImport,
                            isBusy = isBusy
                        )
                    }
                }
            }

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

@Composable
private fun ImportMethodButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) Button(onClick = onClick, modifier = modifier) { Text(label) }
    else OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
}

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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Import Preview", style = MaterialTheme.typography.titleSmall)
            SummaryLine("Found", preview.totalFound.toString(), isError = false)
            SummaryLine("Valid", preview.valid.size.toString(), iconOk = preview.valid.isNotEmpty())
            if (preview.invalid.isNotEmpty()) {
                SummaryLine("Will be skipped", preview.invalid.size.toString(), isError = true)
                preview.invalid.take(3).forEach { err -> InvalidEntryRow(err) }
            }
            if (preview.valid.isNotEmpty()) {
                preview.valid.take(5).forEach { item -> ValidRecipeRow(item) }
            }
            if (preview.duplicates.isNotEmpty()) {
                Text("Duplicate handling", style = MaterialTheme.typography.labelMedium)
                DuplicateHandling.entries.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = duplicateHandling == option,
                            onClick = { onDuplicateHandlingChange(option) }
                        )
                        Text(
                            when (option) {
                                DuplicateHandling.SKIP -> "Skip duplicates"
                                DuplicateHandling.OVERWRITE -> "Overwrite existing"
                                DuplicateHandling.IMPORT_AS_NEW -> "Import as new"
                            }
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCancel, enabled = !isBusy, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = onCommit,
                    enabled = !isBusy && preview.valid.isNotEmpty(),
                    modifier = Modifier.weight(2f)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(16.dp).height(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Commit Import")
                }
            }
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
            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(4.dp))
        } else if (iconOk) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = "$label: $value",
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun InvalidEntryRow(err: RecipeParseError) {
    Text(
        text = "- ${err.reason}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

@Composable
private fun ValidRecipeRow(item: RecipeCandidateItem) {
    Text(
        text = "- ${item.candidateRecipe.name}${if (item.isDuplicate) " (duplicate)" else ""}",
        style = MaterialTheme.typography.bodySmall,
        color = if (item.isDuplicate) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
    )
}

