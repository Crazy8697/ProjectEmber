package com.projectember.mobile.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.backup.BackupManager
import com.projectember.mobile.data.backup.BackupPayloadV1
import com.projectember.mobile.data.local.FoodWeightUnit
import com.projectember.mobile.data.local.ThemePreferencesStore
import com.projectember.mobile.data.local.UnitPreferences
import com.projectember.mobile.data.local.UnitsPreferencesStore
import com.projectember.mobile.data.local.VolumeUnit
import com.projectember.mobile.data.local.WeightUnit
import com.projectember.mobile.data.local.entities.SyncStatus
import com.projectember.mobile.data.repository.SyncRepository
import com.projectember.mobile.sync.HealthConnectAvailability
import com.projectember.mobile.sync.HealthConnectManager
import com.projectember.mobile.sync.HealthConnectUiState
import com.projectember.mobile.sync.SyncManager
import androidx.health.connect.client.HealthConnectClient as HCClient
import com.projectember.mobile.ui.theme.ThemeOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State for a backup operation (export, import, or reset). */
sealed class BackupOpState {
    data object Idle : BackupOpState()
    data object InProgress : BackupOpState()
    data class Success(val message: String) : BackupOpState()
    data class Error(val message: String) : BackupOpState()
}

class SettingsViewModel(
    private val syncRepository: SyncRepository,
    private val syncManager: SyncManager,
    private val healthConnectManager: HealthConnectManager,
    private val backupManager: BackupManager,
    private val themePreferencesStore: ThemePreferencesStore,
    private val unitsPreferencesStore: UnitsPreferencesStore
) : ViewModel() {

    // ── Health Connect state ──────────────────────────────────────────────────

    private val _healthConnectState = MutableStateFlow(
        HealthConnectUiState(requiredPermissions = HealthConnectManager.REQUIRED_PERMISSIONS)
    )
    val healthConnectState: StateFlow<HealthConnectUiState> = _healthConnectState.asStateFlow()

    /** Call when the Settings screen becomes visible to refresh HC availability/permission state. */
    fun checkHealthConnectStatus() {
        viewModelScope.launch {
            val sdkStatus = healthConnectManager.getSdkStatus()
            val availability = when (sdkStatus) {
                HCClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
                HCClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                    HealthConnectAvailability.NOT_INSTALLED
                else -> HealthConnectAvailability.NOT_SUPPORTED
            }
            val permissionsGranted = if (availability == HealthConnectAvailability.AVAILABLE) {
                runCatching { healthConnectManager.hasAllPermissions() }.getOrElse { false }
            } else {
                false
            }
            _healthConnectState.value = _healthConnectState.value.copy(
                availability = availability,
                permissionsGranted = permissionsGranted,
            )
        }
    }

    /** Called by the screen after the system permission dialog closes. */
    fun onPermissionsResult(grantedPermissions: Set<String>) {
        val allGranted = grantedPermissions.containsAll(HealthConnectManager.REQUIRED_PERMISSIONS)
        _healthConnectState.value = _healthConnectState.value.copy(
            permissionsGranted = allGranted,
            errorMessage = if (!allGranted) "Some permissions were not granted." else null,
        )
    }

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
            _healthConnectState.value = _healthConnectState.value.copy(
                syncing = true,
                errorMessage = null,
            )
            try {
                syncManager.triggerManualSync()
            } finally {
                _isSyncing.value = false
                _healthConnectState.value = _healthConnectState.value.copy(syncing = false)
            }
            // Refresh permissions/availability after sync attempt
            checkHealthConnectStatus()
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

    // ── Email Export ──────────────────────────────────────────────────────────

    /** Non-null when backup bytes are ready to be delivered via email/share. */
    private val _pendingEmailExport = MutableStateFlow<ByteArray?>(null)
    val pendingEmailExport: StateFlow<ByteArray?> = _pendingEmailExport.asStateFlow()

    /** Builds the backup payload and stages it for the UI to launch an email intent. */
    fun prepareEmailExport() {
        viewModelScope.launch {
            _exportState.value = BackupOpState.InProgress
            backupManager.buildBackupBytes()
                .onSuccess { bytes ->
                    _pendingEmailExport.value = bytes
                    _exportState.value = BackupOpState.Idle
                }
                .onFailure { e ->
                    _exportState.value = BackupOpState.Error(
                        e.message ?: "Export failed. Please try again."
                    )
                }
        }
    }

    fun clearPendingEmailExport() { _pendingEmailExport.value = null }

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

    // ── Theme ─────────────────────────────────────────────────────────────────

    private val _selectedTheme = MutableStateFlow(themePreferencesStore.getTheme())
    val selectedTheme: StateFlow<ThemeOption> = _selectedTheme.asStateFlow()

    fun setTheme(theme: ThemeOption) {
        themePreferencesStore.setTheme(theme)
        _selectedTheme.value = theme
    }

    // ── Unit Preferences ──────────────────────────────────────────────────────

    private val _unitPreferences = MutableStateFlow(unitsPreferencesStore.getPreferences())
    val unitPreferences: StateFlow<UnitPreferences> = _unitPreferences.asStateFlow()

    fun setWeightUnit(unit: WeightUnit) {
        unitsPreferencesStore.setWeightUnit(unit)
        _unitPreferences.value = _unitPreferences.value.copy(weightUnit = unit)
    }

    fun setFoodWeightUnit(unit: FoodWeightUnit) {
        unitsPreferencesStore.setFoodWeightUnit(unit)
        _unitPreferences.value = _unitPreferences.value.copy(foodWeightUnit = unit)
    }

    fun setVolumeUnit(unit: VolumeUnit) {
        unitsPreferencesStore.setVolumeUnit(unit)
        _unitPreferences.value = _unitPreferences.value.copy(volumeUnit = unit)
    }
}

class SettingsViewModelFactory(
    private val syncRepository: SyncRepository,
    private val syncManager: SyncManager,
    private val healthConnectManager: HealthConnectManager,
    private val backupManager: BackupManager,
    private val themePreferencesStore: ThemePreferencesStore,
    private val unitsPreferencesStore: UnitsPreferencesStore
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SettingsViewModel(
            syncRepository,
            syncManager,
            healthConnectManager,
            backupManager,
            themePreferencesStore,
            unitsPreferencesStore
        ) as T
}
