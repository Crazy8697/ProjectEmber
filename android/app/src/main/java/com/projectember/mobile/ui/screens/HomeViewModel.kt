package com.projectember.mobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.local.KetoTargets
import com.projectember.mobile.data.local.KetoTargetsStore
import com.projectember.mobile.data.local.entities.SyncStatus
import com.projectember.mobile.data.local.entities.WeightEntry
import com.projectember.mobile.data.local.entities.effectiveCalories
import com.projectember.mobile.data.local.entities.effectiveFat
import com.projectember.mobile.data.local.entities.effectiveNetCarbs
import com.projectember.mobile.data.local.entities.effectiveProtein
import com.projectember.mobile.data.repository.KetoRepository
import com.projectember.mobile.data.repository.SyncRepository
import com.projectember.mobile.data.repository.WeightRepository
import com.projectember.mobile.sync.SyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class TodaySummary(
    val calories: Double,
    val proteinG: Double,
    val fatG: Double,
    val netCarbsG: Double
)

class HomeViewModel(
    private val syncRepository: SyncRepository,
    private val syncManager: SyncManager,
    ketoRepository: KetoRepository,
    targetsStore: KetoTargetsStore,
    weightRepository: WeightRepository
) : ViewModel() {

    private val today: String =
        LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    // ── Sync ─────────────────────────────────────────────────────────────────

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

    // ── Dashboard metrics ─────────────────────────────────────────────────────

    val targets: StateFlow<KetoTargets> = targetsStore.targets

    /** Aggregate of all keto (food) entries for today — exercise excluded. */
    val todaySummary: StateFlow<TodaySummary> = ketoRepository
        .getEntriesForDate(today)
        .combine(targetsStore.targets) { entries, _ ->
            // Only food entries count toward the dashboard calorie summary.
            val food = entries.filter { it.eventType != "exercise" }
            TodaySummary(
                calories  = food.sumOf { it.effectiveCalories() }.coerceAtLeast(0.0),
                proteinG  = food.sumOf { it.effectiveProtein() },
                fatG      = food.sumOf { it.effectiveFat() },
                netCarbsG = food.sumOf { it.effectiveNetCarbs() }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TodaySummary(0.0, 0.0, 0.0, 0.0)
        )

    /** Latest logged body weight, or null if none recorded. */
    val lastWeightEntry: StateFlow<WeightEntry?> = weightRepository
        .getLatestEntry()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )
}

class HomeViewModelFactory(
    private val syncRepository: SyncRepository,
    private val syncManager: SyncManager,
    private val ketoRepository: KetoRepository,
    private val targetsStore: KetoTargetsStore,
    private val weightRepository: WeightRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HomeViewModel(syncRepository, syncManager, ketoRepository, targetsStore, weightRepository) as T
}
