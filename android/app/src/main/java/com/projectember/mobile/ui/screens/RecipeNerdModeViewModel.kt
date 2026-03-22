package com.projectember.mobile.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.local.RecipeCategoryStore
import com.projectember.mobile.data.recipe.DuplicateHandling
import com.projectember.mobile.data.recipe.RecipeImportExportManager
import com.projectember.mobile.data.recipe.RecipeImportPreview
import com.projectember.mobile.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** State for a nerd-mode operation (export, import validation, or import commit). */
sealed class NerdModeOpState {
    data object Idle : NerdModeOpState()
    data object InProgress : NerdModeOpState()
    data class Success(val message: String) : NerdModeOpState()
    data class Error(val message: String) : NerdModeOpState()
}

class RecipeNerdModeViewModel(
    private val manager: RecipeImportExportManager,
    private val recipeRepository: RecipeRepository,
    private val recipeCategoryStore: RecipeCategoryStore
) : ViewModel() {

    // ── Export state ──────────────────────────────────────────────────────────

    private val _exportState = MutableStateFlow<NerdModeOpState>(NerdModeOpState.Idle)
    val exportState: StateFlow<NerdModeOpState> = _exportState.asStateFlow()

    /**
     * Bytes of the last requested share-export.  The Screen observes this and launches
     * the share intent when it becomes non-null, then calls [clearPendingShareBytes].
     */
    private val _pendingShareBytes = MutableStateFlow<ByteArray?>(null)
    val pendingShareBytes: StateFlow<ByteArray?> = _pendingShareBytes.asStateFlow()

    // ── Import state ──────────────────────────────────────────────────────────

    private val _importState = MutableStateFlow<NerdModeOpState>(NerdModeOpState.Idle)
    val importState: StateFlow<NerdModeOpState> = _importState.asStateFlow()

    /** The validated import preview waiting for user confirmation. */
    private val _preview = MutableStateFlow<RecipeImportPreview?>(null)
    val preview: StateFlow<RecipeImportPreview?> = _preview.asStateFlow()

    /** How the user wants to handle duplicate recipes during the pending import. */
    private val _duplicateHandling = MutableStateFlow(DuplicateHandling.SKIP)
    val duplicateHandling: StateFlow<DuplicateHandling> = _duplicateHandling.asStateFlow()

    // ── Recipe settings (categories + clear) ────────────────────────────────

    val categories: StateFlow<List<String>> = recipeCategoryStore.categories

    private val _clearRecipesState = MutableStateFlow<NerdModeOpState>(NerdModeOpState.Idle)
    val clearRecipesState: StateFlow<NerdModeOpState> = _clearRecipesState.asStateFlow()

    // ── Export actions ────────────────────────────────────────────────────────

    /** Saves the export JSON directly to the SAF [uri] chosen by the user. */
    fun exportToUri(uri: Uri) {
        _exportState.value = NerdModeOpState.InProgress
        viewModelScope.launch {
            manager.exportToUri(uri).fold(
                onSuccess = {
                    _exportState.value = NerdModeOpState.Success("Recipes exported successfully")
                },
                onFailure = {
                    _exportState.value = NerdModeOpState.Error(
                        it.message ?: "Export failed"
                    )
                }
            )
        }
    }

    /**
     * Builds the export JSON and stores it in [pendingShareBytes] so the Screen can
     * launch a share/email intent.
     */
    fun buildShareExport() {
        _exportState.value = NerdModeOpState.InProgress
        viewModelScope.launch {
            manager.buildExportBytes().fold(
                onSuccess = { bytes ->
                    _pendingShareBytes.value = bytes
                    _exportState.value = NerdModeOpState.Idle
                },
                onFailure = {
                    _exportState.value = NerdModeOpState.Error(
                        it.message ?: "Export failed"
                    )
                }
            )
        }
    }

    fun clearPendingShareBytes() {
        _pendingShareBytes.value = null
    }

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

    fun setDuplicateHandling(handling: DuplicateHandling) {
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
                        "Imported $count recipe${if (count != 1) "s" else ""}"
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

    fun clearExportState() {
        _exportState.value = NerdModeOpState.Idle
    }

    fun clearImportState() {
        _importState.value = NerdModeOpState.Idle
    }

    fun addCategory(name: String) {
        recipeCategoryStore.addCategory(name)
    }

    fun renameCategory(oldName: String, newName: String) {
        recipeCategoryStore.renameCategory(oldName, newName)
    }

    fun deleteCategory(name: String) {
        recipeCategoryStore.deleteCategory(name)
    }

    fun clearRecipes() {
        _clearRecipesState.value = NerdModeOpState.InProgress
        viewModelScope.launch {
            runCatching { recipeRepository.replaceAll(emptyList()) }.fold(
                onSuccess = {
                    _clearRecipesState.value = NerdModeOpState.Success(
                        "Recipe book cleared. Keto logs were not changed."
                    )
                },
                onFailure = {
                    _clearRecipesState.value = NerdModeOpState.Error(
                        it.message ?: "Failed to clear recipes"
                    )
                }
            )
        }
    }

    fun clearClearRecipesState() {
        _clearRecipesState.value = NerdModeOpState.Idle
    }
}

class RecipeNerdModeViewModelFactory(
    private val manager: RecipeImportExportManager,
    private val recipeRepository: RecipeRepository,
    private val recipeCategoryStore: RecipeCategoryStore
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        RecipeNerdModeViewModel(manager, recipeRepository, recipeCategoryStore) as T
}
