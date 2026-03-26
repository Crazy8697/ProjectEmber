package com.projectember.mobile.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.barcode.BarcodeProductLookupRepository
import com.projectember.mobile.data.barcode.BarcodeProductResult
import com.projectember.mobile.data.barcode.NameNormalizer
import com.projectember.mobile.data.local.entities.Ingredient
import com.projectember.mobile.data.local.entities.StackDefinition
import com.projectember.mobile.data.repository.IngredientRepository
import com.projectember.mobile.data.repository.StackDefinitionRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val TAG = "BarcodeScanner"

class BarcodeScannerViewModel(
    private val ingredientRepository: IngredientRepository,
    private val stackDefinitionRepository: StackDefinitionRepository,
    private val lookupRepository: BarcodeProductLookupRepository
) : ViewModel() {

    sealed interface ScanState {
        data object Idle : ScanState

        /**
         * Barcode matched a local Ingredient record.
         * [onlineData] is non-null when the online source has values missing locally —
         * the UI can offer a "Update Values" merge option in that case.
         */
        data class FoundIngredient(
            val ingredient: Ingredient,
            val onlineData: BarcodeProductResult? = null
        ) : ScanState

        /** Barcode matched a local StackDefinition (supplement) record. */
        data class FoundSupplement(val definition: StackDefinition) : ScanState

        /**
         * No barcode match locally, but online lookup returned a product.
         * [nameMatch] is set when a local ingredient with a matching normalized name already
         * exists — the UI should offer "Link to Existing" before "Create New".
         */
        data class OnlineLookupResult(
            val result: BarcodeProductResult,
            val nameMatch: Ingredient? = null
        ) : ScanState

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
            // Run local DB lookups and online lookup concurrently to minimise latency.
            val ingredientJob = async { ingredientRepository.findByBarcode(barcode) }
            val supplementJob = async { stackDefinitionRepository.findByBarcode(barcode) }
            val onlineJob    = async { lookupRepository.lookup(barcode) }

            val ingredient = ingredientJob.await()
            val supplement = supplementJob.await()
            val online     = onlineJob.await()

            when {
                ingredient != null -> {
                    Log.d(TAG, "BARCODE_MATCH_FOUND: id=${ingredient.id} name=${ingredient.name}")
                    val betterData = if (online != null && hasBetterData(ingredient, online)) {
                        Log.d(TAG, "BARCODE_MATCH_FOUND: online data available for merge")
                        online
                    } else null
                    _scanState.value = ScanState.FoundIngredient(ingredient, betterData)
                }

                supplement != null -> {
                    Log.d(TAG, "BARCODE_MATCH_FOUND: supplement id=${supplement.id} name=${supplement.name}")
                    _scanState.value = ScanState.FoundSupplement(supplement)
                }

                online != null -> {
                    val normalizedOnlineName = NameNormalizer.normalize(online.name)
                    val nameMatch = ingredientRepository.findByNormalizedName(normalizedOnlineName)
                    if (nameMatch != null) {
                        Log.d(TAG, "NAME_MATCH_FOUND: id=${nameMatch.id} name=${nameMatch.name}")
                        _scanState.value = ScanState.OnlineLookupResult(online, nameMatch)
                    } else {
                        Log.d(TAG, "NEW_INGREDIENT_CREATED: barcode=$barcode name=${online.name}")
                        _scanState.value = ScanState.OnlineLookupResult(online, null)
                    }
                }

                else -> {
                    Log.d(TAG, "NEW_INGREDIENT_CREATED: barcode=$barcode (no online data)")
                    _scanState.value = ScanState.NotFound(barcode)
                }
            }
            isProcessing = false
        }
    }

    /**
     * Link a scanned barcode to an existing ingredient that matched by name.
     * Updates the ingredient's barcode field so future scans resolve instantly,
     * then calls [onDone] with the ingredient id for navigation.
     */
    fun linkBarcodeToIngredient(barcode: String, ingredientId: Int, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val existing = ingredientRepository.getById(ingredientId) ?: return@launch
            ingredientRepository.update(existing.copy(barcode = barcode))
            Log.d(TAG, "BARCODE_MATCH_FOUND: barcode linked to existing id=$ingredientId")
            onDone(ingredientId)
        }
    }

    /**
     * Log duplicate prevention. The state stays on FoundIngredient so the UI can present
     * the reuse options; caller drives navigation.
     */
    fun onDuplicateBlocked(ingredient: Ingredient) {
        Log.d(TAG, "DUPLICATE_BLOCKED: barcode=${ingredient.barcode} name=${ingredient.name}")
    }

    fun reset() {
        _scanState.value = ScanState.Idle
        isProcessing = false
    }

    /**
     * Returns true if [online] has at least one nutrition field that [existing] stores as zero,
     * indicating that the online source can enrich the local record.
     */
    private fun hasBetterData(existing: Ingredient, online: BarcodeProductResult): Boolean =
        (online.caloriesKcal != null && existing.calories == 0.0) ||
        (online.proteinG    != null && existing.proteinG  == 0.0) ||
        (online.fatG        != null && existing.fatG      == 0.0) ||
        (online.totalCarbsG != null && existing.totalCarbsG == 0.0) ||
        (online.sodiumMg    != null && existing.sodiumMg  == 0.0)
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
