package com.projectember.mobile.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A manually entered health metric reading stored locally in Ember.
 *
 * Manual entries are distinct from Health Connect imported data and are never
 * written back to Health Connect.  [source] is always [SOURCE_MANUAL] for rows
 * created by this app.
 *
 * [metricType] matches the name() of the corresponding [com.projectember.mobile.data.local.HealthMetric]
 * enum constant (e.g. "HEART_RATE", "BLOOD_PRESSURE").
 *
 * [value1] holds the primary numeric value (BPM, °C, %, etc.).
 * [value2] is used only for metrics that require two values — currently only
 * BLOOD_PRESSURE, where value1 = systolic and value2 = diastolic.
 *
 * [entryDate] format: "yyyy-MM-dd".
 * [entryTime] format: "HH:mm".
 */
@Entity(
    tableName = "manual_health_entries",
    indices = [Index(value = ["metricType", "entryDate"])]
)
data class ManualHealthEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val metricType: String,
    val value1: Double,
    val value2: Double? = null,
    val entryDate: String,
    val entryTime: String,
    val source: String = SOURCE_MANUAL,
) {
    companion object {
        const val SOURCE_MANUAL = "manual"
        /** Entry originated from a Health Connect historical read (never persisted to Room). */
        const val SOURCE_HEALTH_CONNECT = "health_connect"
    }
}
