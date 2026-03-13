package com.projectember.mobile.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Calories counted toward daily total: negative for exercise, positive otherwise. */
fun KetoEntry.effectiveCalories(): Double =
    if (eventType.equals("exercise", ignoreCase = true)) -calories else calories

@Entity(tableName = "keto_entries")
data class KetoEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
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
    val notes: String? = null
)
