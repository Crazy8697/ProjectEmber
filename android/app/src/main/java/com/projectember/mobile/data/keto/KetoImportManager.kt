package com.projectember.mobile.data.keto

import android.content.Context
import android.net.Uri
import com.projectember.mobile.data.local.entities.KetoEntry
import com.projectember.mobile.data.repository.KetoRepository
import org.json.JSONArray
import org.json.JSONObject

/**
 * Handles Keto-event JSON import.
 *
 * Import: reads JSON from a [Uri] or a raw string, validates each entry object, detects
 * likely duplicates against the live database, and returns a [KetoImportPreview] for user
 * confirmation before any data is written.
 *
 * No export is provided in this PR — this is a targeted import-only tool.
 *
 * Expected JSON format:
 * ```json
 * {
 *   "schemaVersion": 1,
 *   "exportedAt": "2026-03-18T12:00:00Z",
 *   "ketoEntries": [
 *     {
 *       "label": "Scrambled Eggs",
 *       "eventType": "meal",
 *       "calories": 320,
 *       "proteinG": 22,
 *       "fatG": 24,
 *       "netCarbsG": 2,
 *       "waterMl": 0,
 *       "sodiumMg": 380,
 *       "potassiumMg": 200,
 *       "magnesiumMg": 20,
 *       "entryDate": "2026-03-18",
 *       "eventTimestamp": "2026-03-18 08:15",
 *       "notes": "Optional notes",
 *       "servings": 1.0
 *     }
 *   ]
 * }
 * ```
 */
class KetoImportManager(
    private val context: Context,
    private val ketoRepository: KetoRepository
) {

    // ── Import ────────────────────────────────────────────────────────────────

    /**
     * Reads the JSON file at [uri], validates each entry object, and returns a
     * [KetoImportPreview] ready for user confirmation.
     *
     * No data is written — call [commitImport] after the user confirms.
     */
    suspend fun parseAndValidateFromUri(uri: Uri): Result<KetoImportPreview> = runCatching {
        val json = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: error("Cannot open import file")
        parseAndValidate(json).getOrThrow()
    }

    /**
     * Validates a raw JSON string and returns a [KetoImportPreview].
     * No data is written — call [commitImport] after the user confirms.
     */
    suspend fun parseAndValidate(json: String): Result<KetoImportPreview> = runCatching {
        val existingEntries = ketoRepository.getAllOnce()
        val existingKeys = existingEntries
            .map { duplicateKey(it.label, it.entryDate, it.eventTimestamp) }
            .toSet()

        val root = runCatching { JSONObject(json) }.getOrElse {
            error("Invalid JSON: ${it.message}")
        }

        val arr: JSONArray = when {
            root.has("ketoEntries") -> root.getJSONArray("ketoEntries")
            else -> error("JSON must contain a top-level \"ketoEntries\" array")
        }

        val valid = mutableListOf<KetoCandidateItem>()
        val invalid = mutableListOf<KetoParseError>()

        for (i in 0 until arr.length()) {
            val obj = try {
                arr.getJSONObject(i)
            } catch (_: Exception) {
                invalid.add(KetoParseError(i, "Item at index $i is not a JSON object"))
                continue
            }

            val candidate = try {
                parseEntryObject(obj, i)
            } catch (e: Exception) {
                invalid.add(KetoParseError(i, e.message ?: "Unknown parse error"))
                continue
            }

            val key = duplicateKey(candidate.label, candidate.entryDate, candidate.eventTimestamp)
            valid.add(
                KetoCandidateItem(
                    index = i,
                    candidate = candidate,
                    isDuplicate = key in existingKeys
                )
            )
        }

        KetoImportPreview(
            totalFound = arr.length(),
            valid = valid,
            invalid = invalid
        )
    }

    /**
     * Commits the validated import, applying [duplicateHandling] for duplicate entries.
     *
     * @return The number of entries actually written to the database.
     */
    suspend fun commitImport(
        preview: KetoImportPreview,
        duplicateHandling: KetoDuplicateHandling
    ): Int {
        var count = 0
        for (item in preview.valid) {
            if (item.isDuplicate) {
                when (duplicateHandling) {
                    KetoDuplicateHandling.SKIP -> Unit
                    KetoDuplicateHandling.IMPORT_AS_NEW -> {
                        ketoRepository.insertEntry(item.candidate.copy(id = 0))
                        count++
                    }
                }
            } else {
                ketoRepository.insertEntry(item.candidate.copy(id = 0))
                count++
            }
        }
        return count
    }

    // ── Parsing helpers ───────────────────────────────────────────────────────

    private fun parseEntryObject(obj: JSONObject, index: Int): KetoEntry {
        val label = obj.optString("label", "").trim()
        require(label.isNotBlank()) {
            "Item at index $index: \"label\" is required and must not be blank"
        }

        val eventType = obj.optString("eventType", "").trim()
        require(eventType.isNotBlank()) {
            "Entry \"$label\": \"eventType\" is required and must not be blank"
        }

        val calories   = requireNonNegativeDouble(obj, "calories", label)
        val proteinG   = requireNonNegativeDouble(obj, "proteinG", label)
        val fatG       = requireNonNegativeDouble(obj, "fatG", label)
        val netCarbsG  = requireNonNegativeDouble(obj, "netCarbsG", label)

        val entryDate = obj.optString("entryDate", "").trim()
        require(entryDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            "Entry \"$label\": \"entryDate\" must be in yyyy-MM-dd format (got \"$entryDate\")"
        }

        val eventTimestamp = obj.optString("eventTimestamp", "").trim()
        require(eventTimestamp.isNotBlank()) {
            "Entry \"$label\": \"eventTimestamp\" is required"
        }
        require(eventTimestamp.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}.*"))) {
            "Entry \"$label\": \"eventTimestamp\" must start with \"yyyy-MM-dd HH:mm\" format (got \"$eventTimestamp\")"
        }

        val servings = obj.optDouble("servings", 1.0)
        require(!servings.isNaN() && servings > 0) {
            "Entry \"$label\": \"servings\" must be a positive number (got $servings)"
        }

        return KetoEntry(
            id = 0,
            label = label,
            eventType = eventType,
            calories = calories,
            proteinG = proteinG,
            fatG = fatG,
            netCarbsG = netCarbsG,
            waterMl = obj.optDouble("waterMl", 0.0).coerceAtLeast(0.0),
            sodiumMg = obj.optDouble("sodiumMg", 0.0).coerceAtLeast(0.0),
            potassiumMg = obj.optDouble("potassiumMg", 0.0).coerceAtLeast(0.0),
            magnesiumMg = obj.optDouble("magnesiumMg", 0.0).coerceAtLeast(0.0),
            entryDate = entryDate,
            eventTimestamp = eventTimestamp,
            notes = obj.optString("notes", "").takeIf { it.isNotEmpty() },
            servings = servings,
            recipeId = null   // recipeId intentionally omitted — import as standalone entry
        )
    }

    private fun requireNonNegativeDouble(obj: JSONObject, key: String, label: String): Double {
        require(obj.has(key)) { "Entry \"$label\": \"$key\" is required" }
        val v = obj.optDouble(key, Double.NaN)
        require(!v.isNaN()) { "Entry \"$label\": \"$key\" must be a number" }
        require(v >= 0) { "Entry \"$label\": \"$key\" must be >= 0 (got $v)" }
        return v
    }

    private fun duplicateKey(label: String, entryDate: String, eventTimestamp: String) =
        "${label.trim().lowercase()}|$entryDate|$eventTimestamp"
}
