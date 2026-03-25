package com.projectember.mobile.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.projectember.mobile.data.local.KetoTargets
import com.projectember.mobile.data.local.KetoTargetsStore
import com.projectember.mobile.data.local.db.AppDatabase
import com.projectember.mobile.data.local.entities.ExerciseCategory
import com.projectember.mobile.data.local.entities.ExerciseEntry
import com.projectember.mobile.data.local.entities.KetoEntry
import com.projectember.mobile.data.local.entities.Recipe
import com.projectember.mobile.data.local.entities.StackDefinition
import com.projectember.mobile.data.local.entities.SupplementEntry
import com.projectember.mobile.data.local.entities.WeightEntry
import com.projectember.mobile.data.repository.ExerciseCategoryRepository
import com.projectember.mobile.data.repository.ExerciseRepository
import com.projectember.mobile.data.repository.KetoRepository
import com.projectember.mobile.data.repository.RecipeRepository
import com.projectember.mobile.data.local.entities.ManualHealthEntry
import com.projectember.mobile.data.repository.ManualHealthEntryRepository
import com.projectember.mobile.data.repository.StackDefinitionRepository
import com.projectember.mobile.data.repository.SupplementRepository
import com.projectember.mobile.data.repository.WeightRepository
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Centralised backup / restore manager.
 *
 * Export: reads all data from the database and SharedPreferences and writes a
 * single versioned JSON file to the caller-supplied [Uri].
 *
 * Import: reads, parses, and validates a file from a [Uri], then asks the
 * caller to confirm before performing a full replace-restore. The entire DB
 * portion of the restore runs inside a single Room transaction so that any
 * failure leaves existing data completely untouched. SharedPreferences (keto
 * targets) are saved only after the transaction commits successfully.
 *
 * Reset:  wipes all app data and resets factory defaults; same transactional
 * guarantee for the DB portion.
 *
 * All IDs are preserved in the backup so that cross-references
 * (KetoEntry.recipeId, ExerciseEntry.categoryId) remain valid after restore.
 */
class BackupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val ketoRepository: KetoRepository,
    private val recipeRepository: RecipeRepository,
    private val exerciseRepository: ExerciseRepository,
    private val exerciseCategoryRepository: ExerciseCategoryRepository,
    private val weightRepository: WeightRepository,
    private val supplementRepository: SupplementRepository,
    private val stackDefinitionRepository: StackDefinitionRepository,
    private val manualHealthEntryRepository: ManualHealthEntryRepository,
    private val ketoTargetsStore: KetoTargetsStore,
    // Preference stores for exporting/restoring preferences
    private val themePreferencesStore: com.projectember.mobile.data.local.ThemePreferencesStore,
    private val unitsPreferencesStore: com.projectember.mobile.data.local.UnitsPreferencesStore,
    private val dailyRhythmStore: com.projectember.mobile.data.local.DailyRhythmStore,
    private val mealTimingStore: com.projectember.mobile.data.local.MealTimingStore,
    private val healthMetricPreferencesStore: com.projectember.mobile.data.local.HealthMetricPreferencesStore,
    private val appVersion: String
) {

    // ── Export ────────────────────────────────────────────────────────────────

    suspend fun exportToUri(uri: Uri): Result<Unit> = runCatching {
        val bytes = buildBackupBytes().getOrThrow()
        context.contentResolver.openOutputStream(uri)?.use { os ->
            os.write(bytes)
        } ?: error("Cannot open output stream for export URI")
    }

    /** Builds the backup payload and returns it as UTF-8 bytes.
     *  Both the local-save and email export paths share this single
     *  backup-generation implementation. */
    suspend fun buildBackupBytes(): Result<ByteArray> = runCatching {
        buildPayloadJson().toByteArray(Charsets.UTF_8)
    }

    // ── Import ────────────────────────────────────────────────────────────────

    /** Reads the file at [uri], parses and validates it.
     *  Returns the payload ready for [restoreFromPayload] if successful. */
    suspend fun readAndValidateImport(uri: Uri): Result<BackupPayloadV1> = runCatching {
        val json = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        } ?: error("Cannot open import file")

        parseAndValidate(json)
    }

    /**
     * Performs the full destructive replace-restore.
     * Call only after user confirmation.
     *
     * The entire DB write runs in a single Room transaction. If anything
     * throws, the transaction is rolled back and existing data is untouched.
     * KetoTargets (SharedPreferences) are written only after the transaction
     * commits so they cannot diverge from the DB state.
     */
    suspend fun restoreFromPayload(payload: BackupPayloadV1) {
        // All DB tables are replaced inside one atomic transaction.
        // Dependency order: parent tables first so referential integrity is maintained
        // throughout the operation.
        database.withTransaction {
            // 1. Recipes before keto entries (KetoEntry.recipeId references Recipe.id)
            recipeRepository.replaceAll(payload.recipes.map { it.toEntity() })
            ketoRepository.replaceAll(payload.ketoEntries.map { it.toEntity() })

            // 2. Exercise categories before exercise entries (ExerciseEntry.categoryId references ExerciseCategory.id)
            exerciseCategoryRepository.replaceAll(payload.exerciseCategories.map { it.toEntity() })
            exerciseRepository.replaceAll(payload.exerciseEntries.map { it.toEntity() })

            // 3. Weight entries have no cross-table dependencies
            weightRepository.replaceAll(payload.weightEntries.map { it.toEntity() })

            // 4. Stack definitions before supplement entries (supplement_entries.stackDefinitionId references stack_definitions.id)
            stackDefinitionRepository.replaceAll(payload.stackDefinitions.map { it.toEntity() })
            supplementRepository.replaceAll(payload.supplementEntries.map { it.toEntity() })

            // 5. Manual health entries have no cross-table dependencies
            manualHealthEntryRepository.replaceAll(payload.manualHealthEntries.map { it.toEntity() })
        }

        // SharedPreferences are saved only after the DB transaction has committed.
        ketoTargetsStore.save(payload.ketoTargets.toKetoTargets())

        // Restore preference stores (non-DB data)
        try {
            // Theme
            try {
                val themeName = payload.theme.selectedThemeName
                com.projectember.mobile.ui.theme.ThemeOption.valueOf(themeName).let {
                    themePreferencesStore.setTheme(it)
                }
            } catch (_: Exception) {}

            // Units
            try {
                unitsPreferencesStore.setWeightUnit(com.projectember.mobile.data.local.WeightUnit.valueOf(payload.units.weightUnit))
                unitsPreferencesStore.setFoodWeightUnit(com.projectember.mobile.data.local.FoodWeightUnit.valueOf(payload.units.foodWeightUnit))
                unitsPreferencesStore.setVolumeUnit(com.projectember.mobile.data.local.VolumeUnit.valueOf(payload.units.volumeUnit))
            } catch (_: Exception) {}

            // Daily rhythm
            try {
                dailyRhythmStore.setWakeTime(payload.dailyRhythm.wakeHour, payload.dailyRhythm.wakeMinute)
                dailyRhythmStore.setSleepTime(payload.dailyRhythm.sleepHour, payload.dailyRhythm.sleepMinute)
                payload.dailyRhythm.eatingStyle.let { s ->
                    try {
                        dailyRhythmStore.setEatingStyle(com.projectember.mobile.data.local.EatingStyle.valueOf(s))
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}

            // Meal timing
            try {
                fun toMealWindow(dto: com.projectember.mobile.data.backup.MealWindowDto?): com.projectember.mobile.data.local.MealWindow? =
                    dto?.let { com.projectember.mobile.data.local.MealWindow(it.startHour, it.startMinute, it.endHour, it.endMinute) }

                mealTimingStore.setBreakfastWindow(toMealWindow(payload.mealTiming.breakfastWindow))
                mealTimingStore.setLunchWindow(toMealWindow(payload.mealTiming.lunchWindow))
                mealTimingStore.setDinnerWindow(toMealWindow(payload.mealTiming.dinnerWindow))
                mealTimingStore.setSnackWindow(toMealWindow(payload.mealTiming.snackWindow))
            } catch (_: Exception) {}

            // Health metric preferences
            try {
                payload.healthMetricPreferences.enabled.forEach { (k, v) ->
                    try {
                        val metric = com.projectember.mobile.data.local.HealthMetric.valueOf(k)
                        healthMetricPreferencesStore.setMetricEnabled(metric, v)
                    } catch (_: Exception) {}
                }
                payload.healthMetricPreferences.graphEnabled.forEach { (k, v) ->
                    try {
                        val metric = com.projectember.mobile.data.local.HealthMetric.valueOf(k)
                        healthMetricPreferencesStore.setMetricGraphEnabled(metric, v)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    /**
     * Deletes all app data and resets preferences to factory defaults.
     * DB wipe runs inside a single Room transaction; SharedPreferences are
     * reset only after the transaction commits.
     */
    suspend fun resetAll() {
        database.withTransaction {
            ketoRepository.replaceAll(emptyList())
            recipeRepository.replaceAll(emptyList())
            exerciseCategoryRepository.replaceAll(emptyList())
            exerciseRepository.replaceAll(emptyList())
            weightRepository.replaceAll(emptyList())
            supplementRepository.replaceAll(emptyList())
            stackDefinitionRepository.replaceAll(emptyList())
            manualHealthEntryRepository.replaceAll(emptyList())
        }
        ketoTargetsStore.save(KetoTargets())
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun buildPayloadJson(): String {
        val root = JSONObject()
        root.put("schemaVersion", BACKUP_SCHEMA_VERSION)
        root.put("appVersion", appVersion)
        root.put("exportedAt", Instant.now().toString())

        root.put("ketoEntries", ketoRepository.getAllOnce().toJsonArray { it.toJson() })
        root.put("recipes", recipeRepository.getAllOnce().toJsonArray { it.toJson() })
        root.put(
            "exerciseCategories",
            exerciseCategoryRepository.getAllCategoriesOnce().toJsonArray { it.toJson() }
        )
        root.put("exerciseEntries", exerciseRepository.getAllOnce().toJsonArray { it.toJson() })
        root.put("weightEntries", weightRepository.getAllOnce().toJsonArray { it.toJson() })
        root.put("supplementEntries", supplementRepository.getAllOnce().toJsonArray { it.toJson() })
        root.put("stackDefinitions", stackDefinitionRepository.getAllOnce().toJsonArray { it.toJson() })
        root.put("manualHealthEntries", manualHealthEntryRepository.getAllOnce().toJsonArray { it.toJson() })
        root.put("ketoTargets", ketoTargetsStore.targets.value.toJson())

        // Preferences: theme, units, daily rhythm, meal timing, health metric prefs
        try {
            val theme = JSONObject().apply {
                put("selectedThemeName", themePreferencesStore.getTheme().name)
            }
            root.put("theme", theme)
        } catch (_: Exception) {}

        try {
            val units = JSONObject().apply {
                val p = unitsPreferencesStore.getPreferences()
                put("weightUnit", p.weightUnit.name)
                put("foodWeightUnit", p.foodWeightUnit.name)
                put("volumeUnit", p.volumeUnit.name)
            }
            root.put("units", units)
        } catch (_: Exception) {}

        try {
            val dr = dailyRhythmStore.getRhythm()
            val drj = JSONObject().apply {
                put("wakeHour", dr.wakeHour)
                put("wakeMinute", dr.wakeMinute)
                put("sleepHour", dr.sleepHour)
                put("sleepMinute", dr.sleepMinute)
                put("eatingStyle", dr.eatingStyle.name)
            }
            root.put("dailyRhythm", drj)
        } catch (_: Exception) {}

        try {
            val mt = mealTimingStore.getMealTiming()
            fun mwToJson(w: com.projectember.mobile.data.local.MealWindow?) = w?.let {
                JSONObject().apply {
                    put("startHour", it.startHour)
                    put("startMinute", it.startMinute)
                    put("endHour", it.endHour)
                    put("endMinute", it.endMinute)
                }
            }
            val mtj = JSONObject().apply {
                mwToJson(mt.breakfastWindow)?.let { put("breakfastWindow", it) }
                mwToJson(mt.lunchWindow)?.let { put("lunchWindow", it) }
                mwToJson(mt.dinnerWindow)?.let { put("dinnerWindow", it) }
                mwToJson(mt.snackWindow)?.let { put("snackWindow", it) }
            }
            root.put("mealTiming", mtj)
        } catch (_: Exception) {}

        try {
            val enabled = healthMetricPreferencesStore.getAllSettings().mapKeys { it.key.name }
            val graph = healthMetricPreferencesStore.getAllGraphSettings().mapKeys { it.key.name }
            val hmj = JSONObject().apply {
                put("enabled", JSONObject(enabled))
                put("graphEnabled", JSONObject(graph))
            }
            root.put("healthMetricPreferences", hmj)
        } catch (_: Exception) {}

        return root.toString(2)
    }

    private fun parseAndValidate(json: String): BackupPayloadV1 {
        val root = runCatching { JSONObject(json) }.getOrElse {
            error("Invalid backup file: not valid JSON")
        }

        val schemaVersion = root.optInt("schemaVersion", -1)
        require(schemaVersion == BACKUP_SCHEMA_VERSION) {
            "Unsupported backup version $schemaVersion. This app supports version $BACKUP_SCHEMA_VERSION."
        }
        val appVer = root.optString("appVersion", "").trim()
        require(appVer.isNotEmpty()) { "Invalid backup: missing appVersion." }

        val recipes = root.optJSONArray("recipes")
            ?.let { parseRecipes(it) } ?: emptyList()
        val ketoEntries = root.optJSONArray("ketoEntries")
            ?.let { parseKetoEntries(it) } ?: emptyList()
        val exerciseCategories = root.optJSONArray("exerciseCategories")
            ?.let { parseExerciseCategories(it) } ?: emptyList()
        val exerciseEntries = root.optJSONArray("exerciseEntries")
            ?.let { parseExerciseEntries(it) } ?: emptyList()
        val weightEntries = root.optJSONArray("weightEntries")
            ?.let { parseWeightEntries(it) } ?: emptyList()
        val supplementEntries = root.optJSONArray("supplementEntries")
            ?.let { parseSupplementEntries(it) } ?: emptyList()
        val stackDefinitions = root.optJSONArray("stackDefinitions")
            ?.let { parseStackDefinitions(it) } ?: emptyList()
        val manualHealthEntries = root.optJSONArray("manualHealthEntries")
            ?.let { parseManualHealthEntries(it) } ?: emptyList()
        val ketoTargets = root.optJSONObject("ketoTargets")
            ?.let { parseKetoTargets(it) } ?: KetoTargetsDto()

        // Optional new preference sections — maintain backward compatibility by falling back to defaults
        val theme = root.optJSONObject("theme")?.let {
            ThemeDto(selectedThemeName = it.optString("selectedThemeName", "EMBER_DARK"))
        } ?: ThemeDto()

        val units = root.optJSONObject("units")?.let {
            UnitsDto(
                weightUnit = it.optString("weightUnit", "KG"),
                foodWeightUnit = it.optString("foodWeightUnit", "G"),
                volumeUnit = it.optString("volumeUnit", "ML")
            )
        } ?: UnitsDto()

        val dailyRhythm = root.optJSONObject("dailyRhythm")?.let {
            DailyRhythmDto(
                wakeHour = it.optInt("wakeHour", 7),
                wakeMinute = it.optInt("wakeMinute", 0),
                sleepHour = it.optInt("sleepHour", 23),
                sleepMinute = it.optInt("sleepMinute", 0),
                eatingStyle = it.optString("eatingStyle", "NORMAL_EATER")
            )
        } ?: DailyRhythmDto()

        val mealTiming = root.optJSONObject("mealTiming")?.let { mt ->
            fun parseWindow(key: String): MealWindowDto? = mt.optJSONObject(key)?.let { w ->
                MealWindowDto(
                    startHour = w.optInt("startHour"),
                    startMinute = w.optInt("startMinute"),
                    endHour = w.optInt("endHour"),
                    endMinute = w.optInt("endMinute")
                )
            }
            MealTimingDto(
                breakfastWindow = parseWindow("breakfastWindow"),
                lunchWindow = parseWindow("lunchWindow"),
                dinnerWindow = parseWindow("dinnerWindow"),
                snackWindow = parseWindow("snackWindow")
            )
        } ?: MealTimingDto()

        val healthMetricPreferences = root.optJSONObject("healthMetricPreferences")?.let { h ->
            val enabled = mutableMapOf<String, Boolean>()
            val graph = mutableMapOf<String, Boolean>()
            h.optJSONObject("enabled")?.let { jo ->
                jo.keys().forEach { k -> enabled[k] = jo.optBoolean(k, true) }
            }
            h.optJSONObject("graphEnabled")?.let { jo ->
                jo.keys().forEach { k -> graph[k] = jo.optBoolean(k, false) }
            }
            HealthMetricPreferencesDto(enabled = enabled, graphEnabled = graph)
        } ?: HealthMetricPreferencesDto()

        // ── Referential integrity checks ──────────────────────────────────────
        val recipeIds = recipes.map { it.id }.toSet()
        val orphanedKetoRefs = ketoEntries.filter { it.recipeId != null && it.recipeId !in recipeIds }
        require(orphanedKetoRefs.isEmpty()) {
            "Invalid backup: ${orphanedKetoRefs.size} keto " +
                "${if (orphanedKetoRefs.size == 1) "entry references" else "entries reference"} " +
                "a recipe ID not present in this backup."
        }

        val categoryIds = exerciseCategories.map { it.id }.toSet()
        val orphanedExerciseRefs = exerciseEntries.filter { it.categoryId !in categoryIds }
        require(orphanedExerciseRefs.isEmpty()) {
            "Invalid backup: ${orphanedExerciseRefs.size} exercise " +
                "${if (orphanedExerciseRefs.size == 1) "entry references" else "entries reference"} " +
                "a category ID not present in this backup."
        }

        return BackupPayloadV1(
            schemaVersion = schemaVersion,
            appVersion = appVer,
            exportedAt = root.optString("exportedAt", ""),
            ketoEntries = ketoEntries,
            recipes = recipes,
            exerciseEntries = exerciseEntries,
            exerciseCategories = exerciseCategories,
            weightEntries = weightEntries,
            supplementEntries = supplementEntries,
            stackDefinitions = stackDefinitions,
            manualHealthEntries = manualHealthEntries,
            ketoTargets = ketoTargets,
            theme = theme,
            units = units,
            dailyRhythm = dailyRhythm,
            mealTiming = mealTiming,
            healthMetricPreferences = healthMetricPreferences
        )
    }
}

// ── JSON serialisation helpers ────────────────────────────────────────────────

private fun <T> List<T>.toJsonArray(block: (T) -> JSONObject): JSONArray {
    val arr = JSONArray()
    forEach { arr.put(block(it)) }
    return arr
}

private fun KetoEntry.toJson() = JSONObject().apply {
    put("id", id)
    put("label", label)
    put("eventType", eventType)
    put("calories", calories)
    put("proteinG", proteinG)
    put("fatG", fatG)
    put("netCarbsG", netCarbsG)
    put("waterMl", waterMl)
    put("sodiumMg", sodiumMg)
    put("potassiumMg", potassiumMg)
    put("magnesiumMg", magnesiumMg)
    put("entryDate", entryDate)
    put("eventTimestamp", eventTimestamp)
    putOpt("notes", notes)
    put("servings", servings)
    putOpt("recipeId", recipeId)
}

private fun Recipe.toJson() = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("category", category)
    putOpt("description", description)
    put("calories", calories)
    put("proteinG", proteinG)
    put("fatG", fatG)
    put("netCarbsG", netCarbsG)
    put("totalCarbsG", totalCarbsG)
    put("fiberG", fiberG)
    put("sodiumMg", sodiumMg)
    put("potassiumMg", potassiumMg)
    put("magnesiumMg", magnesiumMg)
    put("waterMl", waterMl)
    put("servings", servings)
    putOpt("ketoNotes", ketoNotes)
    putOpt("ingredientsRaw", ingredientsRaw)
}

private fun ExerciseEntry.toJson() = JSONObject().apply {
    put("id", id)
    put("entryDate", entryDate)
    put("entryTime", entryTime)
    put("timestamp", timestamp)
    put("type", type)
    putOpt("subtype", subtype)
    put("categoryId", categoryId)
    putOpt("notes", notes)
    putOpt("durationMinutes", durationMinutes)
    putOpt("caloriesBurned", caloriesBurned)
}

private fun ExerciseCategory.toJson() = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("isBuiltIn", isBuiltIn)
}

private fun WeightEntry.toJson() = JSONObject().apply {
    put("id", id)
    put("entryDate", entryDate)
    put("weightKg", weightKg)
    if (source != null) put("source", source)
}

private fun SupplementEntry.toJson() = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("dose", dose)
    putOpt("unit", unit)
    put("entryDate", entryDate)
    put("entryTime", entryTime)
    putOpt("notes", notes)
    putOpt("stackDefinitionId", stackDefinitionId)
    putOpt("ketoEntryId", ketoEntryId)
}

private fun StackDefinition.toJson() = JSONObject().apply {
    put("id", id)
    put("name", name)
    putOpt("defaultDose", defaultDose)
    putOpt("defaultUnit", defaultUnit)
    putOpt("notes", notes)
    putOpt("caloriesKcal", caloriesKcal)
    putOpt("proteinG", proteinG)
    putOpt("fatG", fatG)
    putOpt("netCarbsG", netCarbsG)
    putOpt("sodiumMg", sodiumMg)
    putOpt("potassiumMg", potassiumMg)
    putOpt("magnesiumMg", magnesiumMg)
}

private fun KetoTargets.toJson() = JSONObject().apply {
    put("caloriesKcal", caloriesKcal)
    put("proteinG", proteinG)
    put("fatG", fatG)
    put("netCarbsG", netCarbsG)
    put("waterMl", waterMl)
    put("sodiumMg", sodiumMg)
    put("potassiumMg", potassiumMg)
    put("magnesiumMg", magnesiumMg)
}

// ── JSON deserialisation helpers ──────────────────────────────────────────────

private fun parseKetoEntries(arr: JSONArray): List<KetoEntryDto> =
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        KetoEntryDto(
            id = o.getInt("id"),
            label = o.getString("label"),
            eventType = o.getString("eventType"),
            calories = o.getDouble("calories"),
            proteinG = o.getDouble("proteinG"),
            fatG = o.getDouble("fatG"),
            netCarbsG = o.getDouble("netCarbsG"),
            waterMl = o.optDouble("waterMl", 0.0),
            sodiumMg = o.optDouble("sodiumMg", 0.0),
            potassiumMg = o.optDouble("potassiumMg", 0.0),
            magnesiumMg = o.optDouble("magnesiumMg", 0.0),
            entryDate = o.getString("entryDate"),
            eventTimestamp = o.getString("eventTimestamp"),
            notes = o.optString("notes", "").takeIf { it.isNotEmpty() },
            servings = o.optDouble("servings", 1.0),
            recipeId = if (o.has("recipeId") && !o.isNull("recipeId")) o.getInt("recipeId") else null
        )
    }

private fun parseRecipes(arr: JSONArray): List<RecipeDto> =
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        RecipeDto(
            id = o.getInt("id"),
            name = o.getString("name"),
            category = o.optString("category", "General"),
            description = o.optString("description", "").takeIf { it.isNotEmpty() },
            calories = o.getDouble("calories"),
            proteinG = o.getDouble("proteinG"),
            fatG = o.getDouble("fatG"),
            netCarbsG = o.getDouble("netCarbsG"),
            totalCarbsG = o.optDouble("totalCarbsG", 0.0),
            fiberG = o.optDouble("fiberG", 0.0),
            sodiumMg = o.optDouble("sodiumMg", 0.0),
            potassiumMg = o.optDouble("potassiumMg", 0.0),
            magnesiumMg = o.optDouble("magnesiumMg", 0.0),
            waterMl = o.optDouble("waterMl", 0.0),
            servings = o.optDouble("servings", 1.0),
            ketoNotes = o.optString("ketoNotes", "").takeIf { it.isNotEmpty() },
            ingredientsRaw = o.optString("ingredientsRaw", "").takeIf { it.isNotEmpty() }
        )
    }

private fun parseExerciseCategories(arr: JSONArray): List<ExerciseCategoryDto> =
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        ExerciseCategoryDto(
            id = o.getInt("id"),
            name = o.getString("name"),
            isBuiltIn = o.optBoolean("isBuiltIn", false)
        )
    }

private fun parseExerciseEntries(arr: JSONArray): List<ExerciseEntryDto> =
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        ExerciseEntryDto(
            id = o.getInt("id"),
            entryDate = o.getString("entryDate"),
            entryTime = o.getString("entryTime"),
            timestamp = o.getString("timestamp"),
            type = o.getString("type"),
            subtype = o.optString("subtype", "").takeIf { it.isNotEmpty() },
            categoryId = o.getInt("categoryId"),
            notes = o.optString("notes", "").takeIf { it.isNotEmpty() },
            durationMinutes = if (o.has("durationMinutes") && !o.isNull("durationMinutes"))
                o.getInt("durationMinutes") else null,
            caloriesBurned = if (o.has("caloriesBurned") && !o.isNull("caloriesBurned"))
                o.getDouble("caloriesBurned") else null
        )
    }

