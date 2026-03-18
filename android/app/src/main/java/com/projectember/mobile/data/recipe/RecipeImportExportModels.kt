package com.projectember.mobile.data.recipe

import com.projectember.mobile.data.local.entities.Recipe

/** Schema version for the recipe-specific JSON export format (separate from the full backup schema). */
const val RECIPE_JSON_SCHEMA_VERSION = 1

/** How duplicate recipes (matched by name + category, case-insensitive) should be handled during import. */
enum class DuplicateHandling {
    /** Skip the incoming recipe — existing recipe is kept unchanged. */
    SKIP,

    /** Replace the existing recipe with the incoming data. */
    OVERWRITE,

    /** Insert the incoming recipe as a brand-new entry regardless of the duplicate. */
    IMPORT_AS_NEW
}

/**
 * A single candidate recipe that passed basic validation.
 *
 * @param index          0-based position in the source JSON array.
 * @param candidateRecipe The parsed recipe with id=0 (to be auto-assigned on insert).
 * @param isDuplicate     True if a recipe with the same normalised name+category already exists.
 * @param existingId      The Room id of the matching existing recipe (null if not a duplicate).
 */
data class RecipeCandidateItem(
    val index: Int,
    val candidateRecipe: Recipe,
    val isDuplicate: Boolean,
    val existingId: Int?
)

/** A recipe entry from the source JSON that failed validation. */
data class RecipeParseError(
    val index: Int,
    val reason: String
)

/**
 * Summary of a validated import payload — shown to the user before committing.
 *
 * @param totalFound Total number of items in the source JSON array (valid + invalid).
 * @param valid      Items that passed validation (includes duplicates).
 * @param invalid    Items that failed validation and will be skipped.
 */
data class RecipeImportPreview(
    val totalFound: Int,
    val valid: List<RecipeCandidateItem>,
    val invalid: List<RecipeParseError>
) {
    val duplicates: List<RecipeCandidateItem> get() = valid.filter { it.isDuplicate }
    val nonDuplicates: List<RecipeCandidateItem> get() = valid.filter { !it.isDuplicate }
}
