package com.projectember.mobile.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A saved, reusable Stack definition — the "library item".
 *
 * A StackDefinition captures a user's go-to supplement with its default
 * dose/unit/notes. Optionally, nutrition fields allow the supplement to
 * contribute to Keto daily totals when logged.
 *
 * All nutrition fields are nullable; a null value means "not applicable".
 */
@Entity(tableName = "stack_definitions")
data class StackDefinition(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val defaultDose: String? = null,
    val defaultUnit: String? = null,
    val notes: String? = null,

    // ── Optional Keto / nutrition fields ──────────────────────────────────────
    val caloriesKcal: Double? = null,
    val proteinG: Double? = null,
    val fatG: Double? = null,
    val netCarbsG: Double? = null,
    val sodiumMg: Double? = null,
    val potassiumMg: Double? = null,
    val magnesiumMg: Double? = null,
    /** Optional barcode (UPC/EAN/QR) from product packaging scan. */
    val barcode: String? = null
) {
    /** True if this definition carries any nutrition data that can feed Keto. */
    fun hasNutritionData(): Boolean =
        caloriesKcal != null || proteinG != null || fatG != null ||
            netCarbsG != null || sodiumMg != null ||
            potassiumMg != null || magnesiumMg != null
}
