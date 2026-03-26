package com.projectember.mobile.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.local.entities.Ingredient
import com.projectember.mobile.data.repository.IngredientRepository
import kotlinx.coroutines.launch

class AddEditIngredientViewModel(
    private val ingredientRepository: IngredientRepository,
    private val editIngredientId: Int? = null,
    initialBarcode: String? = null
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
            val ing = Ingredient(
                id = original?.id ?: 0,
                name = name.trim(),
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
        if (d == 0.0) "" else d.toBigDecimal().stripTrailingZeros().toPlainString()

    private fun dbl(s: String): Double = s.toDoubleOrNull() ?: 0.0
}

class AddEditIngredientViewModelFactory(
    private val ingredientRepository: IngredientRepository,
    private val editIngredientId: Int? = null,
    private val initialBarcode: String? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AddEditIngredientViewModel(ingredientRepository, editIngredientId, initialBarcode) as T
}
