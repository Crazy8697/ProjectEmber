package com.projectember.mobile.import

import android.util.Log
import com.projectember.mobile.data.local.dao.WeightDao
import com.projectember.mobile.data.local.entities.WeightEntry
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

interface ImportRepository {
    suspend fun importItems(
        domain: String,
        items: List<Map<String, Any?>>,
        includeDuplicates: Boolean = false
    ): ImportSummary
}

/**
 * A conservative stub repository used by the import ViewModel until real repositories are wired.
 * It simulates duplicate detection by building a simple signature from date/time/value fields.
 */
class StubImportRepository : ImportRepository {
    private val existingSignatures = mutableSetOf<String>()

    override suspend fun importItems(
        domain: String,
        items: List<Map<String, Any?>>,
        includeDuplicates: Boolean
    ): ImportSummary {
        delay(200)

        var valid = 0
        var invalid = 0
        var duplicates = 0
        val errors = mutableListOf<String>()
        val normalizedDomain = domain.lowercase()

        for ((idx, item) in items.withIndex()) {
            val validation = ImportValidator.validate(normalizedDomain, item)
            if (!validation.valid) {
                invalid++
                errors.add("item[$idx]: ${validation.reason ?: "invalid"}")
                continue
            }

            val signature = buildSignature(validation)
            val wasAdded = signature?.let { existingSignatures.add(it) } ?: false
            val isDuplicate = !includeDuplicates && signature != null && !wasAdded
            if (isDuplicate) {
                duplicates++
                continue
            }

            if (!wasAdded && signature != null) {
                existingSignatures.add(signature)
            }
            valid++
        }

        return ImportSummary(
            domain = domain,
            total = items.size,
            valid = valid,
            invalid = invalid,
            duplicates = duplicates,
            errors = errors
        )
    }

    private fun buildSignature(validation: ValidationResult): String? {
        val date = validation.normalizedDate ?: return null
        val time = validation.normalizedTime ?: "00:00"
        val value = validation.primaryValue?.let { (it * 1_000).roundToLong() } ?: return null
        return listOf(validation.normalizedDomain, date, time, value.toString()).joinToString("|")
    }
}

class RoomImportRepository(
    private val weightDao: WeightDao,
    private val fallback: ImportRepository = StubImportRepository()
) : ImportRepository {

    private companion object {
        const val TAG = "RoomImportRepository"
    }

    override suspend fun importItems(
        domain: String,
        items: List<Map<String, Any?>>,
        includeDuplicates: Boolean
    ): ImportSummary {
        return when (domain.lowercase()) {
            "weight" -> importWeight(items, includeDuplicates)
            else -> fallback.importItems(domain, items, includeDuplicates)
        }
    }

    private suspend fun importWeight(
        items: List<Map<String, Any?>>,
        includeDuplicates: Boolean
    ): ImportSummary {
        val errors = mutableListOf<String>()
        var valid = 0
        var invalid = 0
        var duplicates = 0

        val beforeRows = weightDao.count()
        val existingRows = weightDao.getAllOnce()
        val existingSignatures = existingRows
            .mapNotNull { buildWeightSignature(it.entryDate, it.weightKg) }
            .toMutableSet()

        Log.d(TAG, "importWeight: previewItems=${items.size}, existingRows=$beforeRows")

        val rowsToInsert = mutableListOf<WeightEntry>()

        items.forEachIndexed { index, raw ->
            val validation = ImportValidator.validate("weight", raw)
            if (!validation.valid) {
                invalid++
                errors.add("item[$index]: ${validation.reason ?: "invalid"}")
                return@forEachIndexed
            }

            val signature = buildWeightSignature(validation.normalizedDate, validation.primaryValue)
            val isDuplicate = !includeDuplicates && signature != null && !existingSignatures.add(signature)
            if (isDuplicate) {
                duplicates++
                return@forEachIndexed
            }

            if (signature != null) existingSignatures.add(signature)

            rowsToInsert += WeightEntry(
                entryDate = validation.normalizedDate!!,
                weightKg = validation.primaryValue!!,
                source = WeightEntry.SOURCE_IMPORT
            )
            valid++
        }

        val insertedIds = if (rowsToInsert.isNotEmpty()) {
            weightDao.insertAll(rowsToInsert)
        } else {
            emptyList()
        }

        val afterRows = weightDao.count()
        val netInserted = (afterRows - beforeRows).coerceAtLeast(0)

        Log.d(
            TAG,
            "importWeight: mapped=${rowsToInsert.size}, insertedCalls=${insertedIds.size}, netInserted=$netInserted, afterRows=$afterRows"
        )

        // Report persistence based on actual stored row delta, not optimistic mapping count.
        val nonDuplicateCandidates = items.size - invalid - duplicates
        val persistedValid = minOf(netInserted, nonDuplicateCandidates)
        val droppedAfterValidation = (nonDuplicateCandidates - persistedValid).coerceAtLeast(0)
        if (droppedAfterValidation > 0) {
            invalid += droppedAfterValidation
            errors.add("$droppedAfterValidation items did not persist (database insert mismatch)")
        }

        val historyRows = weightDao.getAllOnce().size
        Log.d(TAG, "importWeight: historyRowsAfterImport=$historyRows")

        return ImportSummary(
            domain = "weight",
            total = items.size,
            valid = persistedValid,
            invalid = invalid,
            duplicates = duplicates,
            errors = errors
        )
    }

    private fun buildWeightSignature(date: String?, value: Double?): String? {
        if (date == null || value == null) return null
        return listOf("weight", date, ((value * 1_000).roundToLong()).toString()).joinToString("|")
    }
}

