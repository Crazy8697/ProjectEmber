package com.projectember.mobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.local.UnitPreferences
import com.projectember.mobile.data.local.UnitsPreferencesStore
import com.projectember.mobile.data.local.WeightUnit
import com.projectember.mobile.data.local.entities.WeightEntry
import com.projectember.mobile.data.repository.WeightRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class WeightHistoryViewModel(
    private val weightRepository: WeightRepository,
    private val unitsPreferencesStore: UnitsPreferencesStore
) : ViewModel() {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** All weight entries, newest first. */
    val allEntries: StateFlow<List<WeightEntry>> = weightRepository
        .getAllEntries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** Live unit preferences so the screen can react to changes. */
    val unitPreferences: StateFlow<UnitPreferences> = unitsPreferencesStore
        .preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = unitsPreferencesStore.getPreferences()
        )

    /**
     * Log a new weight entry for the given date (defaults to today).
     * [weightInSelectedUnit] is the value the user typed — it will be converted
     * to kg before being persisted so that stored values remain stable regardless
     * of unit preference.
     */
    fun logWeight(
        weightInSelectedUnit: Double,
        unit: WeightUnit = unitsPreferencesStore.getPreferences().weightUnit,
        date: String = LocalDate.now().format(dateFormatter)
    ) {
        val kg = unit.toKg(weightInSelectedUnit)
        viewModelScope.launch(Dispatchers.IO) {
            weightRepository.insert(WeightEntry(entryDate = date, weightKg = kg))
        }
    }

    /** Delete a weight entry. */
    fun deleteEntry(entry: WeightEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            weightRepository.delete(entry)
        }
    }
}

class WeightHistoryViewModelFactory(
    private val weightRepository: WeightRepository,
    private val unitsPreferencesStore: UnitsPreferencesStore
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        WeightHistoryViewModel(weightRepository, unitsPreferencesStore) as T
}

