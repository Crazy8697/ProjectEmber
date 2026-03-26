package com.projectember.mobile.data.label

/**
 * Structured result from parsing OCR text off a nutrition facts label.
 *
 * Null = field not found / unknown (do NOT treat as zero).
 * 0.0  = field was explicitly read as zero from the label.
 *
 * [missingFields] lists core fields that could not be extracted.
 * [warnings]      lists fields where OCR text was ambiguous.
 */
data class NutritionParseResult(
    val servingAmount: Double?,
    val servingUnit: String?,
    val calories: Double?,
    val fatG: Double?,
    val proteinG: Double?,
    val totalCarbsG: Double?,
    val fiberG: Double?,
    val sodiumMg: Double?,
    val potassiumMg: Double?,
    val magnesiumMg: Double?,
    val productName: String? = null,
    val warnings: List<String> = emptyList(),
    val missingFields: List<String> = emptyList()
)