private fun parseWeightEntries(arr: JSONArray): List<WeightEntryDto> =
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        WeightEntryDto(
            id = o.getInt("id"),
            entryDate = o.getString("entryDate"),
            weightKg = o.getDouble("weightKg"),
            source = o.optString("source").takeIf { it.isNotEmpty() }
        )
    }

private fun parseSupplementEntries(arr: JSONArray): List<SupplementEntryDto> =
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        SupplementEntryDto(
            id = o.getInt("id"),
            name = o.getString("name"),
            dose = o.getString("dose"),
            unit = o.optString("unit", "").takeIf { it.isNotEmpty() },
            entryDate = o.getString("entryDate"),
            entryTime = o.getString("entryTime"),
            notes = o.optString("notes", "").takeIf { it.isNotEmpty() },
            stackDefinitionId = if (o.has("stackDefinitionId") && !o.isNull("stackDefinitionId"))
                o.getInt("stackDefinitionId") else null,
            ketoEntryId = if (o.has("ketoEntryId") && !o.isNull("ketoEntryId"))
                o.getInt("ketoEntryId") else null
        )
    }

private fun parseStackDefinitions(arr: JSONArray): List<StackDefinitionDto> =
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        fun optDoubleOrNull(key: String): Double? =
            if (o.has(key) && !o.isNull(key)) o.getDouble(key) else null
        StackDefinitionDto(
            id = o.getInt("id"),
            name = o.getString("name"),
            defaultDose = o.optString("defaultDose", "").takeIf { it.isNotEmpty() },
            defaultUnit = o.optString("defaultUnit", "").takeIf { it.isNotEmpty() },
            notes = o.optString("notes", "").takeIf { it.isNotEmpty() },
            caloriesKcal = optDoubleOrNull("caloriesKcal"),
            proteinG = optDoubleOrNull("proteinG"),
            fatG = optDoubleOrNull("fatG"),
            netCarbsG = optDoubleOrNull("netCarbsG"),
            sodiumMg = optDoubleOrNull("sodiumMg"),
            potassiumMg = optDoubleOrNull("potassiumMg"),
            magnesiumMg = optDoubleOrNull("magnesiumMg")
        )
    }

