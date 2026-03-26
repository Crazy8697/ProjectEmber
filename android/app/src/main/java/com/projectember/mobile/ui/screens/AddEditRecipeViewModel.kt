package com.projectember.mobile.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.projectember.mobile.data.local.FoodWeightUnit
import com.projectember.mobile.data.local.UnitPreferences
import com.projectember.mobile.data.local.UnitsPreferencesStore
import com.projectember.mobile.data.local.VolumeUnit
import com.projectember.mobile.data.local.RecipeCategoryStore
import com.projectember.mobile.data.recipe.DuplicateHandling
import com.projectember.mobile.data.recipe.RecipeImportExportManager
import com.projectember.mobile.data.recipe.RecipeImportPreview
import com.projectember.mobile.data.local.entities.Recipe
import com.projectember.mobile.data.local.entities.RecipeIngredient
import com.projectember.mobile.data.local.entities.decodeIngredients
import com.projectember.mobile.data.local.entities.encodeIngredients
import com.projectember.mobile.data.local.entities.parsedTags
import com.projectember.mobile.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AddEditRecipeViewModel(
    private val recipeRepository: RecipeRepository,
    private val editRecipeId: Int? = null,
    private val unitsPreferencesStore: UnitsPreferencesStore? = null,
    private val recipeCategoryStore: RecipeCategoryStore? = null,
    private val recipeImportExportManager: RecipeImportExportManager? = null
) : ViewModel() {

    companion object {
        val CATEGORIES = listOf(
            "General",
            "Breakfast",
            "Lunch",
            "Dinner",
            "Snack",
            "Side",
            "Sauce",
            "Drink",
            "Dessert"
        )

        private fun parseDoubleOrZero(value: String): Double = value.toDoubleOrNull() ?: 0.0

        private fun formatDouble(d: Double): String =
            if (d == 0.0) "" else d.toBigDecimal().stripTrailingZeros().toPlainString()
    }

    // Snapshot unit preferences at creation time so load and save use the same units.
    private val prefs: UnitPreferences = unitsPreferencesStore?.getPreferences() ?: UnitPreferences()
    private val foodUnit: FoodWeightUnit get() = prefs.foodWeightUnit
    private val volUnit: VolumeUnit get() = prefs.volumeUnit

    /** Exposed to the Screen so labels update correctly. */
    val unitPreferences: StateFlow<UnitPreferences> = MutableStateFlow(prefs)

    val categories: StateFlow<List<String>> = recipeCategoryStore?.categories ?: MutableStateFlow(CATEGORIES)

    private val _exportState = MutableStateFlow<NerdModeOpState>(NerdModeOpState.Idle)
    val exportState: StateFlow<NerdModeOpState> = _exportState

    private val _pendingShareBytes = MutableStateFlow<ByteArray?>(null)
    val pendingShareBytes: StateFlow<ByteArray?> = _pendingShareBytes

    private val _importState = MutableStateFlow<NerdModeOpState>(NerdModeOpState.Idle)
    val importState: StateFlow<NerdModeOpState> = _importState

    private val _preview = MutableStateFlow<RecipeImportPreview?>(null)
    val preview: StateFlow<RecipeImportPreview?> = _preview

    private val _duplicateHandling = MutableStateFlow(DuplicateHandling.SKIP)
    val duplicateHandling: StateFlow<DuplicateHandling> = _duplicateHandling

    val isEditMode: Boolean get() = editRecipeId != null

    private var originalRecipe: Recipe? = null

    var name by mutableStateOf("")
        private set
    var category by mutableStateOf("General")
        private set
    var description by mutableStateOf("")
        private set

    // Macro fields — held in display units (g or oz per preference)
    var calories by mutableStateOf("")
        private set
    var proteinG by mutableStateOf("")
        private set
    var fatG by mutableStateOf("")
        private set

    // Carb fields — netCarbsG is derived (totalCarbsG − fiberG) on save
    var totalCarbsG by mutableStateOf("")
        private set
    var fiberG by mutableStateOf("")
        private set

    // Extended nutrition fields
    var sodiumMg by mutableStateOf("")
        private set
    var potassiumMg by mutableStateOf("")
        private set
    var magnesiumMg by mutableStateOf("")
        private set
    // Held in display units (mL or cups per preference)
    var waterMl by mutableStateOf("")
        private set

    var servings by mutableStateOf("1")
        private set

    // Ingredient list
    var ingredients by mutableStateOf<List<RecipeIngredient>>(emptyList())
        private set

    /** Additional category tags (beyond the primary [category]). */
    var additionalTags by mutableStateOf<Set<String>>(emptySet())
        private set

    var nameError by mutableStateOf<String?>(null)
        private set

    init {
        if (editRecipeId != null) {
            viewModelScope.launch {
                val recipe = recipeRepository.getRecipeById(editRecipeId)
                if (recipe != null) {
                    originalRecipe = recipe
                    name = recipe.name
                    category = recipe.category
                    description = recipe.description ?: ""
                    calories = formatDouble(recipe.calories)
                    // Convert stored base-unit values to display units
                    proteinG = formatDouble(foodUnit.fromG(recipe.proteinG))
                    fatG = formatDouble(foodUnit.fromG(recipe.fatG))
                    totalCarbsG = formatDouble(foodUnit.fromG(recipe.totalCarbsG))
                    fiberG = formatDouble(foodUnit.fromG(recipe.fiberG))
                    sodiumMg = formatDouble(recipe.sodiumMg)
                    potassiumMg = formatDouble(recipe.potassiumMg)
                    magnesiumMg = formatDouble(recipe.magnesiumMg)
                    waterMl = formatDouble(volUnit.fromMl(recipe.waterMl))
                    servings = formatDouble(recipe.servings).ifBlank { "1" }
                    ingredients = decodeIngredients(recipe.ingredientsRaw)
                    additionalTags = recipe.parsedTags().toSet()
                }
            }
        }
    }

    fun onNameChange(value: String) {
        name = value
        nameError = null
    }

    fun onCategoryChange(value: String) {
        category = value
        // If the new primary category was also an additional tag, remove it to avoid duplication.
        additionalTags = additionalTags - value
    }

    fun onTagToggle(cat: String) {
        additionalTags = if (cat in additionalTags) additionalTags - cat else additionalTags + cat
    }
    fun onDescriptionChange(value: String) { description = value }
    fun onCaloriesChange(value: String) { calories = value }
    fun onProteinGChange(value: String) { proteinG = value }
    fun onFatGChange(value: String) { fatG = value }
    fun onTotalCarbsGChange(value: String) { totalCarbsG = value }
    fun onFiberGChange(value: String) { fiberG = value }
    fun onSodiumMgChange(value: String) { sodiumMg = value }
    fun onPotassiumMgChange(value: String) { potassiumMg = value }
    fun onMagnesiumMgChange(value: String) { magnesiumMg = value }
    fun onWaterMlChange(value: String) { waterMl = value }
    fun onServingsChange(value: String) { servings = value }

    fun exportToUri(uri: Uri) {
        val manager = recipeImportExportManager ?: return
        _exportState.value = NerdModeOpState.InProgress
        viewModelScope.launch {
            manager.exportToUri(uri).fold(
                onSuccess = {
                    _exportState.value = NerdModeOpState.Success("Recipes exported successfully")
                },
                onFailure = {
                    _exportState.value = NerdModeOpState.Error(it.message ?: "Export failed")
                }
            )
        }
    }

    fun buildShareExport() {
        val manager = recipeImportExportManager ?: return
        _exportState.value = NerdModeOpState.InProgress
        viewModelScope.launch {
            manager.buildExportBytes().fold(
                onSuccess = { bytes ->
                    _pendingShareBytes.value = bytes
                    _exportState.value = NerdModeOpState.Idle
                },
                onFailure = {
                    _exportState.value = NerdModeOpState.Error(it.message ?: "Export failed")
                }
            )
        }
    }

    fun clearPendingShareBytes() {
        _pendingShareBytes.value = null
    }

    fun loadImportFromUri(uri: Uri) {
        val manager = recipeImportExportManager ?: return
        _importState.value = NerdModeOpState.InProgress
        viewModelScope.launch {
            manager.parseAndValidateFromUri(uri).fold(
                onSuccess = { p ->
                    _preview.value = p
                    _importState.value = NerdModeOpState.Idle
                },
                onFailure = {
                    _importState.value = NerdModeOpState.Error(
                        it.message ?: "Could not read or validate file"
                    )
                }
            )
        }
    }

    fun validatePasteImport(json: String) {
        val manager = recipeImportExportManager ?: return
        if (json.isBlank()) {
            _importState.value = NerdModeOpState.Error("Please paste JSON before validating")
            return
        }
        _importState.value = NerdModeOpState.InProgress
        viewModelScope.launch {
            manager.parseAndValidate(json).fold(
                onSuccess = { p ->
                    _preview.value = p
                    _importState.value = NerdModeOpState.Idle
                },
                onFailure = {
                    _importState.value = NerdModeOpState.Error(it.message ?: "Validation failed")
                }
            )
        }
    }

    fun setDuplicateHandling(handling: DuplicateHandling) {
        _duplicateHandling.value = handling
    }

    fun commitImport() {
        val manager = recipeImportExportManager ?: return
        val p = _preview.value ?: return
        _importState.value = NerdModeOpState.InProgress
        viewModelScope.launch {
            runCatching { manager.commitImport(p, _duplicateHandling.value) }.fold(
                onSuccess = { count ->
                    _preview.value = null
                    _importState.value = NerdModeOpState.Success(
                        "Imported $count recipe${if (count != 1) "s" else ""}"
                    )
                },
                onFailure = {
                    _importState.value = NerdModeOpState.Error(it.message ?: "Import failed")
                }
            )
        }
    }

    fun cancelImport() {
        _preview.value = null
        _importState.value = NerdModeOpState.Idle
    }

    fun clearExportState() {
        _exportState.value = NerdModeOpState.Idle
    }

    fun clearImportState() {
        _importState.value = NerdModeOpState.Idle
    }

    fun addIngredient() {
        ingredients = ingredients + RecipeIngredient("", "")
    }

    fun removeIngredient(index: Int) {
        ingredients = ingredients.filterIndexed { i, _ -> i != index }
    }

    fun updateIngredientName(index: Int, value: String) {
        ingredients = ingredients.mapIndexed { i, ing ->
            if (i == index) ing.copy(name = value) else ing
        }
    }

    fun updateIngredientAmount(index: Int, value: String) {
        ingredients = ingredients.mapIndexed { i, ing ->
            if (i == index) ing.copy(amount = value) else ing
        }
    }

    fun save(onSuccess: () -> Unit, onValidationFailed: () -> Unit = {}) {
        if (name.isBlank()) {
            nameError = "Recipe name is required"
            onValidationFailed()
            return
        }

        viewModelScope.launch {
            val encodedIngredients = encodeIngredients(ingredients)
            // Convert display-unit values back to storage base units before persisting
            val storedProteinG    = foodUnit.toG(parseDoubleOrZero(proteinG))
            val storedFatG        = foodUnit.toG(parseDoubleOrZero(fatG))
            val storedTotalCarbsG = foodUnit.toG(parseDoubleOrZero(totalCarbsG))
            val storedFiberG      = foodUnit.toG(parseDoubleOrZero(fiberG))
            val storedNetCarbs    = maxOf(0.0, storedTotalCarbsG - storedFiberG)
            val storedWaterMl     = volUnit.toMl(parseDoubleOrZero(waterMl))

            val storedTags = additionalTags.filter { it != category }.joinToString(",")

            val existing = originalRecipe
            if (existing != null) {
                recipeRepository.updateRecipe(
                    existing.copy(
                        name = name.trim(),
                        category = category,
                        description = description.trim().ifBlank { null },
                        calories = parseDoubleOrZero(calories),
                        proteinG = storedProteinG,
                        fatG = storedFatG,
                        totalCarbsG = storedTotalCarbsG,
                        fiberG = storedFiberG,
                        netCarbsG = storedNetCarbs,
                        sodiumMg = parseDoubleOrZero(sodiumMg),
                        potassiumMg = parseDoubleOrZero(potassiumMg),
                        magnesiumMg = parseDoubleOrZero(magnesiumMg),
                        waterMl = storedWaterMl,
                        ingredientsRaw = encodedIngredients,
                        servings = servings.toDoubleOrNull()?.coerceAtLeast(0.1) ?: 1.0,
                        tags = storedTags
                    )
                )
            } else {
                recipeRepository.insertRecipe(
                    Recipe(
                        name = name.trim(),
                        category = category,
                        description = description.trim().ifBlank { null },
                        calories = parseDoubleOrZero(calories),
                        proteinG = storedProteinG,
                        fatG = storedFatG,
                        totalCarbsG = storedTotalCarbsG,
                        fiberG = storedFiberG,
                        netCarbsG = storedNetCarbs,
                        sodiumMg = parseDoubleOrZero(sodiumMg),
                        potassiumMg = parseDoubleOrZero(potassiumMg),
                        magnesiumMg = parseDoubleOrZero(magnesiumMg),
                        waterMl = storedWaterMl,
                        ingredientsRaw = encodedIngredients,
                        servings = servings.toDoubleOrNull()?.coerceAtLeast(0.1) ?: 1.0,
                        tags = storedTags
                    )
                )
            }
            onSuccess()
        }
    }

    fun deleteRecipe(onSuccess: () -> Unit) {
        val id = editRecipeId ?: return
        viewModelScope.launch {
            recipeRepository.deleteRecipeById(id)
            onSuccess()
        }
    }
}

class AddEditRecipeViewModelFactory(
    private val recipeRepository: RecipeRepository,
    private val editRecipeId: Int? = null,
    private val unitsPreferencesStore: UnitsPreferencesStore? = null,
    private val recipeCategoryStore: RecipeCategoryStore? = null,
    private val recipeImportExportManager: RecipeImportExportManager? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AddEditRecipeViewModel(
            recipeRepository,
            editRecipeId,
            unitsPreferencesStore,
            recipeCategoryStore,
            recipeImportExportManager
        ) as T
}
