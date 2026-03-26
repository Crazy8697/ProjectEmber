package com.projectember.mobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.barcode.BarcodeProductLookupRepository
import com.projectember.mobile.data.barcode.BarcodeProductResult
import com.projectember.mobile.data.local.entities.Ingredient
import com.projectember.mobile.data.local.entities.StackDefinition
import com.projectember.mobile.data.repository.IngredientRepository
import com.projectember.mobile.data.repository.StackDefinitionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BarcodeScannerViewModel(
    private val ingredientRepository: IngredientRepository,
    private val stackDefinitionRepository: StackDefinitionRepository,
    private val lookupRepository: BarcodeProductLookupRepository
) : ViewModel() {

    sealed interface ScanState {
        data object Idle : ScanState
        /** Barcode matched a local Ingredient record. */
        data class FoundIngredient(val ingredient: Ingredient) : ScanState
        /** Barcode matched a local StackDefinition (supplement) record. */
        data class FoundSupplement(val definition: StackDefinition) : ScanState
        /** No local match — online lookup returned a usable product. */
        data class OnlineLookupResult(val result: BarcodeProductResult) : ScanState
        /** No local match and online lookup found nothing (or failed). */
        data class NotFound(val barcode: String) : ScanState
    }

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState

    private var isProcessing = false

    fun onBarcodeDetected(barcode: String) {
        if (isProcessing || _scanState.value != ScanState.Idle) return
        isProcessing = true
        viewModelScope.launch {
            // 1. Local ingredient check
            val ingredient = ingredientRepository.findByBarcode(barcode)
            if (ingredient != null) {
                _scanState.value = ScanState.FoundIngredient(ingredient)
                isProcessing = false
                return@launch
            }
            // 2. Local supplement (stack definition) check
            val definition = stackDefinitionRepository.findByBarcode(barcode)
            if (definition != null) {
                _scanState.value = ScanState.FoundSupplement(definition)
                isProcessing = false
                return@launch
            }
            // 3. Online lookup — only reached when barcode is unknown locally
            val online = lookupRepository.lookup(barcode)
            _scanState.value = if (online != null) {
                ScanState.OnlineLookupResult(online)
            } else {
                ScanState.NotFound(barcode)
            }
            isProcessing = false
        }
    }

    fun reset() {
        _scanState.value = ScanState.Idle
        isProcessing = false
    }
}

class BarcodeScannerViewModelFactory(
    private val ingredientRepository: IngredientRepository,
    private val stackDefinitionRepository: StackDefinitionRepository,
    private val lookupRepository: BarcodeProductLookupRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        BarcodeScannerViewModel(ingredientRepository, stackDefinitionRepository, lookupRepository) as T
}
