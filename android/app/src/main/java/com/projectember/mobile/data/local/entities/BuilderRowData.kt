package com.projectember.mobile.data.local.entities

import org.json.JSONArray
import org.json.JSONObject

/**
 * Snapshot of a single ingredient row as assembled in the Recipe Builder.
 *
 * Stored as a JSON array in [Recipe.builderRows] so that the builder can restore
 * its row structure when a built recipe is re-opened for editing in the future.
 *
 * [ingredientName] is a snapshot at build time so display remains correct even if the
 * source [Ingredient] is later renamed or deleted.  [contributionCalories] etc. are the
 * already-scaled contribution of this row to the total recipe nutrition.
 */
data class BuilderRowData(
    val ingredientId: Int,
    val ingredientName: String,
    val amount: Double,
    val unit: String,
    val contributionCalories: Double,
    val contributionProteinG: Double,
    val contributionFatG: Double,
    val contributionNetCarbsG: Double,
    val contributionTotalCarbsG: Double,
    val contributionFiberG: Double,
    val contributionSodiumMg: Double,
    val contributionPotassiumMg: Double,
    val contributionMagnesiumMg: Double,
    val contributionWaterMl: Double
)

fun encodeBuilderRows(rows: List<BuilderRowData>): String? {
    if (rows.isEmpty()) return null
    val arr = JSONArray()
    rows.forEach { row ->
        arr.put(JSONObject().apply {
            put("ingredientId", row.ingredientId)
            put("ingredientName", row.ingredientName)
            put("amount", row.amount)
            put("unit", row.unit)
            put("contributionCalories", row.contributionCalories)
            put("contributionProteinG", row.contributionProteinG)
            put("contributionFatG", row.contributionFatG)
            put("contributionNetCarbsG", row.contributionNetCarbsG)
            put("contributionTotalCarbsG", row.contributionTotalCarbsG)
            put("contributionFiberG", row.contributionFiberG)
            put("contributionSodiumMg", row.contributionSodiumMg)
            put("contributionPotassiumMg", row.contributionPotassiumMg)
            put("contributionMagnesiumMg", row.contributionMagnesiumMg)
            put("contributionWaterMl", row.contributionWaterMl)
        })
    }
    return arr.toString()
}

fun decodeBuilderRows(raw: String?): List<BuilderRowData> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            BuilderRowData(
                ingredientId = obj.optInt("ingredientId", 0),
                ingredientName = obj.optString("ingredientName", ""),
                amount = obj.optDouble("amount", 0.0),
                unit = obj.optString("unit", "g"),
                contributionCalories = obj.optDouble("contributionCalories", 0.0),
                contributionProteinG = obj.optDouble("contributionProteinG", 0.0),
                contributionFatG = obj.optDouble("contributionFatG", 0.0),
                contributionNetCarbsG = obj.optDouble("contributionNetCarbsG", 0.0),
                contributionTotalCarbsG = obj.optDouble("contributionTotalCarbsG", 0.0),
                contributionFiberG = obj.optDouble("contributionFiberG", 0.0),
                contributionSodiumMg = obj.optDouble("contributionSodiumMg", 0.0),
                contributionPotassiumMg = obj.optDouble("contributionPotassiumMg", 0.0),
                contributionMagnesiumMg = obj.optDouble("contributionMagnesiumMg", 0.0),
                contributionWaterMl = obj.optDouble("contributionWaterMl", 0.0)
            )
        }
    }.getOrDefault(emptyList())
}