private fun ManualHealthEntry.toJson() = JSONObject().apply {
    put("id", id)
    put("metricType", metricType)
    put("value1", value1)
    if (value2 != null) put("value2", value2)
    put("entryDate", entryDate)
    put("entryTime", entryTime)
    put("source", source)
}

private fun parseManualHealthEntries(arr: JSONArray): List<ManualHealthEntryDto> =
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        ManualHealthEntryDto(
            id = o.getInt("id"),
            metricType = o.getString("metricType"),
            value1 = o.getDouble("value1"),
            value2 = if (o.has("value2") && !o.isNull("value2")) o.getDouble("value2") else null,
            entryDate = o.getString("entryDate"),
            entryTime = o.getString("entryTime"),
            source = o.optString("source", "manual")
        )
    }

private fun parseKetoTargets(o: JSONObject) = KetoTargetsDto(
    caloriesKcal = o.optDouble("caloriesKcal", 2000.0),
    proteinG = o.optDouble("proteinG", 100.0),
    fatG = o.optDouble("fatG", 150.0),
    netCarbsG = o.optDouble("netCarbsG", 20.0),
    waterMl = o.optDouble("waterMl", 2000.0),
    sodiumMg = o.optDouble("sodiumMg", 2300.0),
    potassiumMg = o.optDouble("potassiumMg", 3500.0),
    magnesiumMg = o.optDouble("magnesiumMg", 400.0)
)

