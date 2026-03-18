package com.projectember.mobile.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "supplement_entries",
    indices = [Index(value = ["entryDate"])]
)
data class SupplementEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val dose: String,
    val unit: String? = null,
    val entryDate: String,   // "yyyy-MM-dd"
    val entryTime: String,   // "HH:mm"
    val notes: String? = null
)
