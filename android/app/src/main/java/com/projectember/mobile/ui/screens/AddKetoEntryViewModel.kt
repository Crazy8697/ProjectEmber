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

class AddKetoEntryViewModel(
    private val ketoRepository: KetoRepository,
    private val editEntryId: Int? = null
) : ViewModel() {

    companion object {
        private const val DATE_FORMAT = "yyyy-MM-dd"
        private const val TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm"
        val EVENT_TYPES = listOf("meal", "drink", "snack", "exercise")

        private fun parseDoubleOrZero(value: String): Double = value.toDoubleOrNull() ?: 0.0

        private fun formatDouble(d: Double): String =
            if (d == 0.0) "" else d.toBigDecimal().stripTrailingZeros().toPlainString()
    }

    /** True when opened for an existing entry; false when creating a new one. */
    val isEditMode: Boolean get() = editEntryId != null

    private var originalEntry: KetoEntry? = null

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
    var waterMl by mutableStateOf("")
        private set
    var sodiumMg by mutableStateOf("")
        private set
    var potassiumMg by mutableStateOf("")
        private set
    var magnesiumMg by mutableStateOf("")
        private set
    var notes by mutableStateOf("")
        private set

    var labelError by mutableStateOf<String?>(null)
        private set
    var eventTypeError by mutableStateOf<String?>(null)
        private set

    init {
        if (editEntryId != null) {
            viewModelScope.launch {
                val entry = ketoRepository.getEntryById(editEntryId)
                if (entry != null) {
                    originalEntry = entry
                    label = entry.label
                    eventType = entry.eventType
                    calories = formatDouble(entry.calories)
                    proteinG = formatDouble(entry.proteinG)
                    fatG = formatDouble(entry.fatG)
                    netCarbsG = formatDouble(entry.netCarbsG)
                    waterMl = formatDouble(entry.waterMl)
                    sodiumMg = formatDouble(entry.sodiumMg)
                    potassiumMg = formatDouble(entry.potassiumMg)
                    magnesiumMg = formatDouble(entry.magnesiumMg)
                    notes = entry.notes ?: ""
                }
            }
        }
    }

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
    fun onWaterMlChange(value: String) { waterMl = value }
    fun onSodiumMgChange(value: String) { sodiumMg = value }
    fun onPotassiumMgChange(value: String) { potassiumMg = value }
    fun onMagnesiumMgChange(value: String) { magnesiumMg = value }
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
            val existing = originalEntry
            if (existing != null) {
                ketoRepository.updateEntry(
                    existing.copy(
                        label = label.trim(),
                        eventType = eventType,
                        calories = parseDoubleOrZero(calories),
                        proteinG = parseDoubleOrZero(proteinG),
                        fatG = parseDoubleOrZero(fatG),
                        netCarbsG = parseDoubleOrZero(netCarbsG),
                        waterMl = parseDoubleOrZero(waterMl),
                        sodiumMg = parseDoubleOrZero(sodiumMg),
                        potassiumMg = parseDoubleOrZero(potassiumMg),
                        magnesiumMg = parseDoubleOrZero(magnesiumMg),
                        notes = notes.trim().ifBlank { null }
                    )
                )
            } else {
                val now = LocalDateTime.now()
                ketoRepository.insertEntry(
                    KetoEntry(
                        label = label.trim(),
                        eventType = eventType,
                        calories = parseDoubleOrZero(calories),
                        proteinG = parseDoubleOrZero(proteinG),
                        fatG = parseDoubleOrZero(fatG),
                        netCarbsG = parseDoubleOrZero(netCarbsG),
                        waterMl = parseDoubleOrZero(waterMl),
                        sodiumMg = parseDoubleOrZero(sodiumMg),
                        potassiumMg = parseDoubleOrZero(potassiumMg),
                        magnesiumMg = parseDoubleOrZero(magnesiumMg),
                        entryDate = now.format(DateTimeFormatter.ofPattern(DATE_FORMAT)),
                        eventTimestamp = now.format(DateTimeFormatter.ofPattern(TIMESTAMP_FORMAT)),
                        notes = notes.trim().ifBlank { null }
                    )
                )
            }
            onSuccess()
        }
    }

    fun deleteEntry(onSuccess: () -> Unit) {
        // originalEntry is null only if the entry hasn't loaded yet; nothing to delete in that case.
        val entry = originalEntry ?: return
        viewModelScope.launch {
            ketoRepository.deleteEntry(entry)
            onSuccess()
        }
    }
}

class AddKetoEntryViewModelFactory(
    private val ketoRepository: KetoRepository,
    private val editEntryId: Int? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AddKetoEntryViewModel(ketoRepository, editEntryId) as T
}
