package com.projectember.mobile.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.local.entities.ExerciseCategory
import com.projectember.mobile.data.local.entities.ExerciseEntry
import com.projectember.mobile.data.repository.ExerciseCategoryRepository
import com.projectember.mobile.data.repository.ExerciseRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AddEditExerciseViewModel(
    private val exerciseRepository: ExerciseRepository,
    private val categoryRepository: ExerciseCategoryRepository,
    private val editEntryId: Int? = null,
    initialDate: String? = null
) : ViewModel() {

    companion object {
        private const val DATE_FORMAT = "yyyy-MM-dd"
        private const val TIME_FORMAT = "HH:mm"

        /** Ordered map of exercise type → list of subtypes (empty = no subtypes). */
        val EXERCISE_TYPES_MAP: LinkedHashMap<String, List<String>> = linkedMapOf(
            "Walk"     to listOf("Outdoor Walk", "Treadmill Walk"),
            "Run"      to listOf("Outdoor Run", "Treadmill Run"),
            "Strength" to listOf("Upper Body", "Lower Body", "Full Body"),
            "Bike"     to listOf("Outdoor Bike", "Stationary Bike"),
            "Mobility" to listOf("Stretching", "Mobility Work"),
            "Chore"    to listOf("Yard Work", "House Cleaning"),
            "Sport"    to emptyList(),
            "Other"    to emptyList()
        )

        val EXERCISE_TYPES: List<String> = EXERCISE_TYPES_MAP.keys.toList()
    }

    val isEditMode: Boolean get() = editEntryId != null

    private var originalEntry: ExerciseEntry? = null

    // ── Form state ────────────────────────────────────────────────────────────
    var selectedCategoryId by mutableStateOf(0)
        private set
    var type by mutableStateOf("")
        private set
    var subtype by mutableStateOf("")
        private set
    var entryDate by mutableStateOf(
        initialDate?.takeIf { it.isNotBlank() }
            ?: LocalDate.now().format(DateTimeFormatter.ofPattern(DATE_FORMAT))
    )
        private set
    var entryTime by mutableStateOf(
        LocalTime.now().format(DateTimeFormatter.ofPattern(TIME_FORMAT))
    )
        private set
    var durationMinutes by mutableStateOf("")
        private set
    var caloriesBurned by mutableStateOf("")
        private set
    var notes by mutableStateOf("")
        private set

    // ── Validation errors ─────────────────────────────────────────────────────
    var categoryError by mutableStateOf<String?>(null)
        private set
    var typeError by mutableStateOf<String?>(null)
        private set

    // ── Categories (live from DB) ─────────────────────────────────────────────
    val categories: StateFlow<List<ExerciseCategory>> = categoryRepository
        .getAllCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    init {
        if (editEntryId != null) {
            viewModelScope.launch {
                val entry = exerciseRepository.getEntryById(editEntryId)
                if (entry != null) {
                    originalEntry = entry
                    selectedCategoryId = entry.categoryId
                    type = entry.type
                    subtype = entry.subtype ?: ""
                    entryDate = entry.entryDate
                    entryTime = entry.entryTime
                    durationMinutes = entry.durationMinutes?.toString() ?: ""
                    caloriesBurned = entry.caloriesBurned?.toBigDecimal()
                        ?.stripTrailingZeros()?.toPlainString() ?: ""
                    notes = entry.notes ?: ""
                }
            }
        } else {
            // Quick-log: auto-select a sensible default category when categories arrive
            viewModelScope.launch {
                val cats = categories.filter { it.isNotEmpty() }.first()
                if (selectedCategoryId <= 0) {
                    val default = cats.firstOrNull { it.name == "Other" } ?: cats.first()
                    selectedCategoryId = default.id
                }
            }
        }
    }

    // ── Change handlers ───────────────────────────────────────────────────────
    fun onCategorySelected(id: Int) {
        selectedCategoryId = id
        categoryError = null
    }

    fun onTypeSelected(value: String) {
        type = value
        subtype = "" // reset subtype whenever type changes
        typeError = null
    }

    fun onSubtypeSelected(value: String) { subtype = if (subtype == value) "" else value }
    fun clearSubtype() { subtype = "" }
    fun onDateChange(value: String) { entryDate = value }
    fun onTimeChange(value: String) { entryTime = value }
    fun onDurationChange(value: String) { durationMinutes = value }
    fun onCaloriesBurnedChange(value: String) { caloriesBurned = value }
    fun onNotesChange(value: String) { notes = value }

    /** Create a custom category and auto-select it. Returns an error message, or null on success. */
    suspend fun createCategoryValidated(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return "Category name cannot be empty"
        if (categoryRepository.nameExists(trimmed)) {
            return "A category named \"$trimmed\" already exists"
        }
        val newId = categoryRepository.insert(
            ExerciseCategory(name = trimmed, isBuiltIn = false)
        ).toInt()
        selectedCategoryId = newId
        categoryError = null
        return null
    }

    /** Create a custom category and auto-select it. */
    fun createCategory(name: String) {
        viewModelScope.launch {
            val error = createCategoryValidated(name)
            if (error != null) {
                categoryError = error
            }
        }
    }

    /**
     * Safely delete a custom category.
     *
     * Returns a [DeleteCategoryResult] describing the outcome:
     *  - [DeleteCategoryResult.Deleted] on success
     *  - [DeleteCategoryResult.BuiltInProtected] if the category is a built-in
     *  - [DeleteCategoryResult.InUse] if exercise entries reference it (includes count)
     */
    suspend fun deleteCategory(categoryId: Int): DeleteCategoryResult {
        val cat = categoryRepository.getById(categoryId)
            ?: return DeleteCategoryResult.Deleted // already gone
        if (cat.isBuiltIn) return DeleteCategoryResult.BuiltInProtected
        val usageCount = exerciseRepository.countEntriesForCategory(categoryId)
        if (usageCount > 0) return DeleteCategoryResult.InUse(usageCount)
        categoryRepository.delete(cat)
        // If the deleted category was selected, reset selection
        if (selectedCategoryId == categoryId) selectedCategoryId = 0
        return DeleteCategoryResult.Deleted
    }

    sealed interface DeleteCategoryResult {
        data object Deleted : DeleteCategoryResult
        data object BuiltInProtected : DeleteCategoryResult
        data class InUse(val entryCount: Int) : DeleteCategoryResult
    }

    /** Validate and persist. Calls [onSuccess] on success, [onValidationFailed] if invalid. */
    fun save(onSuccess: () -> Unit, onValidationFailed: () -> Unit = {}) {
        var valid = true

        if (selectedCategoryId <= 0) {
            categoryError = "Please select a category"
            valid = false
        }
        if (type.isBlank()) {
            typeError = "Please select an activity type"
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
        val ts = "$dateStr $timeStr"

        viewModelScope.launch {
            val existing = originalEntry
            if (existing != null) {
                exerciseRepository.updateEntry(
                    existing.copy(
                        entryDate = dateStr,
                        entryTime = timeStr,
                        timestamp = ts,
                        type = type,
                        subtype = subtype.takeIf { it.isNotBlank() },
                        categoryId = selectedCategoryId,
                        notes = notes.takeIf { it.isNotBlank() },
                        durationMinutes = durationMinutes.toIntOrNull(),
                        caloriesBurned = caloriesBurned.toDoubleOrNull()
                    )
                )
            } else {
                exerciseRepository.insertEntry(
                    ExerciseEntry(
                        entryDate = dateStr,
                        entryTime = timeStr,
                        timestamp = ts,
                        type = type,
                        subtype = subtype.takeIf { it.isNotBlank() },
                        categoryId = selectedCategoryId,
                        notes = notes.takeIf { it.isNotBlank() },
                        durationMinutes = durationMinutes.toIntOrNull(),
                        caloriesBurned = caloriesBurned.toDoubleOrNull()
                    )
                )
            }
            onSuccess()
        }
    }

    fun deleteEntry(onSuccess: () -> Unit) {
        val entry = originalEntry ?: return
        viewModelScope.launch {
            exerciseRepository.deleteEntry(entry)
            onSuccess()
        }
    }
}

class AddEditExerciseViewModelFactory(
    private val exerciseRepository: ExerciseRepository,
    private val categoryRepository: ExerciseCategoryRepository,
    private val editEntryId: Int? = null,
    private val initialDate: String? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AddEditExerciseViewModel(exerciseRepository, categoryRepository, editEntryId, initialDate) as T
}
