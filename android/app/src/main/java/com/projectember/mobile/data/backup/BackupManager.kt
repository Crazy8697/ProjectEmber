package com.projectember.mobile.data.backup

import android.content.Context
import android.net.Uri
import com.projectember.mobile.data.local.KetoTargets
import com.projectember.mobile.data.local.KetoTargetsStore
import com.projectember.mobile.data.local.entities.ExerciseCategory
import com.projectember.mobile.data.local.entities.ExerciseEntry
import com.projectember.mobile.data.local.entities.KetoEntry
import com.projectember.mobile.data.local.entities.Recipe
import com.projectember.mobile.data.local.entities.WeightEntry
import com.projectember.mobile.data.repository.ExerciseCategoryRepository
import com.projectember.mobile.data.repository.ExerciseRepository
import com.projectember.mobile.data.repository.KetoRepository
import com.projectember.mobile.data.repository.RecipeRepository
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
 * caller to confirm before performing a full replace-restore.
 *
 * Reset:  wipes all app data and restores factory defaults.
 *
 * All IDs are preserved in the backup so that cross-references
 * (KetoEntry.recipeId, ExerciseEntry.categoryId) remain valid after restore.
 */
class BackupManager(
    private val context: Context,
    private val ketoRepository: KetoRepository,
    private val recipeRepository: RecipeRepository,
    private val exerciseRepository: ExerciseRepository,
    private val exerciseCategoryRepository: ExerciseCategoryRepository,
    private val weightRepository: WeightRepository,
    private val ketoTargetsStore: KetoTargetsStore,
    private val appVersion: String
) {

    // ── Export ────────────────────────────────────────────────────────────────

    suspend fun exportToUri(uri: Uri): Result<Unit> = runCatching {
        val json = buildPayloadJson()
        context.contentResolver.openOutputStream(uri)?.use { os ->
            os.write(json.toByteArray(Charsets.UTF_8))
        } ?: error("Cannot open output stream for export URI")
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

    /** Performs the full destructive replace-restore. Call only after user confirmation. */
    suspend fun restoreFromPayload(payload: BackupPayloadV1) {
        // Restore in dependency order: categories before entries that reference them,
        // recipes before keto entries that reference them.
        ketoRepository.replaceAll(payload.ketoEntries.map { it.toEntity() })
        recipeRepository.replaceAll(payload.recipes.map { it.toEntity() })
        weightRepository.replaceAll(payload.weightEntries.map { it.toEntity() })
        exerciseCategoryRepository.replaceAll(payload.exerciseCategories.map { it.toEntity() })
        exerciseRepository.replaceAll(payload.exerciseEntries.map { it.toEntity() })
        ketoTargetsStore.save(payload.ketoTargets.toKetoTargets())
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    /** Deletes all app data and resets preferences to factory defaults. */
    suspend fun resetAll() {
        ketoRepository.replaceAll(emptyList())
        recipeRepository.replaceAll(emptyList())
        weightRepository.replaceAll(emptyList())
        exerciseCategoryRepository.replaceAll(emptyList())
        exerciseRepository.replaceAll(emptyList())
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
        root.put("ketoTargets", ketoTargetsStore.targets.value.toJson())

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

        val ketoEntries = root.optJSONArray("ketoEntries")
            ?.let { parseKetoEntries(it) } ?: emptyList()
        val recipes = root.optJSONArray("recipes")
            ?.let { parseRecipes(it) } ?: emptyList()
        val exerciseCategories = root.optJSONArray("exerciseCategories")
            ?.let { parseExerciseCategories(it) } ?: emptyList()
        val exerciseEntries = root.optJSONArray("exerciseEntries")
            ?.let { parseExerciseEntries(it) } ?: emptyList()
        val weightEntries = root.optJSONArray("weightEntries")
            ?.let { parseWeightEntries(it) } ?: emptyList()
        val ketoTargets = root.optJSONObject("ketoTargets")
            ?.let { parseKetoTargets(it) } ?: KetoTargetsDto()

        return BackupPayloadV1(
            schemaVersion = schemaVersion,
            appVersion = appVer,
            exportedAt = root.optString("exportedAt", ""),
            ketoEntries = ketoEntries,
            recipes = recipes,
            exerciseEntries = exerciseEntries,
            exerciseCategories = exerciseCategories,
            weightEntries = weightEntries,
            ketoTargets = ketoTargets
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
            weightKg = o.getDouble("weightKg")
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
    weightKg = weightKg
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
