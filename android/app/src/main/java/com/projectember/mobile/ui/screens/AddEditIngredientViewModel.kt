package com.projectember.mobile.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.barcode.BarcodeProductResult
import com.projectember.mobile.data.barcode.NameNormalizer
import com.projectember.mobile.data.label.NutritionParseResult
import com.projectember.mobile.data.local.entities.Ingredient
import com.projectember.mobile.data.repository.IngredientRepository
import kotlinx.coroutines.launch

class AddEditIngredientViewModel(
    private val ingredientRepository: IngredientRepository,
    private val editIngredientId: Int? = null,
    initialBarcode: String? = null,
    initialProductResult: BarcodeProductResult? = null,
    /**
     * When true (edit mode with online data), pre-fill nutrition fields that are currently
     * zero with values from [initialProductResult]. Does not overwrite existing non-zero values.
     */
    private val mergeOnlineData: Boolean = false,
    /** PR73: pre-fill from a label OCR scan result. Only applied in create mode. */
    initialLabelResult: NutritionParseResult? = null
) : ViewModel() {

    val isEditMode: Boolean get() = editIngredientId != null
    private var original: Ingredient? = null

    var name by mutableStateOf("")
        private set
    var defaultAmount by mutableStateOf("100")
        private set
    var defaultUnit by mutableStateOf("g")
        private set

    // Nutrition fields — stored in base units (g, mg, ml)
    var calories by mutableStateOf("")
        private set
    var proteinG by mutableStateOf("")
        private set
    var fatG by mutableStateOf("")
        private set
    var totalCarbsG by mutableStateOf("")
        private set
    var fiberG by mutableStateOf("")
        private set
    var sodiumMg by mutableStateOf("")
        private set
    var potassiumMg by mutableStateOf("")
        private set
    var magnesiumMg by mutableStateOf("")
        private set
    var waterMl by mutableStateOf("")
        private set
    var barcode by mutableStateOf(initialBarcode ?: "")
        private set

    var nameError by mutableStateOf<String?>(null)
        private set
    var defaultAmountError by mutableStateOf<String?>(null)
        private set

    init {
        // Pre-fill from online lookup result (takes priority over bare initialBarcode)
        if (initialProductResult != null && editIngredientId == null) {
            val cleanedName = NameNormalizer.cleanProductName(
                if (!initialProductResult.brand.isNullOrBlank()) {
                    "${initialProductResult.brand} — ${initialProductResult.name}"
                } else {
                    initialProductResult.name
                }
            )
            name = cleanedName
            barcode = initialProductResult.barcode
            if (initialProductResult.caloriesKcal != null) calories = fmt(initialProductResult.caloriesKcal)
            if (initialProductResult.proteinG != null) proteinG = fmt(initialProductResult.proteinG)
            if (initialProductResult.fatG != null) fatG = fmt(initialProductResult.fatG)
            if (initialProductResult.totalCarbsG != null) totalCarbsG = fmt(initialProductResult.totalCarbsG)
            if (initialProductResult.fiberG != null) fiberG = fmt(initialProductResult.fiberG)
            if (initialProductResult.sodiumMg != null) sodiumMg = fmt(initialProductResult.sodiumMg)
        }
        // PR73: Pre-fill from label OCR scan (create mode only, label-first nutrition path)
        if (initialLabelResult != null && editIngredientId == null) {
            if (initialLabelResult.servingAmount != null) {
                defaultAmount = fmt(initialLabelResult.servingAmount)
                defaultUnit = initialLabelResult.servingUnit ?: "g"
            }
            if (initialLabelResult.calories != null) calories = fmt(initialLabelResult.calories)
            if (initialLabelResult.proteinG != null) proteinG = fmt(initialLabelResult.proteinG)
            if (initialLabelResult.fatG != null) fatG = fmt(initialLabelResult.fatG)
            if (initialLabelResult.totalCarbsG != null) totalCarbsG = fmt(initialLabelResult.totalCarbsG)
            if (initialLabelResult.fiberG != null) fiberG = fmt(initialLabelResult.fiberG)
            if (initialLabelResult.sodiumMg != null) sodiumMg = fmt(initialLabelResult.sodiumMg)
            if (initialLabelResult.potassiumMg != null) potassiumMg = fmt(initialLabelResult.potassiumMg)
            if (initialLabelResult.magnesiumMg != null) magnesiumMg = fmt(initialLabelResult.magnesiumMg)
            android.util.Log.d("AddEditIngredient", "LABEL_DRAFT_CREATED from OCR parse result")
        }

        if (editIngredientId != null) {
            viewModelScope.launch {
                val ing = ingredientRepository.getById(editIngredientId) ?: return@launch
                original = ing
                name = ing.name
                defaultAmount = fmt(ing.defaultAmount)
                defaultUnit = ing.defaultUnit
                calories = fmt(ing.calories)
                proteinG = fmt(ing.proteinG)
                fatG = fmt(ing.fatG)
                totalCarbsG = fmt(ing.totalCarbsG)
                fiberG = fmt(ing.fiberG)
                sodiumMg = fmt(ing.sodiumMg)
                potassiumMg = fmt(ing.potassiumMg)
                magnesiumMg = fmt(ing.magnesiumMg)
                waterMl = fmt(ing.waterMl)
                barcode = ing.barcode ?: ""

                // Merge online data into empty/zero fields only (does not overwrite existing values)
                if (mergeOnlineData && initialProductResult != null) {
                    if (ing.calories == 0.0 && initialProductResult.caloriesKcal != null)
                        calories = fmt(initialProductResult.caloriesKcal)
                    if (ing.proteinG == 0.0 && initialProductResult.proteinG != null)
                        proteinG = fmt(initialProductResult.proteinG)
                    if (ing.fatG == 0.0 && initialProductResult.fatG != null)
                        fatG = fmt(initialProductResult.fatG)
                    if (ing.totalCarbsG == 0.0 && initialProductResult.totalCarbsG != null)
                        totalCarbsG = fmt(initialProductResult.totalCarbsG)
                    if (ing.fiberG == 0.0 && initialProductResult.fiberG != null)
                        fiberG = fmt(initialProductResult.fiberG)
                    if (ing.sodiumMg == 0.0 && initialProductResult.sodiumMg != null)
                        sodiumMg = fmt(initialProductResult.sodiumMg)
                    // Store barcode if ingredient didn't have one
                    if (ing.barcode.isNullOrBlank() && initialProductResult.barcode.isNotBlank())
                        barcode = initialProductResult.barcode
                }
            }
        }
    }

    fun onNameChange(v: String) { name = v; nameError = null }
    fun onDefaultAmountChange(v: String) { defaultAmount = v; defaultAmountError = null }
    fun onDefaultUnitChange(v: String) { defaultUnit = v }
    fun onCaloriesChange(v: String) { calories = v }
    fun onProteinGChange(v: String) { proteinG = v }
    fun onFatGChange(v: String) { fatG = v }
    fun onTotalCarbsGChange(v: String) { totalCarbsG = v }
    fun onFiberGChange(v: String) { fiberG = v }
    fun onSodiumMgChange(v: String) { sodiumMg = v }
    fun onPotassiumMgChange(v: String) { potassiumMg = v }
    fun onMagnesiumMgChange(v: String) { magnesiumMg = v }
    fun onWaterMlChange(v: String) { waterMl = v }
    fun onBarcodeChange(v: String) { barcode = v }

    fun save(onSuccess: () -> Unit, onValidationFailed: () -> Unit = {}) {
        var valid = true
        if (name.isBlank()) { nameError = "Name is required"; valid = false }
        val parsedAmount = defaultAmount.toDoubleOrNull()
        if (parsedAmount == null || parsedAmount <= 0.0) {
            defaultAmountError = "Enter a positive number"
            valid = false
        }
        if (!valid) { onValidationFailed(); return }

        viewModelScope.launch {
            val totalC = dbl(totalCarbsG)
            val fiber = dbl(fiberG)
            val netC = maxOf(0.0, totalC - fiber)
            val trimmedName = name.trim()
            val ing = Ingredient(
                id = original?.id ?: 0,
                name = trimmedName,
                normalizedName = NameNormalizer.normalize(trimmedName),
                defaultAmount = parsedAmount!!,
                defaultUnit = defaultUnit.trim().ifBlank { "g" },
                calories = dbl(calories),
                proteinG = dbl(proteinG),
                fatG = dbl(fatG),
                netCarbsG = netC,
                totalCarbsG = totalC,
                fiberG = fiber,
                sodiumMg = dbl(sodiumMg),
                potassiumMg = dbl(potassiumMg),
                magnesiumMg = dbl(magnesiumMg),
                waterMl = dbl(waterMl),
                isBuiltIn = original?.isBuiltIn ?: false,
                barcode = barcode.trim().ifBlank { null }
            )
            if (original != null) ingredientRepository.update(ing)
            else ingredientRepository.insert(ing)
            onSuccess()
        }
    }

    fun deleteIngredient(onSuccess: () -> Unit) {
        val ing = original ?: return
        viewModelScope.launch {
            ingredientRepository.delete(ing)
            onSuccess()
        }
    }

    private fun fmt(d: Double): String =
        if (d == 0.0) "0" else d.toBigDecimal().stripTrailingZeros().toPlainString()

    private fun dbl(s: String): Double = s.toDoubleOrNull() ?: 0.0
}

class AddEditIngredientViewModelFactory(
    private val ingredientRepository: IngredientRepository,
    private val editIngredientId: Int? = null,
    private val initialBarcode: String? = null,
    private val initialProductResult: BarcodeProductResult? = null,
    private val mergeOnlineData: Boolean = false,
    private val initialLabelResult: NutritionParseResult? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AddEditIngredientViewModel(
            ingredientRepository, editIngredientId, initialBarcode,
            initialProductResult, mergeOnlineData, initialLabelResult
        ) as T
}
