package com.projectember.mobile.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A reusable ingredient record stored in the Ingredient Index.
 *
 * All nutrition values are stored as totals **per [defaultAmount] [defaultUnit]**.
 * The Recipe Builder scales these values by the ratio of user-entered amount / defaultAmount.
 *
 * Example: defaultAmount=100, defaultUnit="g", calories=165 → entering 150g yields 247.5 kcal.
 *
 * [barcode] and [normalizedName] are both indexed to support fast duplicate detection during
 * barcode scanning without table scans.
 */
@Entity(
    tableName = "ingredients",
    indices = [
        Index(value = ["barcode"]),
        Index(value = ["normalizedName"])
    ]
)
data class Ingredient(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    /**
     * Lowercase, punctuation-stripped version of [name] used for duplicate detection and search.
     * Computed by [com.projectember.mobile.data.barcode.NameNormalizer.normalize].
     */
    val normalizedName: String = "",
    /** Reference serving size for the nutrition values below. */
    val defaultAmount: Double = 100.0,
    /** Unit for the reference serving (e.g. "g", "ml", "oz"). */
    val defaultUnit: String = "g",
    val calories: Double = 0.0,
    val proteinG: Double = 0.0,
    val fatG: Double = 0.0,
    val netCarbsG: Double = 0.0,
    val totalCarbsG: Double = 0.0,
    val fiberG: Double = 0.0,
    val sodiumMg: Double = 0.0,
    val potassiumMg: Double = 0.0,
    val magnesiumMg: Double = 0.0,
    val waterMl: Double = 0.0,
    /** True for the built-in seed set; false for user-created custom ingredients. */
    val isBuiltIn: Boolean = false,
    /** Optional barcode value (UPC, EAN, QR, etc.) scanned from the product packaging. */
    val barcode: String? = null
)
