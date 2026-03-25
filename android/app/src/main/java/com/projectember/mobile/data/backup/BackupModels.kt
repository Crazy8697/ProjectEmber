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
    val supplementEntries: List<SupplementEntryDto> = emptyList(),
    val stackDefinitions: List<StackDefinitionDto> = emptyList(),
    val manualHealthEntries: List<ManualHealthEntryDto> = emptyList(),
    val ketoTargets: KetoTargetsDto,
    // Newly added preference coverage
    val theme: ThemeDto = ThemeDto(),
    val units: UnitsDto = UnitsDto(),
    val dailyRhythm: DailyRhythmDto = DailyRhythmDto(),
    val mealTiming: MealTimingDto = MealTimingDto(),
    val healthMetricPreferences: HealthMetricPreferencesDto = HealthMetricPreferencesDto()
)

data class ThemeDto(
    val selectedThemeName: String = "EMBER_DARK"
)

data class UnitsDto(
    val weightUnit: String = "KG",
    val foodWeightUnit: String = "G",
    val volumeUnit: String = "ML"
)

data class DailyRhythmDto(
    val wakeHour: Int = 7,
    val wakeMinute: Int = 0,
    val sleepHour: Int = 23,
    val sleepMinute: Int = 0,
    val eatingStyle: String = "NORMAL_EATER"
)

data class MealTimingDto(
    val breakfastWindow: MealWindowDto? = null,
    val lunchWindow: MealWindowDto? = null,
    val dinnerWindow: MealWindowDto? = null,
    val snackWindow: MealWindowDto? = null
)

data class MealWindowDto(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
)

data class HealthMetricPreferencesDto(
    val enabled: Map<String, Boolean> = emptyMap(),
    val graphEnabled: Map<String, Boolean> = emptyMap()
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

data class SupplementEntryDto(
    val id: Int = 0,
    val name: String,
    val dose: String,
    val unit: String? = null,
    val entryDate: String,
    val entryTime: String,
    val notes: String? = null,
    val stackDefinitionId: Int? = null,
    val ketoEntryId: Int? = null
)

data class ManualHealthEntryDto(
    val id: Int = 0,
    val metricType: String,
    val value1: Double,
    val value2: Double? = null,
    val entryDate: String,
    val entryTime: String,
    val source: String = "manual"
)

data class StackDefinitionDto(
    val id: Int = 0,
    val name: String,
    val defaultDose: String? = null,
    val defaultUnit: String? = null,
    val notes: String? = null,
    val caloriesKcal: Double? = null,
    val proteinG: Double? = null,
    val fatG: Double? = null,
    val netCarbsG: Double? = null,
    val sodiumMg: Double? = null,
    val potassiumMg: Double? = null,
    val magnesiumMg: Double? = null
)
