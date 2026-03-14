package com.projectember.mobile.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exercise_entries",
    indices = [Index(value = ["entryDate"])]
)
data class ExerciseEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    /** "yyyy-MM-dd" */
    val entryDate: String,
    /** "HH:mm" */
    val entryTime: String,
    /** "yyyy-MM-dd HH:mm" — full sortable timestamp */
    val timestamp: String,
    val type: String,
    val subtype: String? = null,
    val categoryId: Int,
    val notes: String? = null,
    val durationMinutes: Int? = null,
    val caloriesBurned: Double? = null
)