// ── DTO → Entity ──────────────────────────────────────────────────────────────

internal fun KetoEntryDto.toEntity() = KetoEntry(
    id = id,
    label = label,
    eventType = eventType,
    calories = calories,
    proteinG = proteinG,
    fatG = fatG,
    netCarbsG = netCarbsG,
    waterMl = waterMl,
    sodiumMg = sodiumMg,
    potassiumMg = potassiumMg,
    magnesiumMg = magnesiumMg,
    entryDate = entryDate,
    eventTimestamp = eventTimestamp,
    notes = notes,
    servings = servings,
    recipeId = recipeId
)

internal fun RecipeDto.toEntity() = Recipe(
    id = id,
    name = name,
    category = category,
    description = description,
    calories = calories,
    proteinG = proteinG,
    fatG = fatG,
    netCarbsG = netCarbsG,
    totalCarbsG = totalCarbsG,
    fiberG = fiberG,
    sodiumMg = sodiumMg,
    potassiumMg = potassiumMg,
    magnesiumMg = magnesiumMg,
    waterMl = waterMl,
    servings = servings,
    ketoNotes = ketoNotes,
    ingredientsRaw = ingredientsRaw
)

internal fun ExerciseCategoryDto.toEntity() = ExerciseCategory(
    id = id,
    name = name,
    isBuiltIn = isBuiltIn
)

