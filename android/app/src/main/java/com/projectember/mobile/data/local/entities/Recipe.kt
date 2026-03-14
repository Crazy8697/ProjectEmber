package com.projectember.mobile.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val category: String = "General",
    val description: String? = null,
    val calories: Double,
    val proteinG: Double,
    val fatG: Double,
    /** Stored for backward compatibility and keto-log integration. Prefer deriving from totalCarbsG − fiberG. */
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
