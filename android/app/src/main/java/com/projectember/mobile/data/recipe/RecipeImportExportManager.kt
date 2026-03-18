package com.projectember.mobile.data.recipe

import android.content.Context
import android.net.Uri
import com.projectember.mobile.data.local.entities.Recipe
import com.projectember.mobile.data.repository.RecipeRepository
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Handles recipe-specific JSON export and import.
 *
 * Export: serialises all recipes to a human-readable, stable JSON format that is suitable
 * for re-import.  IDs are intentionally omitted so imported recipes receive new auto-generated
 * IDs and do not conflict with existing data.
 *
 * Import: reads JSON from a [Uri] or a raw string, validates each recipe object, detects
 * duplicates against the live database, and returns a [RecipeImportPreview] for user
 * confirmation before any data is written.
 *
 * The JSON format uses a top-level `"recipes"` array so that it is easy for power users to
 * hand-edit or script.  Example structure:
 * ```json
 * {
 *   "schemaVersion": 1,
 *   "exportedAt": "2026-03-18T00:00:00Z",
 *   "recipeCount": 2,
 *   "recipes": [
 *     {
 *       "name": "Keto Pancakes",
 *       "category": "Breakfast",
 *       "servings": 4.0,
 *       "calories": 800.0,
 *       "proteinG": 40.0,
 *       "fatG": 60.0,
 *       "netCarbsG": 8.0,
 *       ...
 *     }
 *   ]
 * }
 * ```
 */
