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
    val netCarbsG: Double,
    val servings: Double = 1.0,
    val ketoNotes: String? = null
)
