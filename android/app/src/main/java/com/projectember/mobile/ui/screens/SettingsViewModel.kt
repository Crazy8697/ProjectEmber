package com.projectember.mobile.ui.screens

import android.net.Uri
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.backup.BackupManager
import com.projectember.mobile.data.backup.BackupPayloadV1
import com.projectember.mobile.data.backup.NightlyBackupEngine
import com.projectember.mobile.data.backup.NightlyBackupStore
import com.projectember.mobile.data.local.DailyRhythm
import com.projectember.mobile.data.local.DailyRhythmStore
import com.projectember.mobile.data.local.EatingStyle
import com.projectember.mobile.data.local.FoodWeightUnit
import com.projectember.mobile.data.local.HealthMetric
import com.projectember.mobile.data.local.HealthMetricPreferencesStore
import com.projectember.mobile.data.local.MealTiming
import com.projectember.mobile.data.local.MealTimingStore
import com.projectember.mobile.data.local.MealWindow
import com.projectember.mobile.data.local.ThemePreferencesStore
import com.projectember.mobile.data.local.UnitPreferences
import com.projectember.mobile.data.local.UnitsPreferencesStore
import com.projectember.mobile.data.local.VolumeUnit
import com.projectember.mobile.data.local.WeightUnit
import com.projectember.mobile.data.local.entities.SyncStatus
import com.projectember.mobile.data.repository.SyncRepository
import com.projectember.mobile.data.repository.KetoRepository
import com.projectember.mobile.data.repository.RecipeRepository
import com.projectember.mobile.sync.HealthConnectManager
import com.projectember.mobile.sync.HealthConnectUiState
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
    private val ketoRepository: KetoRepository,
    private val recipeRepository: RecipeRepository,
    private val healthConnectManager: HealthConnectManager,
    private val backupManager: BackupManager,
    private val themePreferencesStore: ThemePreferencesStore,
    private val unitsPreferencesStore: UnitsPreferencesStore,
    private val dailyRhythmStore: DailyRhythmStore,
    private val mealTimingStore: MealTimingStore,
    private val healthMetricPreferencesStore: HealthMetricPreferencesStore,
    private val nightlyBackupStore: NightlyBackupStore,
    private val nightlyBackupEngine: NightlyBackupEngine,
) : ViewModel() {

    // ── Health Connect state ──────────────────────────────────────────────────

    /** The permission set required for Health Connect sync — passed to the permission launcher. */
    val healthConnectPermissions: Set<String> = healthConnectManager.requiredPermissions

    private val _healthConnectState =
        MutableStateFlow<HealthConnectUiState>(HealthConnectUiState.Checking)
    val healthConnectState: StateFlow<HealthConnectUiState> = _healthConnectState.asStateFlow()

    private fun permissionForMetric(metric: HealthMetric): Set<String> = when (metric) {
        HealthMetric.WEIGHT -> setOf(
            HealthPermission.getReadPermission(WeightRecord::class)
        )
        HealthMetric.STEPS -> setOf(
            HealthPermission.getReadPermission(StepsRecord::class)
        )
        HealthMetric.DISTANCE -> setOf(
            HealthPermission.getReadPermission(DistanceRecord::class)
        )
        HealthMetric.ACTIVE_CALORIES -> setOf(
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
        )
        HealthMetric.EXERCISE_SESSIONS -> setOf(
            HealthPermission.getReadPermission(ExerciseSessionRecord::class)
        )
        HealthMetric.HEART_RATE -> setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class)
        )
        HealthMetric.RESTING_HEART_RATE -> setOf(
            HealthPermission.getReadPermission(RestingHeartRateRecord::class)
        )
        HealthMetric.SLEEP -> setOf(
            HealthPermission.getReadPermission(SleepSessionRecord::class)
        )
        HealthMetric.BLOOD_PRESSURE -> setOf(
            HealthPermission.getReadPermission(BloodPressureRecord::class)
        )
        HealthMetric.BLOOD_GLUCOSE -> setOf(
            HealthPermission.getReadPermission(BloodGlucoseRecord::class)
        )
        HealthMetric.BODY_TEMPERATURE -> setOf(
            HealthPermission.getReadPermission(BodyTemperatureRecord::class)
        )
        HealthMetric.OXYGEN_SATURATION -> setOf(
            HealthPermission.getReadPermission(OxygenSaturationRecord::class)
        )
        HealthMetric.RESPIRATORY_RATE -> setOf(
            HealthPermission.getReadPermission(RespiratoryRateRecord::class)
        )
    }

    private fun enabledMetricPermissions(): Set<String> =
        healthMetricPreferencesStore.getAllSettings()
            .filterValues { it }
            .keys
            .flatMap { permissionForMetric(it) }
            .toSet()

    /** Called when the Sync section is (re-)entered to refresh HC availability/permission state. */
    fun checkHealthConnectStatus() {
        viewModelScope.launch {
            _healthConnectState.value = HealthConnectUiState.Checking
            if (!healthConnectManager.isAvailable()) {
                _healthConnectState.value = HealthConnectUiState.NotInstalled
                return@launch
            }
            val granted = try {
                healthConnectManager.getGrantedPermissions()
            } catch (e: Exception) {
                _healthConnectState.value = HealthConnectUiState.Error(
                    e.message ?: "Could not check Health Connect permissions"
                )
                return@launch
            }
            val needed = enabledMetricPermissions()
            if (needed.isNotEmpty() && !granted.containsAll(needed)) {
                _healthConnectState.value = HealthConnectUiState.PermissionsRequired()
                return@launch
            }
            // Show last sync result if available, otherwise Ready
            val last = syncRepository.getLastSyncStatusOnce()
            if (last != null && last.status == "success" && last.lastSyncTime != null) {
                _healthConnectState.value = HealthConnectUiState.LastSyncSuccess(
                    lastSyncTime = last.lastSyncTime,
                    summary = last.message ?: "Sync complete"
                )
            } else if (last != null && last.status == "error") {
                _healthConnectState.value = HealthConnectUiState.Error(
                    last.message ?: "Unknown sync error"
                )
            } else {
                _healthConnectState.value = HealthConnectUiState.Ready
            }
        }
    }

    /**
     * Called by the UI after the permission launcher returns.
     * Re-checks permission state and updates [healthConnectState] accordingly.
     */
    fun onPermissionsResult(granted: Set<String>) {
        viewModelScope.launch {
            val needed = enabledMetricPermissions()
            val hasAllNeeded = needed.all { it in granted }
            if (hasAllNeeded) {
                _healthConnectState.value = HealthConnectUiState.Ready
            } else {
                _healthConnectState.value = HealthConnectUiState.PermissionsRequired(
                    canRequest = granted.isNotEmpty()  // partial grant — some may be permanently denied
                )
            }
        }
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
            _healthConnectState.value = HealthConnectUiState.Syncing
            healthConnectManager.syncFromHealthConnect()
            _isSyncing.value = false
            // Refresh HC state to reflect the new outcome
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

    private val _clearRecipeIndexState = MutableStateFlow<BackupOpState>(BackupOpState.Idle)
    val clearRecipeIndexState: StateFlow<BackupOpState> = _clearRecipeIndexState.asStateFlow()

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

    /** Scoped operation: clear Recipe Index. This detaches recipe references (recipeId) from keto entries
     *  and leaves Recipe library items intact. Runs inside a coroutine and reports progress via
     *  clearRecipeIndexState. */
    fun clearRecipeIndex() {
        viewModelScope.launch {
            _clearRecipeIndexState.value = BackupOpState.InProgress
            runCatching {
                // Delete all saved recipes from the recipe library so the user can import a clean recipe set.
                recipeRepository.replaceAll(emptyList())
            }.onSuccess {
                _clearRecipeIndexState.value = BackupOpState.Success("All saved recipes deleted. Keto log entries preserved.")
            }.onFailure { e ->
                _clearRecipeIndexState.value = BackupOpState.Error(e.message ?: "Failed to clear recipe index (delete recipes).")
            }
        }
    }

    fun clearResetState() { _resetState.value = BackupOpState.Idle }

    // ── Nightly Backup settings ───────────────────────────────────────────────

    private val _autoBackupEnabled = MutableStateFlow(nightlyBackupStore.isAutoBackupEnabled())
    val autoBackupEnabled: StateFlow<Boolean> = _autoBackupEnabled.asStateFlow()

    fun setAutoBackupEnabled(enabled: Boolean) {
        nightlyBackupStore.setAutoBackupEnabled(enabled)
        _autoBackupEnabled.value = enabled
    }

    private val _retentionCount = MutableStateFlow(nightlyBackupStore.getRetentionCount())
    val retentionCount: StateFlow<Int> = _retentionCount.asStateFlow()

    fun setRetentionCount(count: Int) {
        nightlyBackupStore.setRetentionCount(count)
        _retentionCount.value = count.coerceIn(2, 3)
    }

    private val _lastBackupMs = MutableStateFlow(nightlyBackupStore.getLastSuccessMs())
    val lastBackupMs: StateFlow<Long> = _lastBackupMs.asStateFlow()

    private val _manualBackupState = MutableStateFlow<BackupOpState>(BackupOpState.Idle)
    val manualBackupState: StateFlow<BackupOpState> = _manualBackupState.asStateFlow()

    fun runManualBackup() {
        viewModelScope.launch {
            _manualBackupState.value = BackupOpState.InProgress
            nightlyBackupEngine.performBackup()
                .onSuccess {
                    _lastBackupMs.value = nightlyBackupStore.getLastSuccessMs()
                    _manualBackupState.value = BackupOpState.Success("Backup saved to Documents/Ember/Backups/")
                }
                .onFailure { e ->
                    _manualBackupState.value = BackupOpState.Error(
                        e.message ?: "Backup failed. Check storage permissions."
                    )
                }
        }
    }

    fun clearManualBackupState() { _manualBackupState.value = BackupOpState.Idle }

    fun clearClearRecipeIndexState() { _clearRecipeIndexState.value = BackupOpState.Idle }

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

    // ── Daily Rhythm ──────────────────────────────────────────────────────────

    private val _dailyRhythm = MutableStateFlow(dailyRhythmStore.getRhythm())
    val dailyRhythm: StateFlow<DailyRhythm> = _dailyRhythm.asStateFlow()

    fun setWakeTime(hour: Int, minute: Int) {
        dailyRhythmStore.setWakeTime(hour, minute)
        _dailyRhythm.value = _dailyRhythm.value.copy(wakeHour = hour, wakeMinute = minute)
    }

    fun setSleepTime(hour: Int, minute: Int) {
        dailyRhythmStore.setSleepTime(hour, minute)
        _dailyRhythm.value = _dailyRhythm.value.copy(sleepHour = hour, sleepMinute = minute)
    }

    fun setEatingStyle(style: EatingStyle) {
        dailyRhythmStore.setEatingStyle(style)
        _dailyRhythm.value = _dailyRhythm.value.copy(eatingStyle = style)
    }

    // ── Meal Timing ───────────────────────────────────────────────────────────

    private val _mealTiming = MutableStateFlow(mealTimingStore.getMealTiming())
    val mealTiming: StateFlow<MealTiming> = _mealTiming.asStateFlow()

    fun setBreakfastWindow(window: MealWindow?) {
        mealTimingStore.setBreakfastWindow(window)
        _mealTiming.value = _mealTiming.value.copy(breakfastWindow = window)
    }

    fun setLunchWindow(window: MealWindow?) {
        mealTimingStore.setLunchWindow(window)
        _mealTiming.value = _mealTiming.value.copy(lunchWindow = window)
    }

    fun setDinnerWindow(window: MealWindow?) {
        mealTimingStore.setDinnerWindow(window)
        _mealTiming.value = _mealTiming.value.copy(dinnerWindow = window)
    }

    fun setSnackWindow(window: MealWindow?) {
        mealTimingStore.setSnackWindow(window)
        _mealTiming.value = _mealTiming.value.copy(snackWindow = window)
    }

    // ── Health metric toggles ─────────────────────────────────────────────────

    val enabledMetrics: StateFlow<Map<HealthMetric, Boolean>> =
        healthMetricPreferencesStore.settingsFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = healthMetricPreferencesStore.getAllSettings()
            )

    val graphEnabledMetrics: StateFlow<Map<HealthMetric, Boolean>> =
        healthMetricPreferencesStore.graphSettingsFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = healthMetricPreferencesStore.getAllGraphSettings()
            )

    fun setMetricEnabled(metric: HealthMetric, enabled: Boolean) {
        healthMetricPreferencesStore.setMetricEnabled(metric, enabled)
    }

    fun setMetricGraphEnabled(metric: HealthMetric, enabled: Boolean) {
        healthMetricPreferencesStore.setMetricGraphEnabled(metric, enabled)
    }

    fun setAllMetricsEnabled(enabled: Boolean) {
        healthMetricPreferencesStore.setAllMetricsEnabled(enabled)
    }

    fun setAllGraphEnabled(enabled: Boolean) {
        healthMetricPreferencesStore.setAllGraphEnabled(enabled)
    }

    fun isMetricEnabled(metric: HealthMetric): Boolean =
        healthMetricPreferencesStore.isMetricEnabled(metric)
}

class SettingsViewModelFactory(
    private val syncRepository: SyncRepository,
    private val ketoRepository: KetoRepository,
    private val recipeRepository: RecipeRepository,
    private val healthConnectManager: HealthConnectManager,
    private val backupManager: BackupManager,
    private val themePreferencesStore: ThemePreferencesStore,
    private val unitsPreferencesStore: UnitsPreferencesStore,
    private val dailyRhythmStore: DailyRhythmStore,
    private val mealTimingStore: MealTimingStore,
    private val healthMetricPreferencesStore: HealthMetricPreferencesStore,
    private val nightlyBackupStore: NightlyBackupStore,
    private val nightlyBackupEngine: NightlyBackupEngine,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SettingsViewModel(
            syncRepository,
            ketoRepository,
            recipeRepository,
            healthConnectManager,
            backupManager,
            themePreferencesStore,
            unitsPreferencesStore,
            dailyRhythmStore,
            mealTimingStore,
            healthMetricPreferencesStore,
            nightlyBackupStore,
            nightlyBackupEngine,
        ) as T
}
