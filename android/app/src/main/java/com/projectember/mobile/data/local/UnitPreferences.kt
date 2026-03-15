package com.projectember.mobile.data.local

// ── Weight (body) ─────────────────────────────────────────────────────────────

enum class WeightUnit(val displayName: String, val symbol: String) {
    KG("Kilograms", "kg"),
    LB("Pounds", "lb");

    /** Convert a value stored in kg to this unit. */
    fun fromKg(kg: Double): Double = when (this) {
        KG -> kg
        LB -> kg * 2.20462
    }

    /** Convert a value in this unit back to kg for storage. */
    fun toKg(value: Double): Double = when (this) {
        KG -> value
        LB -> value / 2.20462
    }
}

// ── Food weight ───────────────────────────────────────────────────────────────

enum class FoodWeightUnit(val displayName: String, val symbol: String) {
    G("Grams", "g"),
    OZ("Ounces", "oz");

    /** Convert a value stored in grams to this unit. */
    fun fromG(grams: Double): Double = when (this) {
        G -> grams
        OZ -> grams / 28.3495
    }

    /** Convert a value in this unit back to grams for storage. */
    fun toG(value: Double): Double = when (this) {
        G -> value
        OZ -> value * 28.3495
    }
}

// ── Volume ────────────────────────────────────────────────────────────────────

enum class VolumeUnit(val displayName: String, val symbol: String) {
    ML("Milliliters", "mL"),
    CUPS("Cups", "cups");

    /** Convert a value stored in mL to this unit. */
    fun fromMl(ml: Double): Double = when (this) {
        ML -> ml
        CUPS -> ml / 236.588
    }

    /** Convert a value in this unit back to mL for storage. */
    fun toMl(value: Double): Double = when (this) {
        ML -> value
        CUPS -> value * 236.588
    }
}

// ── Aggregated preferences ────────────────────────────────────────────────────

data class UnitPreferences(
    val weightUnit: WeightUnit = WeightUnit.KG,
    val foodWeightUnit: FoodWeightUnit = FoodWeightUnit.G,
    val volumeUnit: VolumeUnit = VolumeUnit.ML
)