internal fun ExerciseEntryDto.toEntity() = ExerciseEntry(
    id = id,
    entryDate = entryDate,
    entryTime = entryTime,
    timestamp = timestamp,
    type = type,
    subtype = subtype,
    categoryId = categoryId,
    notes = notes,
    durationMinutes = durationMinutes,
    caloriesBurned = caloriesBurned
)

internal fun WeightEntryDto.toEntity() = WeightEntry(
    id = id,
    entryDate = entryDate,
    weightKg = weightKg,
    source = source
)

internal fun SupplementEntryDto.toEntity() = SupplementEntry(
    id = id,
    name = name,
    dose = dose,
    unit = unit,
    entryDate = entryDate,
    entryTime = entryTime,
    notes = notes,
    stackDefinitionId = stackDefinitionId,
    ketoEntryId = ketoEntryId
)

internal fun StackDefinitionDto.toEntity() = StackDefinition(
    id = id,
    name = name,
    defaultDose = defaultDose,
    defaultUnit = defaultUnit,
    notes = notes,
    caloriesKcal = caloriesKcal,
    proteinG = proteinG,
    fatG = fatG,
    netCarbsG = netCarbsG,
    sodiumMg = sodiumMg,
    potassiumMg = potassiumMg,
    magnesiumMg = magnesiumMg
)

internal fun ManualHealthEntryDto.toEntity() = ManualHealthEntry(
    id = id,
    metricType = metricType,
    value1 = value1,
    value2 = value2,
    entryDate = entryDate,
    entryTime = entryTime,
    source = source
)

internal fun KetoTargetsDto.toKetoTargets() = KetoTargets(
    caloriesKcal = caloriesKcal,
    proteinG = proteinG,
    fatG = fatG,
    netCarbsG = netCarbsG,
    waterMl = waterMl,
    sodiumMg = sodiumMg,
    potassiumMg = potassiumMg,
    magnesiumMg = magnesiumMg
)
