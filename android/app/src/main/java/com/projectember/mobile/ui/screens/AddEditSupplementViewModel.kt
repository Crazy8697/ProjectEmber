package com.projectember.mobile.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.local.entities.SupplementEntry
import com.projectember.mobile.data.repository.KetoRepository
import com.projectember.mobile.data.repository.SupplementRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AddEditSupplementViewModel(
    private val supplementRepository: SupplementRepository,
    private val ketoRepository: KetoRepository,
    private val editEntryId: Int? = null
) : ViewModel() {

    companion object {
        private const val DATE_FORMAT = "yyyy-MM-dd"
        private const val TIME_FORMAT = "HH:mm"
    }

    val isEditMode: Boolean get() = editEntryId != null

    private var originalEntry: SupplementEntry? = null

    // ── Form state ────────────────────────────────────────────────────────────
    var name by mutableStateOf("")
        private set
    var dose by mutableStateOf("")
        private set
    var unit by mutableStateOf("")
        private set
    var entryDate by mutableStateOf(
        LocalDate.now().format(DateTimeFormatter.ofPattern(DATE_FORMAT))
    )
        private set
    var entryTime by mutableStateOf(
        LocalTime.now().format(DateTimeFormatter.ofPattern(TIME_FORMAT))
    )
        private set
    var notes by mutableStateOf("")
        private set

    // ── Validation errors ─────────────────────────────────────────────────────
    var nameError by mutableStateOf<String?>(null)
        private set
    var doseError by mutableStateOf<String?>(null)
        private set

    init {
        if (editEntryId != null) {
            viewModelScope.launch {
                val entry = supplementRepository.getById(editEntryId)
                if (entry != null) {
                    originalEntry = entry
                    name = entry.name
                    dose = entry.dose
                    unit = entry.unit ?: ""
                    entryDate = entry.entryDate
                    entryTime = entry.entryTime
                    notes = entry.notes ?: ""
                }
            }
        }
    }

    // ── Change handlers ───────────────────────────────────────────────────────
    fun onNameChange(value: String) {
        name = value
        nameError = null
    }

    fun onDoseChange(value: String) {
        dose = value
        doseError = null
    }

    fun onUnitChange(value: String) { unit = value }
    fun onDateChange(value: String) { entryDate = value }
    fun onTimeChange(value: String) { entryTime = value }
    fun onNotesChange(value: String) { notes = value }

    /** Validate and persist. Calls [onSuccess] on success, [onValidationFailed] if invalid. */
    fun save(onSuccess: () -> Unit, onValidationFailed: () -> Unit = {}) {
        var valid = true

        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            nameError = "Supplement name is required"
            valid = false
        }

        val trimmedDose = dose.trim()
        if (trimmedDose.isBlank()) {
            doseError = "Dose is required"
            valid = false
        }

        if (!valid) {
            onValidationFailed()
            return
        }

        val dateStr = entryDate.ifBlank {
            LocalDate.now().format(DateTimeFormatter.ofPattern(DATE_FORMAT))
        }
        val timeStr = entryTime.ifBlank {
            LocalTime.now().format(DateTimeFormatter.ofPattern(TIME_FORMAT))
        }

        viewModelScope.launch {
            val existing = originalEntry
            if (existing != null) {
                supplementRepository.update(
                    existing.copy(
                        name = trimmedName,
                        dose = trimmedDose,
                        unit = unit.trim().takeIf { it.isNotBlank() },
                        entryDate = dateStr,
                        entryTime = timeStr,
                        notes = notes.trim().takeIf { it.isNotBlank() }
                    )
                )
            } else {
                supplementRepository.insert(
                    SupplementEntry(
                        name = trimmedName,
                        dose = trimmedDose,
                        unit = unit.trim().takeIf { it.isNotBlank() },
                        entryDate = dateStr,
                        entryTime = timeStr,
                        notes = notes.trim().takeIf { it.isNotBlank() }
                    )
                )
            }
            onSuccess()
        }
    }

    fun deleteEntry(onSuccess: () -> Unit) {
        val entry = originalEntry ?: return
        viewModelScope.launch {
            // If this log created a linked Keto entry, remove it too
            entry.ketoEntryId?.let { ketoId ->
                ketoRepository.getEntryById(ketoId)?.let { ketoEntry ->
                    ketoRepository.deleteEntry(ketoEntry)
                }
            }
            supplementRepository.delete(entry)
            onSuccess()
        }
    }
}

class AddEditSupplementViewModelFactory(
    private val supplementRepository: SupplementRepository,
    private val ketoRepository: KetoRepository,
    private val editEntryId: Int? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AddEditSupplementViewModel(supplementRepository, ketoRepository, editEntryId) as T
}
