package com.projectember.mobile.data.keto

import com.projectember.mobile.data.local.entities.KetoEntry

/** Schema version for the Keto-event JSON import format. */
const val KETO_JSON_SCHEMA_VERSION = 1

/** How duplicate Keto entries (matched by label + entryDate + eventTimestamp) are handled. */
enum class KetoDuplicateHandling {
    /** Skip the incoming entry — existing entry is kept unchanged. */
    SKIP,

    /** Insert the incoming entry as a brand-new record regardless of the duplicate. */
    IMPORT_AS_NEW
}

/**
 * A single Keto entry candidate that passed validation.
 *
 * @param index          0-based position in the source JSON array.
 * @param candidate      The parsed entry with id=0 (auto-assigned on insert).
 * @param isDuplicate    True if a matching entry (same label + date + timestamp) already exists.
 */
data class KetoCandidateItem(
    val index: Int,
    val candidate: KetoEntry,
    val isDuplicate: Boolean
)

/** A Keto entry from the source JSON that failed validation. */
data class KetoParseError(
    val index: Int,
    val reason: String
)

/**
 * Summary of a validated Keto import payload — shown to the user before committing.
 *
 * @param totalFound Total number of items in the source JSON array (valid + invalid).
 * @param valid      Items that passed validation (includes duplicates).
 * @param invalid    Items that failed validation and will be skipped.
 */
data class KetoImportPreview(
    val totalFound: Int,
    val valid: List<KetoCandidateItem>,
    val invalid: List<KetoParseError>
) {
    val duplicates: List<KetoCandidateItem> get() = valid.filter { it.isDuplicate }
    val nonDuplicates: List<KetoCandidateItem> get() = valid.filter { !it.isDuplicate }
}
