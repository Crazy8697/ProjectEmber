package com.projectember.mobile.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * All nutrition fields (calories, proteinG, …) store **total recipe** values for the whole recipe
 * (i.e. all servings combined).  Per-serving values are derived: `totalField / servings`.
 *
 * [servings] is the number of servings the full recipe yields.
 */
@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val category: String = "General",
    val description: String? = null,
    /** Total calories for the whole recipe. Per-serving = calories / servings. */
    val calories: Double,
    /** Total protein (g) for the whole recipe. */
    val proteinG: Double,
    /** Total fat (g) for the whole recipe. */
    val fatG: Double,
    /** Stored for backward compatibility and keto-log integration. Prefer deriving from totalCarbsG − fiberG. */
    val netCarbsG: Double,
    val totalCarbsG: Double = 0.0,
    val fiberG: Double = 0.0,
    val sodiumMg: Double = 0.0,
    val potassiumMg: Double = 0.0,
    val magnesiumMg: Double = 0.0,
    val waterMl: Double = 0.0,
    /** Number of servings the full recipe yields. All nutrition fields are for the whole recipe. */
    val servings: Double = 1.0,
    val ketoNotes: String? = null,
    val ingredientsRaw: String? = null
)