class RecipeImportExportManager(
    private val context: Context,
    private val recipeRepository: RecipeRepository,
    private val appVersion: String
) {

    // ── Export ────────────────────────────────────────────────────────────────

    /** Builds the recipe export JSON and returns it as UTF-8 bytes for sharing or saving. */
    suspend fun buildExportBytes(): Result<ByteArray> = runCatching {
        buildExportJson().toByteArray(Charsets.UTF_8)
    }

    /**
     * Writes the recipe export JSON directly to the given [uri] (e.g. from a
     * SAF CreateDocument launcher).
     */
    suspend fun exportToUri(uri: Uri): Result<Unit> = runCatching {
        val bytes = buildExportBytes().getOrThrow()
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: error("Cannot open output stream for export URI")
    }

    private suspend fun buildExportJson(): String {
        val recipes = recipeRepository.getAllOnce()
        val root = JSONObject()
        root.put("schemaVersion", RECIPE_JSON_SCHEMA_VERSION)
        root.put("appVersion", appVersion)
        root.put("exportedAt", Instant.now().toString())
        root.put("recipeCount", recipes.size)

        val arr = JSONArray()
        recipes.forEach { arr.put(it.toExportJson()) }
        root.put("recipes", arr)

        return root.toString(2)
    }

    // ── Import ────────────────────────────────────────────────────────────────

    /**
     * Reads the JSON file at [uri], validates each recipe object, and returns a
     * [RecipeImportPreview] ready for user confirmation.
     *
     * No data is written — call [commitImport] after the user confirms.
     */
    suspend fun parseAndValidateFromUri(uri: Uri): Result<RecipeImportPreview> = runCatching {
        val json = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: error("Cannot open import file")
        parseAndValidate(json).getOrThrow()
    }

    /**
     * Validates a raw JSON string and returns a [RecipeImportPreview].
     * No data is written — call [commitImport] after the user confirms.
     */
    suspend fun parseAndValidate(json: String): Result<RecipeImportPreview> = runCatching {
        val existingRecipes = recipeRepository.getAllOnce()
        val existingByKey = existingRecipes
            .groupBy { normaliseKey(it.name, it.category) }

        val root = runCatching { JSONObject(json) }.getOrElse {
            error("Invalid JSON: ${it.message}")
        }

        val arr: JSONArray = when {
            root.has("recipes") -> root.getJSONArray("recipes")
            else -> error("JSON must contain a top-level \"recipes\" array")
        }

        val valid = mutableListOf<RecipeCandidateItem>()
        val invalid = mutableListOf<RecipeParseError>()

        for (i in 0 until arr.length()) {
            val obj = runCatching { arr.getJSONObject(i) }.getOrElse {
                invalid.add(RecipeParseError(i, "Item at index $i is not a JSON object"))
                continue
            }

            val parseResult = runCatching { parseRecipeObject(obj, i) }
            if (parseResult.isFailure) {
                invalid.add(
                    RecipeParseError(i, parseResult.exceptionOrNull()?.message ?: "Unknown parse error")
                )
                continue
            }

            val candidate = parseResult.getOrThrow()
            val key = normaliseKey(candidate.name, candidate.category)
            val existing = existingByKey[key]?.firstOrNull()

            valid.add(
                RecipeCandidateItem(
                    index = i,
                    candidateRecipe = candidate,
                    isDuplicate = existing != null,
                    existingId = existing?.id
                )
            )
        }

        RecipeImportPreview(
            totalFound = arr.length(),
            valid = valid,
            invalid = invalid
        )
    }

    /**
     * Commits the validated import, applying [duplicateHandling] for duplicate recipes.
     *
     * @return The number of recipes actually written to the database.
     */
    suspend fun commitImport(
        preview: RecipeImportPreview,
        duplicateHandling: DuplicateHandling
    ): Int {
        var count = 0

        for (item in preview.valid) {
            if (item.isDuplicate) {
                when (duplicateHandling) {
                    DuplicateHandling.SKIP -> Unit
                    DuplicateHandling.OVERWRITE -> {
                        recipeRepository.updateRecipe(
                            item.candidateRecipe.copy(id = item.existingId!!)
                        )
                        count++
                    }
                    DuplicateHandling.IMPORT_AS_NEW -> {
                        recipeRepository.insertRecipe(item.candidateRecipe.copy(id = 0))
                        count++
                    }
                }
            } else {
                recipeRepository.insertRecipe(item.candidateRecipe.copy(id = 0))
                count++
            }
        }

        return count
    }

    // ── Parsing helpers ───────────────────────────────────────────────────────

    private fun parseRecipeObject(obj: JSONObject, index: Int): Recipe {
        val name = obj.optString("name", "").trim()
        require(name.isNotBlank()) { "Item at index $index: \"name\" is required and must not be blank" }

        val calories = requireNonNegativeDouble(obj, "calories", name)
        val proteinG = requireNonNegativeDouble(obj, "proteinG", name)
        val fatG = requireNonNegativeDouble(obj, "fatG", name)
        val netCarbsG = requireNonNegativeDouble(obj, "netCarbsG", name)

        val servings = obj.optDouble("servings", 1.0)
        require(!servings.isNaN() && servings > 0) {
            "Recipe \"$name\": \"servings\" must be a positive number (got $servings)"
        }

        return Recipe(
            id = 0,
            name = name,
            category = obj.optString("category", "General").trim().ifBlank { "General" },
            description = obj.optString("description", "").takeIf { it.isNotEmpty() },
            calories = calories,
            proteinG = proteinG,
            fatG = fatG,
            netCarbsG = netCarbsG,
            totalCarbsG = obj.optDouble("totalCarbsG", 0.0).coerceAtLeast(0.0),
            fiberG = obj.optDouble("fiberG", 0.0).coerceAtLeast(0.0),
            sodiumMg = obj.optDouble("sodiumMg", 0.0).coerceAtLeast(0.0),
            potassiumMg = obj.optDouble("potassiumMg", 0.0).coerceAtLeast(0.0),
            magnesiumMg = obj.optDouble("magnesiumMg", 0.0).coerceAtLeast(0.0),
            waterMl = obj.optDouble("waterMl", 0.0).coerceAtLeast(0.0),
            servings = servings,
            ketoNotes = obj.optString("ketoNotes", "").takeIf { it.isNotEmpty() },
            ingredientsRaw = obj.optString("ingredientsRaw", "").takeIf { it.isNotEmpty() }
        )
    }

    private fun requireNonNegativeDouble(obj: JSONObject, key: String, recipeName: String): Double {
        require(obj.has(key)) { "Recipe \"$recipeName\": \"$key\" is required" }
        val v = obj.optDouble(key, Double.NaN)
        require(!v.isNaN()) { "Recipe \"$recipeName\": \"$key\" must be a number" }
        require(v >= 0) { "Recipe \"$recipeName\": \"$key\" must be >= 0 (got $v)" }
        return v
    }

    private fun normaliseKey(name: String, category: String) =
        "${name.trim().lowercase()}|${category.trim().lowercase()}"
}

// ── Export serialisation ──────────────────────────────────────────────────────

/** Serialises a [Recipe] to the portable export JSON format (no id field). */
private fun Recipe.toExportJson() = JSONObject().apply {
    put("name", name)
    put("category", category)
    putOpt("description", description)
    put("servings", servings)
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
    putOpt("ketoNotes", ketoNotes)
    putOpt("ingredientsRaw", ingredientsRaw)
}
