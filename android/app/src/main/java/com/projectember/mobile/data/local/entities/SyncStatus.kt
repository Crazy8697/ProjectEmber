package com.projectember.mobile.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_status")
data class SyncStatus(
    @PrimaryKey val id: Int = 1,
    val lastSyncTime: String? = null,
    val status: String,
    val message: String? = null,
    val updatedAt: String
)
