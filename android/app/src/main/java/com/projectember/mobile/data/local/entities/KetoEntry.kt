package com.projectember.mobile.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "keto_entries")
data class KetoEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val eventType: String,
    val calories: Double,
    val proteinG: Double,
    val fatG: Double,
    val netCarbsG: Double,
    val entryDate: String,
    val eventTimestamp: String,
    val notes: String? = null
)
