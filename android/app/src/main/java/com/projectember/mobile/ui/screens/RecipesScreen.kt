package com.projectember.mobile.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.projectember.mobile.data.local.entities.Recipe
import androidx.compose.material.icons.filled.MoreVert
import com.projectember.mobile.data.local.entities.decodeIngredients
import com.projectember.mobile.ui.theme.KetoAccent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    viewModel: RecipesViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAddRecipe: () -> Unit,
    onNavigateToEditRecipe: (Int) -> Unit,
    onNavigateToNerdMode: () -> Unit,
    onNavigateToBulkCategory: () -> Unit = {},
    onNavigateToIngredientIndex: () -> Unit = {},
    onNavigateToRecipeBuilder: () -> Unit = {}
) {
    val recipes by viewModel.recipes.collectAsState()
    val selectedRecipe by viewModel.selectedRecipe.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val availableCategories by viewModel.availableCategories.collectAsState()
    val unitPrefs by viewModel.unitPreferences.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRecipeMenu by remember { mutableStateOf(false) }
    var showMainMenu by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        val recipeName = selectedRecipe?.name ?: ""
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Recipe") },
            text = {
                Text(
                    "Are you sure you want to delete \"$recipeName\"? " +
                        "This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteSelectedRecipe(
                            onDone = {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("\"$recipeName\" deleted")
                                }
                            }
                        )
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Recipes") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedRecipe != null) viewModel.clearSelectedRecipe()
                        else onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedRecipe != null) {
                        IconButton(onClick = {
                            val id = selectedRecipe?.id ?: return@IconButton
                            viewModel.clearSelectedRecipe()
                            onNavigateToEditRecipe(id)
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit recipe")
                        }
                        Box {
                            IconButton(onClick = { showRecipeMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showRecipeMenu,
                                onDismissRequest = { showRecipeMenu = false }
                            ) {
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
                                        showRecipeMenu = false
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }
                    } else {
                        Box {
                            IconButton(onClick = { showMainMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showMainMenu,
                                onDismissRequest = { showMainMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Recipe Builder") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                    },
                                    onClick = {
                                        showMainMenu = false
                                        onNavigateToRecipeBuilder()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Ingredient Index") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Edit, contentDescription = null)
                                    },
                                    onClick = {
                                        showMainMenu = false
                                        onNavigateToIngredientIndex()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Category Manager") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Edit, contentDescription = null)
                                    },
                                    onClick = {
                                        showMainMenu = false
                                        onNavigateToBulkCategory()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Recipe Settings") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Settings, contentDescription = null)
                                    },
                                    onClick = {
                                        showMainMenu = false
                                        onNavigateToNerdMode()
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
        },
        floatingActionButton = {
            if (selectedRecipe == null) {
                FloatingActionButton(
                    onClick = onNavigateToAddRecipe,
                    containerColor = KetoAccent,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add recipe")
                }
            }
        }
    ) { paddingValues ->
        if (selectedRecipe != null) {
            val recipe = selectedRecipe!!
            var logServingsInput by remember(recipe.id) { mutableStateOf("1") }
            RecipeDetailView(
                recipe = recipe,
                logServingsInput = logServingsInput,
                onLogServingsChange = { logServingsInput = it },
                unitPrefs = unitPrefs,
                onLogToKeto = {
                    val consumed = logServingsInput.toDoubleOrNull()
                    if (consumed == null || consumed <= 0.0) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Enter a valid serving amount to log")
                        }
                    } else {
                        viewModel.logRecipeToKeto(
                            recipe = recipe,
                            servingsConsumed = consumed,
                            onDone = {
                                coroutineScope.launch {
                                    val servingLabel = if (consumed == consumed.toLong().toDouble())
                                        consumed.toLong().toString()
                                    else "%.1f".format(consumed)
                                    snackbarHostState.showSnackbar(
                                        "Logged $servingLabel serving${if (consumed != 1.0) "s" else ""} of \"${recipe.name}\""
                                    )
                                }
                            },
                            onError = {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Failed to log recipe. Please try again.")
                                }
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            )
        } else {
            RecipeListView(
                recipes = recipes,
                availableCategories = availableCategories,
                selectedCategory = selectedCategory,
                onCategorySelected = viewModel::onCategorySelected,
                onRecipeClick = { viewModel.selectRecipe(it) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeListView(
    recipes: List<Recipe>,
    availableCategories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    onRecipeClick: (Recipe) -> Unit,
    modifier: Modifier = Modifier
) {
    // Compute grouped data only when showing "All" with multiple categories present.
    // We sort groups alphabetically; each group's recipes are also sorted by name.
    val showGrouped = selectedCategory == RecipesViewModel.ALL_CATEGORIES &&
        recipes.map { it.category }.distinct().size > 1
    val groupedRecipes: List<Pair<String, List<Recipe>>> = if (showGrouped) {
        recipes
            .groupBy { it.category }
            .entries
            .sortedBy { it.key }
            .map { (cat, catRecipes) -> cat to catRecipes.sortedBy { r -> r.name } }
    } else {
        emptyList()
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        if (availableCategories.size > 1) {
            item {
                var dropdownExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        availableCategories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    onCategorySelected(cat)
                                    dropdownExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }
            }
        }

        if (recipes.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selectedCategory == RecipesViewModel.ALL_CATEGORIES) {
                            Text(
                                text = "No recipes yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Tap + to add your first recipe",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = "No recipes in \"$selectedCategory\"",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { onCategorySelected(RecipesViewModel.ALL_CATEGORIES) }) {
                                Text("Show all recipes")
                            }
                        }
                    }
                }
            }
        } else if (showGrouped) {
            // ── Grouped by category ──────────────────────────────────────────
            groupedRecipes.forEach { (category, categoryRecipes) ->
                item(key = "header_$category") {
                    RecipeCategoryHeader(label = category)
                }
                items(categoryRecipes, key = { it.id }) { recipe ->
                    RecipeCard(recipe = recipe, showCategory = false, onClick = { onRecipeClick(recipe) })
                }
            }
        } else {
            // ── Flat filtered list ────────────────────────────────────────────
            items(recipes, key = { it.id }) { recipe ->
                RecipeCard(recipe = recipe, onClick = { onRecipeClick(recipe) })
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) } // padding to clear FAB
    }
}

@Composable
private fun RecipeCategoryHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = KetoAccent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun RecipeCard(recipe: Recipe, onClick: () -> Unit, showCategory: Boolean = true) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = recipe.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (showCategory) {
                    Text(
                        text = recipe.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = KetoAccent,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                    )
                }
            }
            recipe.description?.let { desc ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "%.0f kcal  |  P: %.0fg  |  F: %.0fg  |  C: %.0fg".format(
                        recipe.calories, recipe.proteinG, recipe.fatG, recipe.netCarbsG
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (recipe.servings == recipe.servings.toLong().toDouble())
                        "${recipe.servings.toLong()} srv"
                    else "%.1f srv".format(recipe.servings),
                    style = MaterialTheme.typography.labelSmall,
                    color = KetoAccent
                )
            }
        }
    }
}

@Composable
private fun RecipeDetailView(
    recipe: Recipe,
    logServingsInput: String,
    onLogServingsChange: (String) -> Unit,
    onLogToKeto: () -> Unit,
    unitPrefs: com.projectember.mobile.data.local.UnitPreferences = com.projectember.mobile.data.local.UnitPreferences(),
    modifier: Modifier = Modifier
) {
    val foodSym = unitPrefs.foodWeightUnit.symbol
    val volSym  = unitPrefs.volumeUnit.symbol
    val foodUnit = unitPrefs.foodWeightUnit
    val volUnit  = unitPrefs.volumeUnit
    val ingredients = remember(recipe.ingredientsRaw) { decodeIngredients(recipe.ingredientsRaw) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = recipe.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = recipe.category,
                style = MaterialTheme.typography.labelMedium,
                color = KetoAccent
            )
        }

        recipe.description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider()

        // ── Servings header ──────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Nutrition",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = if (recipe.servings == recipe.servings.toLong().toDouble())
                    "${recipe.servings.toLong()} serving${if (recipe.servings != 1.0) "s" else ""}"
                else "%.1f servings".format(recipe.servings),
                style = MaterialTheme.typography.labelMedium,
                color = KetoAccent
            )
        }

        val srv = recipe.servings.coerceAtLeast(1.0)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("", modifier = Modifier.weight(1f))
                    Text(
                        text = "Whole Recipe",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Per Serving",
                        style = MaterialTheme.typography.labelSmall,
                        color = KetoAccent,
                        modifier = Modifier.weight(1f)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                NutritionRow("Calories",    "%.0f kcal".format(recipe.calories),                        "%.0f kcal".format(recipe.calories    / srv))
                NutritionRow("Protein",     "%.1f $foodSym".format(foodUnit.fromG(recipe.proteinG)),    "%.1f $foodSym".format(foodUnit.fromG(recipe.proteinG)    / srv))
                NutritionRow("Fat",         "%.1f $foodSym".format(foodUnit.fromG(recipe.fatG)),        "%.1f $foodSym".format(foodUnit.fromG(recipe.fatG)        / srv))
                NutritionRow("Total Carbs", "%.1f $foodSym".format(foodUnit.fromG(recipe.totalCarbsG)), "%.1f $foodSym".format(foodUnit.fromG(recipe.totalCarbsG) / srv))
                NutritionRow("Fiber",       "%.1f $foodSym".format(foodUnit.fromG(recipe.fiberG)),      "%.1f $foodSym".format(foodUnit.fromG(recipe.fiberG)      / srv))
                NutritionRow("Net Carbs",   "%.1f $foodSym".format(foodUnit.fromG(recipe.netCarbsG)),   "%.1f $foodSym".format(foodUnit.fromG(recipe.netCarbsG)   / srv))
            }
        }

        val hasExtendedNutrition = recipe.sodiumMg > 0.0 || recipe.potassiumMg > 0.0 ||
            recipe.magnesiumMg > 0.0 || recipe.waterMl > 0.0
        if (hasExtendedNutrition) {
            HorizontalDivider()
            Text(
                text = "Minerals & Hydration",
                style = MaterialTheme.typography.titleLarge
            )
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("", modifier = Modifier.weight(1f))
                        Text(
                            text = "Whole Recipe",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Per Serving",
                            style = MaterialTheme.typography.labelSmall,
                            color = KetoAccent,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    if (recipe.sodiumMg > 0.0)
                        NutritionRow("Sodium",    "%.0f mg".format(recipe.sodiumMg),                       "%.0f mg".format(recipe.sodiumMg    / srv))
                    if (recipe.potassiumMg > 0.0)
                        NutritionRow("Potassium", "%.0f mg".format(recipe.potassiumMg),                    "%.0f mg".format(recipe.potassiumMg / srv))
                    if (recipe.magnesiumMg > 0.0)
                        NutritionRow("Magnesium", "%.0f mg".format(recipe.magnesiumMg),                    "%.0f mg".format(recipe.magnesiumMg / srv))
                    if (recipe.waterMl > 0.0)
                        NutritionRow("Water",     "%.1f $volSym".format(volUnit.fromMl(recipe.waterMl)),   "%.1f $volSym".format(volUnit.fromMl(recipe.waterMl) / srv))
                }
            }
        }

        if (ingredients.isNotEmpty()) {
            HorizontalDivider()
            Text(
                text = "Ingredients",
                style = MaterialTheme.typography.titleLarge
            )
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)) {
                    ingredients.forEach { ingredient ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = ingredient.name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (ingredient.amount.isNotBlank()) {
                                Text(
                                    text = ingredient.amount,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        recipe.ketoNotes?.let { notes ->
            HorizontalDivider()
            Text(
                text = "Keto Notes",
                style = MaterialTheme.typography.titleLarge
            )
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        HorizontalDivider()

        // ── Log to Keto ──────────────────────────────────────────────────────
        Text(
            text = "Log to Keto",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = logServingsInput,
                onValueChange = onLogServingsChange,
                label = { Text("Servings") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(120.dp),
                singleLine = true
            )
            Button(
                onClick = onLogToKeto,
                modifier = Modifier.weight(1f)
            ) {
                Text("Log to Keto")
            }
        }

        // Real-time nutrition preview for the entered serving count
        val previewServings = logServingsInput.toDoubleOrNull()
        if (previewServings != null && previewServings > 0.0) {
            val recipeServings = recipe.servings.coerceAtLeast(1.0)
            val previewCal     = recipe.calories   / recipeServings * previewServings
            val previewProtein = foodUnit.fromG(recipe.proteinG   / recipeServings * previewServings)
            val previewFat     = foodUnit.fromG(recipe.fatG       / recipeServings * previewServings)
            val previewNc      = foodUnit.fromG(recipe.netCarbsG  / recipeServings * previewServings)
            Text(
                text = "≈ %.0f kcal · P %.1f%s · F %.1f%s · NC %.1f%s".format(
                    previewCal, previewProtein, foodSym, previewFat, foodSym, previewNc, foodSym
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun MacroDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun NutritionRow(label: String, total: String, perServing: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = total,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = perServing,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = KetoAccent,
            modifier = Modifier.weight(1f)
        )
    }
}
