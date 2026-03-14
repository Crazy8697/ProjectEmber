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
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AddKetoEntryViewModel(
    private val ketoRepository: KetoRepository,
    private val editEntryId: Int? = null
) : ViewModel() {

    companion object {
        private const val DATE_FORMAT = "yyyy-MM-dd"
        private const val TIME_FORMAT = "HH:mm"
        private const val TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm"
        val EVENT_TYPES = listOf("meal", "drink", "snack", "exercise", "supplement")

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
    var servings by mutableStateOf("1")
        private set

    // Editable date and time for the entry
    var entryDate by mutableStateOf(
        LocalDate.now().format(DateTimeFormatter.ofPattern(DATE_FORMAT))
    )
        private set
    var entryTime by mutableStateOf(
        LocalTime.now().format(DateTimeFormatter.ofPattern(TIME_FORMAT))
    )
        private set

    // Exercise-specific optional fields
    var distanceKm by mutableStateOf("")
        private set
    var pace by mutableStateOf("")
        private set
    var steps by mutableStateOf("")
        private set
    var activitySubtype by mutableStateOf("")
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
                    entryDate = entry.entryDate
                    entryTime = if (entry.eventTimestamp.length >= 16)
                        entry.eventTimestamp.substring(11, 16)
                    else
                        LocalTime.now().format(DateTimeFormatter.ofPattern(TIME_FORMAT))
                    servings = formatDouble(entry.servings).ifBlank { "1" }
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
    fun onDateChange(value: String) { entryDate = value }
    fun onTimeChange(value: String) { entryTime = value }
    fun onServingsChange(value: String) { servings = value }

    // Exercise-specific handlers
    fun onDistanceKmChange(value: String) { distanceKm = value }
    fun onPaceChange(value: String) { pace = value }
    fun onStepsChange(value: String) { steps = value }
    fun onActivitySubtypeChange(value: String) { activitySubtype = value }

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

        val resolvedNotes = buildResolvedNotes()

        val dateStr = entryDate.ifBlank {
            LocalDate.now().format(DateTimeFormatter.ofPattern(DATE_FORMAT))
        }
        val timeStr = entryTime.ifBlank {
            LocalTime.now().format(DateTimeFormatter.ofPattern(TIME_FORMAT))
        }
        val ts = "$dateStr $timeStr"

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
                        entryDate = dateStr,
                        eventTimestamp = ts,
                        notes = resolvedNotes,
                        servings = servings.toDoubleOrNull()?.coerceAtLeast(0.1) ?: 1.0
                    )
                )
            } else {
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
                        entryDate = dateStr,
                        eventTimestamp = ts,
                        notes = resolvedNotes,
                        servings = servings.toDoubleOrNull()?.coerceAtLeast(0.1) ?: 1.0
                    )
                )
            }
            onSuccess()
        }
    }

    /** Builds the notes string, appending optional exercise extras when event type is exercise. */
    private fun buildResolvedNotes(): String? {
        val base = notes.trim()
        if (eventType.equals("exercise", ignoreCase = true)) {
            val extras = buildList {
                if (distanceKm.isNotBlank()) add("Distance: ${distanceKm.trim()} km")
                if (pace.isNotBlank())       add("Pace: ${pace.trim()}")
                if (steps.isNotBlank())      add("Steps: ${steps.trim()}")
                if (activitySubtype.isNotBlank()) add("Activity: ${activitySubtype.trim()}")
            }
            val parts = buildList {
                if (base.isNotBlank()) add(base)
                if (extras.isNotEmpty()) add(extras.joinToString(" | "))
            }
            return parts.joinToString("\n").ifBlank { null }
        }
        return base.ifBlank { null }
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
