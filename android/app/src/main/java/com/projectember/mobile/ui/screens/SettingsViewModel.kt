package com.projectember.mobile.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.backup.BackupManager
import com.projectember.mobile.data.backup.BackupPayloadV1
import com.projectember.mobile.data.local.entities.SyncStatus
import com.projectember.mobile.data.repository.SyncRepository
import com.projectember.mobile.sync.SyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State for a backup operation (export, import, or reset). */
sealed class BackupOpState {
    object Idle : BackupOpState()
    object InProgress : BackupOpState()
    data class Success(val message: String) : BackupOpState()
    data class Error(val message: String) : BackupOpState()
}

class SettingsViewModel(
    private val syncRepository: SyncRepository,
    private val syncManager: SyncManager,
    private val backupManager: BackupManager
) : ViewModel() {

    // ── Sync ──────────────────────────────────────────────────────────────────

    val syncStatus: StateFlow<SyncStatus?> = syncRepository
        .getSyncStatus()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    fun triggerSync() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            syncManager.triggerManualSync()
            _isSyncing.value = false
        }
    }

    // ── Export ────────────────────────────────────────────────────────────────

    private val _exportState = MutableStateFlow<BackupOpState>(BackupOpState.Idle)
    val exportState: StateFlow<BackupOpState> = _exportState.asStateFlow()

    fun exportToUri(uri: Uri) {
        viewModelScope.launch {
            _exportState.value = BackupOpState.InProgress
            backupManager.exportToUri(uri)
                .onSuccess { _exportState.value = BackupOpState.Success("Backup exported successfully.") }
                .onFailure { e ->
                    _exportState.value = BackupOpState.Error(
                        e.message ?: "Export failed. Please try again."
                    )
                }
        }
    }

    fun clearExportState() { _exportState.value = BackupOpState.Idle }

    // ── Import ────────────────────────────────────────────────────────────────

    private val _importState = MutableStateFlow<BackupOpState>(BackupOpState.Idle)
    val importState: StateFlow<BackupOpState> = _importState.asStateFlow()

    /** Validated payload pending user overwrite confirmation. */
    private val _pendingImport = MutableStateFlow<BackupPayloadV1?>(null)
    val pendingImport: StateFlow<BackupPayloadV1?> = _pendingImport.asStateFlow()

    /** Read and validate the backup file; if valid, set [pendingImport] so the UI
     *  can show the overwrite-confirmation dialog. */
    fun loadImport(uri: Uri) {
        viewModelScope.launch {
            _importState.value = BackupOpState.InProgress
            backupManager.readAndValidateImport(uri)
                .onSuccess { payload ->
                    _pendingImport.value = payload
                    _importState.value = BackupOpState.Idle   // UI shows confirm dialog
                }
                .onFailure { e ->
                    _importState.value = BackupOpState.Error(
                        e.message ?: "Import failed. The file may be invalid or corrupted."
                    )
                }
        }
    }

    /** User confirmed the overwrite — perform the destructive restore. */
    fun confirmImport() {
        val payload = _pendingImport.value ?: return
        viewModelScope.launch {
            _importState.value = BackupOpState.InProgress
            runCatching { backupManager.restoreFromPayload(payload) }
                .onSuccess {
                    _pendingImport.value = null
                    _importState.value = BackupOpState.Success("Data restored successfully.")
                }
                .onFailure { e ->
                    _pendingImport.value = null
                    _importState.value = BackupOpState.Error(
                        e.message ?: "Restore failed. No data was changed."
                    )
                }
        }
    }

    fun cancelImport() {
        _pendingImport.value = null
        _importState.value = BackupOpState.Idle
    }

    fun clearImportState() { _importState.value = BackupOpState.Idle }

    // ── Reset ─────────────────────────────────────────────────────────────────

    private val _resetState = MutableStateFlow<BackupOpState>(BackupOpState.Idle)
    val resetState: StateFlow<BackupOpState> = _resetState.asStateFlow()

    fun resetAll() {
        viewModelScope.launch {
            _resetState.value = BackupOpState.InProgress
            runCatching { backupManager.resetAll() }
                .onSuccess { _resetState.value = BackupOpState.Success("App data has been reset.") }
                .onFailure { e ->
                    _resetState.value = BackupOpState.Error(
                        e.message ?: "Reset failed. Please try again."
                    )
                }
        }
    }

    fun clearResetState() { _resetState.value = BackupOpState.Idle }
}

class SettingsViewModelFactory(
    private val syncRepository: SyncRepository,
    private val syncManager: SyncManager,
    private val backupManager: BackupManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SettingsViewModel(syncRepository, syncManager, backupManager) as T
}
