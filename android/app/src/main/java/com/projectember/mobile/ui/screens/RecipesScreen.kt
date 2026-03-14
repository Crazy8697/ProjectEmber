package com.projectember.mobile.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.projectember.mobile.data.local.entities.Recipe
import com.projectember.mobile.data.local.entities.decodeIngredients
import com.projectember.mobile.ui.theme.KetoAccent
import com.projectember.mobile.ui.theme.OnSurface
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    viewModel: RecipesViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAddRecipe: () -> Unit,
    onNavigateToEditRecipe: (Int) -> Unit
) {
    val recipes by viewModel.recipes.collectAsState()
    val selectedRecipe by viewModel.selectedRecipe.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val availableCategories by viewModel.availableCategories.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }

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
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete recipe",
                                tint = MaterialTheme.colorScheme.error
                            )
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
                    contentColor = OnSurface
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add recipe")
                }
            }
        }
    ) { paddingValues ->
        if (selectedRecipe != null) {
            val recipe = selectedRecipe!!
            RecipeDetailView(
                recipe = recipe,
                onLogToKeto = {
                    viewModel.logRecipeToKeto(
                        recipe = recipe,
                        onDone = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("\"${recipe.name}\" logged to Keto")
                            }
                        },
                        onError = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Failed to log recipe. Please try again.")
                            }
                        }
                    )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeListView(
    recipes: List<Recipe>,
    availableCategories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    onRecipeClick: (Recipe) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        if (availableCategories.size > 2) {
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    availableCategories.forEach { cat ->
                        val isSelected = selectedCategory == cat
                        AssistChip(
                            onClick = { onCategorySelected(cat) },
                            label = { Text(cat) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (isSelected) KetoAccent
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = if (isSelected) OnSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
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
                    }
                }
            }
        } else {
            items(recipes) { recipe ->
                RecipeCard(recipe = recipe, onClick = { onRecipeClick(recipe) })
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) } // padding to clear FAB
    }
}

@Composable
private fun RecipeCard(recipe: Recipe, onClick: () -> Unit) {
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
                Text(
                    text = recipe.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = KetoAccent,
                    modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                )
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
                    text = if (recipe.servings == 1.0) "1 srv"
                        else "%.1g srv".format(recipe.servings),
                    style = MaterialTheme.typography.labelSmall,
                    color = KetoAccent
                )
            }
        }
    }
}

@Composable
private fun RecipeDetailView(recipe: Recipe, onLogToKeto: () -> Unit, modifier: Modifier = Modifier) {
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

        Text(
            text = "Macros (per serving)",
            style = MaterialTheme.typography.titleLarge
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                MacroDetailRow("Calories", "%.0f kcal".format(recipe.calories))
                MacroDetailRow("Protein", "%.1f g".format(recipe.proteinG))
                MacroDetailRow("Fat", "%.1f g".format(recipe.fatG))
                MacroDetailRow("Total Carbs", "%.1f g".format(recipe.totalCarbsG))
                MacroDetailRow("Fiber", "%.1f g".format(recipe.fiberG))
                MacroDetailRow("Net Carbs", "%.1f g".format(recipe.netCarbsG))
                MacroDetailRow("Servings", "%.1f".format(recipe.servings))
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
                    if (recipe.sodiumMg > 0.0)
                        MacroDetailRow("Sodium", "%.0f mg".format(recipe.sodiumMg))
                    if (recipe.potassiumMg > 0.0)
                        MacroDetailRow("Potassium", "%.0f mg".format(recipe.potassiumMg))
                    if (recipe.magnesiumMg > 0.0)
                        MacroDetailRow("Magnesium", "%.0f mg".format(recipe.magnesiumMg))
                    if (recipe.waterMl > 0.0)
                        MacroDetailRow("Water", "%.0f mL".format(recipe.waterMl))
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

        Button(
            onClick = onLogToKeto,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Log to Keto")
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
