package com.projectember.mobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.local.entities.KetoEntry
import com.projectember.mobile.data.local.entities.StackDefinition
import com.projectember.mobile.data.local.entities.SupplementEntry
import com.projectember.mobile.data.repository.KetoRepository
import com.projectember.mobile.data.repository.StackDefinitionRepository
import com.projectember.mobile.data.repository.SupplementRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class StacksViewModel(
    private val stackDefinitionRepository: StackDefinitionRepository,
    private val supplementRepository: SupplementRepository,
    private val ketoRepository: KetoRepository
) : ViewModel() {

    private companion object {
        const val DATE_FORMAT = "yyyy-MM-dd"
        const val TIME_FORMAT = "HH:mm"
        const val KETO_EVENT_TYPE = "supplement"
    }

    /** All saved Stack definitions, sorted alphabetically. */
    val definitions: StateFlow<List<StackDefinition>> = stackDefinitionRepository.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** All logged Stack entries, sorted date/time descending. */
    val logEntries: StateFlow<List<SupplementEntry>> = supplementRepository.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * Quick-log a saved Stack definition with the current date and time.
     *
     * If the definition carries any nutrition data, a corresponding KetoEntry
     * (eventType = "supplement") is also created so the values flow into the
     * Keto daily totals. The created KetoEntry id is stored in the log entry
     * so it can be cleaned up on delete.
     *
     * @param onComplete called on the main thread after saving. The boolean
     *   indicates whether a Keto entry was also created (true = keto-linked).
     */
    fun quickLog(definition: StackDefinition, onComplete: (ketoLinked: Boolean) -> Unit = {}) {
        val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern(DATE_FORMAT))
        val timeStr = LocalTime.now().format(DateTimeFormatter.ofPattern(TIME_FORMAT))

        viewModelScope.launch {
            // Step 1: Try to create the linked Keto entry if this definition has nutrition data.
            // A failed Keto insert must NOT prevent the Stack log from being saved — so the
            // entire Keto-insert path is wrapped in its own try/catch.
            var ketoEntryId: Int? = null
            if (definition.hasNutritionData()) {
                try {
                    val insertedId = ketoRepository.insertEntryAndReturnId(
                        KetoEntry(
                            label = definition.name,
                            eventType = KETO_EVENT_TYPE,
                            calories = definition.caloriesKcal ?: 0.0,
                            proteinG = definition.proteinG ?: 0.0,
                            fatG = definition.fatG ?: 0.0,
                            netCarbsG = definition.netCarbsG ?: 0.0,
                            waterMl = 0.0,
                            sodiumMg = definition.sodiumMg ?: 0.0,
                            potassiumMg = definition.potassiumMg ?: 0.0,
                            magnesiumMg = definition.magnesiumMg ?: 0.0,
                            entryDate = dateStr,
                            eventTimestamp = "$dateStr $timeStr",
                            notes = "Logged from Stacks",
                            servings = 1.0
                        )
                    )
                    // Only store the link when the DB returned a valid, positive row id
                    // that fits within Int range (the ketoEntryId column is INTEGER/Int).
                    if (insertedId > 0L && insertedId <= Int.MAX_VALUE) {
                        ketoEntryId = insertedId.toInt()
                    }
                } catch (_: Exception) {
                    // Keto insert failed — the Stack log will still be saved below,
                    // just without a Keto link. ketoEntryId stays null.
                }
            }

            // Step 2: Always save the Stack log, carrying the Keto link when one was created.
            supplementRepository.insert(
                SupplementEntry(
                    name = definition.name,
                    dose = definition.defaultDose ?: "",
                    unit = definition.defaultUnit,
                    entryDate = dateStr,
                    entryTime = timeStr,
                    notes = definition.notes,
                    stackDefinitionId = definition.id,
                    ketoEntryId = ketoEntryId
                )
            )

            // Only report ketoLinked = true when the Keto entry was actually created and linked.
            onComplete(ketoEntryId != null)
        }
    }
}

class StacksViewModelFactory(
    private val stackDefinitionRepository: StackDefinitionRepository,
    private val supplementRepository: SupplementRepository,
    private val ketoRepository: KetoRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        StacksViewModel(stackDefinitionRepository, supplementRepository, ketoRepository) as T
}

