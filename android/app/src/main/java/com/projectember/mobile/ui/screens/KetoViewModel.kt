package com.projectember.mobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.local.KetoTargets
import com.projectember.mobile.data.local.KetoTargetsStore
import com.projectember.mobile.data.local.entities.KetoEntry
import com.projectember.mobile.data.local.entities.effectiveCalories
import com.projectember.mobile.data.repository.KetoRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class DayTotals(
    val label: String,
    val date: String,
    val calories: Double,
    val netCarbsG: Double
)

class KetoViewModel(
    private val ketoRepository: KetoRepository,
    val targetsStore: KetoTargetsStore
) : ViewModel() {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val today: String = LocalDate.now().format(dateFormatter)
    private val sevenDaysAgo: String = LocalDate.now().minusDays(6).format(dateFormatter)

    val recentEntries: StateFlow<List<KetoEntry>> = ketoRepository
        .getRecentEntries(20)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val todayEntries: StateFlow<List<KetoEntry>> = ketoRepository
        .getEntriesForDate(today)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val targets: StateFlow<KetoTargets> = targetsStore.targets

    val weeklyData: StateFlow<List<DayTotals>> = ketoRepository
        .getEntriesFromDate(sevenDaysAgo)
        .map { entries ->
            val dayShortFmt = DateTimeFormatter.ofPattern("EEE")
            (0..6).map { offset ->
                val date = LocalDate.now().minusDays(6 - offset.toLong())
                val dateStr = date.format(dateFormatter)
                val dayEntries = entries.filter { it.entryDate == dateStr }
                val cals = dayEntries.sumOf { it.effectiveCalories() }
                DayTotals(
                    label = date.format(dayShortFmt),
                    date = dateStr,
                    calories = cals.coerceAtLeast(0.0),
                    netCarbsG = dayEntries.sumOf { it.netCarbsG }
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )
}

class KetoViewModelFactory(
    private val ketoRepository: KetoRepository,
    private val targetsStore: KetoTargetsStore
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        KetoViewModel(ketoRepository, targetsStore) as T
}
