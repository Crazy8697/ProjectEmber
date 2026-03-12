package com.projectember.mobile.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.local.entities.KetoEntry
import com.projectember.mobile.data.repository.KetoRepository
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class AddKetoEntryViewModel(private val ketoRepository: KetoRepository) : ViewModel() {

    companion object {
        private const val DATE_FORMAT = "yyyy-MM-dd"
        private const val TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm"
        val EVENT_TYPES = listOf("meal", "drink", "snack")

        private fun parseDoubleOrZero(value: String): Double = value.toDoubleOrNull() ?: 0.0
    }

    var label by mutableStateOf("")
        private set
    var eventType by mutableStateOf("")
        private set
    var calories by mutableStateOf("")
        private set
    var proteinG by mutableStateOf("")
        private set
    var fatG by mutableStateOf("")
        private set
    var netCarbsG by mutableStateOf("")
        private set
    var notes by mutableStateOf("")
        private set

    var labelError by mutableStateOf<String?>(null)
        private set
    var eventTypeError by mutableStateOf<String?>(null)
        private set

    fun onLabelChange(value: String) {
        label = value
        labelError = null
    }

    fun onEventTypeChange(value: String) {
        eventType = value
        eventTypeError = null
    }

    fun onCaloriesChange(value: String) { calories = value }
    fun onProteinGChange(value: String) { proteinG = value }
    fun onFatGChange(value: String) { fatG = value }
    fun onNetCarbsGChange(value: String) { netCarbsG = value }
    fun onNotesChange(value: String) { notes = value }

    fun save(onSuccess: () -> Unit) {
        var valid = true
        if (label.isBlank()) {
            labelError = "Label is required"
            valid = false
        }
        if (eventType.isBlank()) {
            eventTypeError = "Event type is required"
            valid = false
        }
        if (!valid) return

        viewModelScope.launch {
            val now = LocalDateTime.now()
            val entry = KetoEntry(
                label = label.trim(),
                eventType = eventType,
                calories = parseDoubleOrZero(calories),
                proteinG = parseDoubleOrZero(proteinG),
                fatG = parseDoubleOrZero(fatG),
                netCarbsG = parseDoubleOrZero(netCarbsG),
                entryDate = now.format(DateTimeFormatter.ofPattern(DATE_FORMAT)),
                eventTimestamp = now.format(DateTimeFormatter.ofPattern(TIMESTAMP_FORMAT)),
                notes = notes.trim().ifBlank { null }
            )
            ketoRepository.insertEntry(entry)
            onSuccess()
        }
    }
}

class AddKetoEntryViewModelFactory(
    private val ketoRepository: KetoRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AddKetoEntryViewModel(ketoRepository) as T
}
