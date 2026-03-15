package com.projectember.mobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
    private val weightRepository: WeightRepository
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

    /** Log a new weight entry for the given date (defaults to today). */
    fun logWeight(weightKg: Double, date: String = LocalDate.now().format(dateFormatter)) {
        viewModelScope.launch(Dispatchers.IO) {
            weightRepository.insert(WeightEntry(entryDate = date, weightKg = weightKg))
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
    private val weightRepository: WeightRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        WeightHistoryViewModel(weightRepository) as T
}
