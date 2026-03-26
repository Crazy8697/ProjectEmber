package com.projectember.mobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.local.entities.Ingredient
import com.projectember.mobile.data.repository.IngredientRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BarcodeScannerViewModel(
    private val ingredientRepository: IngredientRepository
) : ViewModel() {

    sealed interface ScanState {
        data object Idle : ScanState
        data class Found(val ingredient: Ingredient) : ScanState
        data class NotFound(val barcode: String) : ScanState
    }

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState

    private var isProcessing = false

    fun onBarcodeDetected(barcode: String) {
        if (isProcessing || _scanState.value != ScanState.Idle) return
        isProcessing = true
        viewModelScope.launch {
            val match = ingredientRepository.findByBarcode(barcode)
            _scanState.value = if (match != null) ScanState.Found(match) else ScanState.NotFound(barcode)
            isProcessing = false
        }
    }

    fun reset() {
        _scanState.value = ScanState.Idle
        isProcessing = false
    }
}

class BarcodeScannerViewModelFactory(
    private val ingredientRepository: IngredientRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        BarcodeScannerViewModel(ingredientRepository) as T
}
