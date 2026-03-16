package com.projectember.mobile.data.backup

/** Increment when the backup format changes incompatibly. */
const val BACKUP_SCHEMA_VERSION = 1

/**
 * Root payload for a V1 backup file.
 *
 * All IDs are preserved so that cross-references (e.g. KetoEntry.recipeId,
 * ExerciseEntry.categoryId) remain valid after a restore.
 */
data class BackupPayloadV1(
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    val appVersion: String,
    val exportedAt: String,
    val ketoEntries: List<KetoEntryDto>,
    val recipes: List<RecipeDto>,
    val exerciseEntries: List<ExerciseEntryDto>,
    val exerciseCategories: List<ExerciseCategoryDto>,
    val weightEntries: List<WeightEntryDto>,
    val ketoTargets: KetoTargetsDto
)

data class KetoEntryDto(
    val id: Int = 0,
    val label: String,
    val eventType: String,
    val calories: Double,
    val proteinG: Double,
    val fatG: Double,
    val netCarbsG: Double,
    val waterMl: Double = 0.0,
    val sodiumMg: Double = 0.0,
    val potassiumMg: Double = 0.0,
    val magnesiumMg: Double = 0.0,
    val entryDate: String,
    val eventTimestamp: String,
    val notes: String? = null,
    val servings: Double = 1.0,
    val recipeId: Int? = null
)

data class RecipeDto(
    val id: Int = 0,
    val name: String,
    val category: String = "General",
    val description: String? = null,
    val calories: Double,
    val proteinG: Double,
    val fatG: Double,
    val netCarbsG: Double,
    val totalCarbsG: Double = 0.0,
    val fiberG: Double = 0.0,
    val sodiumMg: Double = 0.0,
    val potassiumMg: Double = 0.0,
    val magnesiumMg: Double = 0.0,
    val waterMl: Double = 0.0,
    val servings: Double = 1.0,
    val ketoNotes: String? = null,
    val ingredientsRaw: String? = null
)

data class ExerciseEntryDto(
    val id: Int = 0,
    val entryDate: String,
    val entryTime: String,
    val timestamp: String,
    val type: String,
    val subtype: String? = null,
    val categoryId: Int,
    val notes: String? = null,
    val durationMinutes: Int? = null,
    val caloriesBurned: Double? = null
)

data class ExerciseCategoryDto(
    val id: Int = 0,
    val name: String,
    val isBuiltIn: Boolean = false
)

data class WeightEntryDto(
    val id: Int = 0,
    val entryDate: String,
    val weightKg: Double,
    /** Null for manually-entered entries; "health_connect" for HC imports. */
    val source: String? = null
)

data class KetoTargetsDto(
    val caloriesKcal: Double = 2000.0,
    val proteinG: Double = 100.0,
    val fatG: Double = 150.0,
    val netCarbsG: Double = 20.0,
    val waterMl: Double = 2000.0,
    val sodiumMg: Double = 2300.0,
    val potassiumMg: Double = 3500.0,
    val magnesiumMg: Double = 400.0
)
