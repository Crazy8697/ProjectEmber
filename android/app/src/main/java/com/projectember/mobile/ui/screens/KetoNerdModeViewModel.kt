package com.projectember.mobile.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.keto.KetoDuplicateHandling
import com.projectember.mobile.data.keto.KetoImportManager
import com.projectember.mobile.data.keto.KetoImportPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class KetoNerdModeViewModel(
    private val manager: KetoImportManager
) : ViewModel() {

    // ── Import state ──────────────────────────────────────────────────────────

    private val _importState = MutableStateFlow<NerdModeOpState>(NerdModeOpState.Idle)
    val importState: StateFlow<NerdModeOpState> = _importState.asStateFlow()

    /** The validated import preview waiting for user confirmation. */
    private val _preview = MutableStateFlow<KetoImportPreview?>(null)
    val preview: StateFlow<KetoImportPreview?> = _preview.asStateFlow()

    /** How the user wants to handle duplicate entries during the pending import. */
    private val _duplicateHandling = MutableStateFlow(KetoDuplicateHandling.SKIP)
    val duplicateHandling: StateFlow<KetoDuplicateHandling> = _duplicateHandling.asStateFlow()

    // ── Import actions ────────────────────────────────────────────────────────

    /** Reads and validates the JSON file at [uri], then populates [preview]. */
    fun loadImportFromUri(uri: Uri) {
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

    /** Validates [json] (paste input) and populates [preview]. */
    fun validatePasteImport(json: String) {
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
                    _importState.value = NerdModeOpState.Error(
                        it.message ?: "Validation failed"
                    )
                }
            )
        }
    }

    fun setDuplicateHandling(handling: KetoDuplicateHandling) {
        _duplicateHandling.value = handling
    }

    /**
     * Commits the [preview] import using the selected [duplicateHandling] option.
     * Clears the preview on success.
     */
    fun commitImport() {
        val p = _preview.value ?: return
        _importState.value = NerdModeOpState.InProgress
        viewModelScope.launch {
            runCatching { manager.commitImport(p, _duplicateHandling.value) }.fold(
                onSuccess = { count ->
                    _preview.value = null
                    _importState.value = NerdModeOpState.Success(
                        "Imported $count entr${if (count != 1) "ies" else "y"}"
                    )
                },
                onFailure = {
                    _importState.value = NerdModeOpState.Error(
                        it.message ?: "Import failed"
                    )
                }
            )
        }
    }

    /** Discards the pending import preview without writing any data. */
    fun cancelImport() {
        _preview.value = null
        _importState.value = NerdModeOpState.Idle
    }

    fun clearImportState() {
        _importState.value = NerdModeOpState.Idle
    }
}

class KetoNerdModeViewModelFactory(
    private val manager: KetoImportManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        KetoNerdModeViewModel(manager) as T
}
