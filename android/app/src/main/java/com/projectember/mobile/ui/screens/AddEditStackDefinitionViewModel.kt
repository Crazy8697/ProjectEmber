package com.projectember.mobile.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.barcode.BarcodeProductResult
import com.projectember.mobile.data.local.entities.StackDefinition
import com.projectember.mobile.data.repository.StackDefinitionRepository
import kotlinx.coroutines.launch

class AddEditStackDefinitionViewModel(
    private val stackDefinitionRepository: StackDefinitionRepository,
    private val editDefinitionId: Int? = null,
    initialProductResult: BarcodeProductResult? = null
) : ViewModel() {

    val isEditMode: Boolean get() = editDefinitionId != null

    private var originalDefinition: StackDefinition? = null

    // ── Basic section ─────────────────────────────────────────────────────────
    var name by mutableStateOf("")
        private set
    var defaultDose by mutableStateOf("")
        private set
    var defaultUnit by mutableStateOf("")
        private set

    // ── Barcode ───────────────────────────────────────────────────────────────
    var barcode by mutableStateOf("")
        private set

    // ── Notes ─────────────────────────────────────────────────────────────────
    var notes by mutableStateOf("")
        private set

    // ── Optional Keto / nutrition section ────────────────────────────────────
    var caloriesKcal by mutableStateOf("")
        private set
    var proteinG by mutableStateOf("")
        private set
    var fatG by mutableStateOf("")
        private set
    var netCarbsG by mutableStateOf("")
        private set
    var sodiumMg by mutableStateOf("")
        private set
    var potassiumMg by mutableStateOf("")
        private set
    var magnesiumMg by mutableStateOf("")
        private set

    // ── Validation errors ─────────────────────────────────────────────────────
    var nameError by mutableStateOf<String?>(null)
        private set

    init {
        // Pre-fill from online lookup result when creating a new supplement via barcode scan.
        // If name is blank (NotFound path), only barcode is pre-filled.
        if (initialProductResult != null && editDefinitionId == null) {
            barcode = initialProductResult.barcode
            if (initialProductResult.name.isNotBlank()) {
                val displayName = if (!initialProductResult.brand.isNullOrBlank()) {
                    "${initialProductResult.brand} — ${initialProductResult.name}"
                } else {
                    initialProductResult.name
                }
                name = displayName
                if (!initialProductResult.servingSizeNote.isNullOrBlank()) {
                    defaultDose = initialProductResult.servingSizeNote
                }
                if (initialProductResult.caloriesKcal != null) caloriesKcal = initialProductResult.caloriesKcal.toPlainString()
                if (initialProductResult.proteinG != null) proteinG = initialProductResult.proteinG.toPlainString()
                if (initialProductResult.fatG != null) fatG = initialProductResult.fatG.toPlainString()
                if (initialProductResult.totalCarbsG != null) {
                    val carbs = initialProductResult.totalCarbsG
                    val fiber = initialProductResult.fiberG ?: 0.0
                    netCarbsG = maxOf(0.0, carbs - fiber).toPlainString()
                }
                if (initialProductResult.sodiumMg != null) sodiumMg = initialProductResult.sodiumMg.toPlainString()
            }
        }
        if (editDefinitionId != null) {
            viewModelScope.launch {
                val def = stackDefinitionRepository.getById(editDefinitionId)
                if (def != null) {
                    originalDefinition = def
                    name = def.name
                    defaultDose = def.defaultDose ?: ""
                    defaultUnit = def.defaultUnit ?: ""
                    barcode = def.barcode ?: ""
                    notes = def.notes ?: ""
                    caloriesKcal = def.caloriesKcal?.toPlainString() ?: ""
                    proteinG = def.proteinG?.toPlainString() ?: ""
                    fatG = def.fatG?.toPlainString() ?: ""
                    netCarbsG = def.netCarbsG?.toPlainString() ?: ""
                    sodiumMg = def.sodiumMg?.toPlainString() ?: ""
                    potassiumMg = def.potassiumMg?.toPlainString() ?: ""
                    magnesiumMg = def.magnesiumMg?.toPlainString() ?: ""
                }
            }
        }
    }

    // ── Change handlers ───────────────────────────────────────────────────────
    fun onNameChange(v: String) { name = v; nameError = null }
    fun onDefaultDoseChange(v: String) { defaultDose = v }
    fun onDefaultUnitChange(v: String) { defaultUnit = v }
    fun onBarcodeChange(v: String) { barcode = v }
    fun onNotesChange(v: String) { notes = v }
    fun onCaloriesChange(v: String) { caloriesKcal = v }
    fun onProteinChange(v: String) { proteinG = v }
    fun onFatChange(v: String) { fatG = v }
    fun onNetCarbsChange(v: String) { netCarbsG = v }
    fun onSodiumChange(v: String) { sodiumMg = v }
    fun onPotassiumChange(v: String) { potassiumMg = v }
    fun onMagnesiumChange(v: String) { magnesiumMg = v }

    /** Whether any nutrition field has been filled in. */
    val hasAnyNutrition: Boolean
        get() = listOf(caloriesKcal, proteinG, fatG, netCarbsG, sodiumMg, potassiumMg, magnesiumMg)
            .any { it.trim().isNotEmpty() }

    fun save(onSuccess: () -> Unit, onValidationFailed: () -> Unit = {}) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            nameError = "Stack name is required"
            onValidationFailed()
            return
        }

        val definition = StackDefinition(
            id = originalDefinition?.id ?: 0,
            name = trimmedName,
            defaultDose = defaultDose.trim().takeIf { it.isNotBlank() },
            defaultUnit = defaultUnit.trim().takeIf { it.isNotBlank() },
            barcode = barcode.trim().takeIf { it.isNotBlank() },
            notes = notes.trim().takeIf { it.isNotBlank() },
            caloriesKcal = caloriesKcal.trim().toDoubleOrNull(),
            proteinG = proteinG.trim().toDoubleOrNull(),
            fatG = fatG.trim().toDoubleOrNull(),
            netCarbsG = netCarbsG.trim().toDoubleOrNull(),
            sodiumMg = sodiumMg.trim().toDoubleOrNull(),
            potassiumMg = potassiumMg.trim().toDoubleOrNull(),
            magnesiumMg = magnesiumMg.trim().toDoubleOrNull()
        )

        viewModelScope.launch {
            if (originalDefinition != null) {
                stackDefinitionRepository.update(definition)
            } else {
                stackDefinitionRepository.insert(definition)
            }
            onSuccess()
        }
    }

    fun deleteDefinition(
        supplementRepository: com.projectember.mobile.data.repository.SupplementRepository,
        ketoRepository: com.projectember.mobile.data.repository.KetoRepository,
        onSuccess: () -> Unit
    ) {
        val def = originalDefinition ?: return
        viewModelScope.launch {
            // Delete any linked keto entries for logs of this definition
            val logs = supplementRepository.getByDefinitionIdOnce(def.id)
            logs.forEach { log ->
                log.ketoEntryId?.let { ketoId ->
                    ketoRepository.getEntryById(ketoId)?.let { ketoEntry ->
                        ketoRepository.deleteEntry(ketoEntry)
                    }
                }
            }
            // Delete all logs tied to this definition
            supplementRepository.deleteByDefinitionId(def.id)
            // Delete the definition itself
            stackDefinitionRepository.delete(def)
            onSuccess()
        }
    }
}

/** Format a Double to a plain string without trailing zeros. */
private fun Double.toPlainString(): String =
    toBigDecimal().stripTrailingZeros().toPlainString()

class AddEditStackDefinitionViewModelFactory(
    private val stackDefinitionRepository: StackDefinitionRepository,
    private val editDefinitionId: Int? = null,
    private val initialProductResult: BarcodeProductResult? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AddEditStackDefinitionViewModel(stackDefinitionRepository, editDefinitionId, initialProductResult) as T
}
