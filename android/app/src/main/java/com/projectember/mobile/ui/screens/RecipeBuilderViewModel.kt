package com.projectember.mobile.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.local.RecipeCategoryStore
import com.projectember.mobile.data.local.entities.BuilderRowData
import com.projectember.mobile.data.local.entities.Ingredient
import com.projectember.mobile.data.local.entities.Recipe
import com.projectember.mobile.data.local.entities.encodeBuilderRows
import com.projectember.mobile.data.repository.IngredientRepository
import com.projectember.mobile.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Runtime state for a single ingredient row in the builder.
 *
 * [amountText] is what the user has typed.  Nutrition contribution is scaled
 * from the ingredient's reference serving by the ratio amount / defaultAmount.
 */
data class BuilderRowState(
    val ingredient: Ingredient,
    val amountText: String
) {
    private val parsedAmount: Double get() = amountText.toDoubleOrNull() ?: 0.0
    private val scale: Double
        get() = if (ingredient.defaultAmount > 0) parsedAmount / ingredient.defaultAmount else 0.0

    val contributionCalories: Double get() = ingredient.calories * scale
    val contributionProteinG: Double get() = ingredient.proteinG * scale
    val contributionFatG: Double get() = ingredient.fatG * scale
    val contributionNetCarbsG: Double get() = ingredient.netCarbsG * scale
    val contributionTotalCarbsG: Double get() = ingredient.totalCarbsG * scale
    val contributionFiberG: Double get() = ingredient.fiberG * scale
    val contributionSodiumMg: Double get() = ingredient.sodiumMg * scale
    val contributionPotassiumMg: Double get() = ingredient.potassiumMg * scale
    val contributionMagnesiumMg: Double get() = ingredient.magnesiumMg * scale
    val contributionWaterMl: Double get() = ingredient.waterMl * scale

    fun toBuilderRowData() = BuilderRowData(
        ingredientId = ingredient.id,
        ingredientName = ingredient.name,
        amount = parsedAmount,
        unit = ingredient.defaultUnit,
        contributionCalories = contributionCalories,
        contributionProteinG = contributionProteinG,
        contributionFatG = contributionFatG,
        contributionNetCarbsG = contributionNetCarbsG,
        contributionTotalCarbsG = contributionTotalCarbsG,
        contributionFiberG = contributionFiberG,
        contributionSodiumMg = contributionSodiumMg,
        contributionPotassiumMg = contributionPotassiumMg,
        contributionMagnesiumMg = contributionMagnesiumMg,
        contributionWaterMl = contributionWaterMl
    )
}

class RecipeBuilderViewModel(
    private val recipeRepository: RecipeRepository,
    private val ingredientRepository: IngredientRepository,
    recipeCategoryStore: RecipeCategoryStore? = null
) : ViewModel() {

    val allIngredients: StateFlow<List<Ingredient>> = ingredientRepository.getAllIngredients()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<String>> =
        recipeCategoryStore?.categories ?: MutableStateFlow(AddEditRecipeViewModel.CATEGORIES)

    // ── Form state ────────────────────────────────────────────────────────────

    var name by mutableStateOf("")
        private set
    var category by mutableStateOf("General")
        private set
    var description by mutableStateOf("")
        private set
    var servings by mutableStateOf("1")
        private set
    var rows by mutableStateOf<List<BuilderRowState>>(emptyList())
        private set
    var nameError by mutableStateOf<String?>(null)
        private set

    private val _saveResult = MutableStateFlow<String?>(null)
    val saveResult: StateFlow<String?> = _saveResult.asStateFlow()

    // ── Derived totals ────────────────────────────────────────────────────────

    val totalCalories: Double get() = rows.sumOf { it.contributionCalories }
    val totalProteinG: Double get() = rows.sumOf { it.contributionProteinG }
    val totalFatG: Double get() = rows.sumOf { it.contributionFatG }
    val totalNetCarbsG: Double get() = rows.sumOf { it.contributionNetCarbsG }
    val totalTotalCarbsG: Double get() = rows.sumOf { it.contributionTotalCarbsG }
    val totalFiberG: Double get() = rows.sumOf { it.contributionFiberG }
    val totalSodiumMg: Double get() = rows.sumOf { it.contributionSodiumMg }
    val totalPotassiumMg: Double get() = rows.sumOf { it.contributionPotassiumMg }
    val totalMagnesiumMg: Double get() = rows.sumOf { it.contributionMagnesiumMg }
    val totalWaterMl: Double get() = rows.sumOf { it.contributionWaterMl }

    // ── Mutations ─────────────────────────────────────────────────────────────

    fun onNameChange(v: String) { name = v; nameError = null }
    fun onCategoryChange(v: String) { category = v }
    fun onDescriptionChange(v: String) { description = v }
    fun onServingsChange(v: String) { servings = v }

    fun addIngredient(ingredient: Ingredient) {
        val defaultText = fmtAmount(ingredient.defaultAmount)
        rows = rows + BuilderRowState(ingredient, defaultText)
    }

    fun removeRow(index: Int) {
        rows = rows.filterIndexed { i, _ -> i != index }
    }

    fun updateRowAmount(index: Int, amountText: String) {
        rows = rows.mapIndexed { i, row ->
            if (i == index) row.copy(amountText = amountText) else row
        }
    }

    fun save(onSuccess: () -> Unit, onValidationFailed: () -> Unit = {}) {
        if (name.isBlank()) {
            nameError = "Recipe name is required"
            onValidationFailed()
            return
        }
        viewModelScope.launch {
            val srv = servings.toDoubleOrNull()?.coerceAtLeast(0.1) ?: 1.0
            val builderData = rows.map { it.toBuilderRowData() }
            val recipe = Recipe(
                name = name.trim(),
                category = category,
                description = description.trim().ifBlank { null },
                servings = srv,
                calories = totalCalories,
                proteinG = totalProteinG,
                fatG = totalFatG,
                netCarbsG = totalNetCarbsG,
                totalCarbsG = totalTotalCarbsG,
                fiberG = totalFiberG,
                sodiumMg = totalSodiumMg,
                potassiumMg = totalPotassiumMg,
                magnesiumMg = totalMagnesiumMg,
                waterMl = totalWaterMl,
                builderRows = encodeBuilderRows(builderData)
            )
            recipeRepository.insertRecipe(recipe)
            onSuccess()
        }
    }

    fun clearSaveResult() { _saveResult.value = null }

    private fun fmtAmount(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else "%.1f".format(d)
}

class RecipeBuilderViewModelFactory(
    private val recipeRepository: RecipeRepository,
    private val ingredientRepository: IngredientRepository,
    private val recipeCategoryStore: RecipeCategoryStore? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        RecipeBuilderViewModel(recipeRepository, ingredientRepository, recipeCategoryStore) as T
}
