package com.projectember.mobile.data.barcode

/**
 * Normalized product data returned from an online barcode lookup.
 * Nutrition values are per-100g where available (matching Ingredient convention).
 * Null means the field was not present in the lookup response.
 */
data class BarcodeProductResult(
    val barcode: String,
    val name: String,
    val brand: String?,
    /** Human-readable serving note from the product label, e.g. "30g" or "1 capsule / 500mg". */
    val servingSizeNote: String?,
    val caloriesKcal: Double?,
    val proteinG: Double?,
    val fatG: Double?,
    val totalCarbsG: Double?,
    val fiberG: Double?,
    val sodiumMg: Double?
)
