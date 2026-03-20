package com.projectember.mobile.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single historical body-weight log entry.
 *
 * One row is stored per logging event.  Multiple entries on the same date
 * are allowed; the latest by [id] is used as the "current" value for the
 * dashboard card.  Date format: "yyyy-MM-dd".
 *
 * [source] is null for manually-entered entries.  Health Connect imports set it
 * to [WeightEntry.SOURCE_HEALTH_CONNECT] so the UI can show a provenance badge.
 */
@Entity(
    tableName = "weight_entries",
    indices = [Index(value = ["entryDate"])]
)
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val entryDate: String,   // "yyyy-MM-dd"
    val weightKg: Double,
    /** Null for manually-entered entries; [SOURCE_HEALTH_CONNECT] for HC imports. */
    val source: String? = null
) {
    companion object {
        const val SOURCE_HEALTH_CONNECT = "health_connect"
        const val SOURCE_IMPORT = "import"
    }
}
