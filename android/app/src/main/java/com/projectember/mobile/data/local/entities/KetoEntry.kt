package com.projectember.mobile.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * All macro fields (calories, proteinG, …) store **per-serving** values.
 * The effective amount contributed to daily totals is always macro × servings.
 *
 * Effective calories toward the daily total: negative for exercise, positive otherwise.
 */
fun KetoEntry.effectiveCalories(): Double =
    if (eventType.equals("exercise", ignoreCase = true)) -(calories * servings) else calories * servings

fun KetoEntry.effectiveProtein(): Double   = proteinG   * servings
fun KetoEntry.effectiveFat(): Double       = fatG       * servings
fun KetoEntry.effectiveNetCarbs(): Double  = netCarbsG  * servings
fun KetoEntry.effectiveWater(): Double     = waterMl    * servings
fun KetoEntry.effectiveSodium(): Double    = sodiumMg   * servings
fun KetoEntry.effectivePotassium(): Double = potassiumMg * servings
fun KetoEntry.effectiveMagnesium(): Double = magnesiumMg * servings

@Entity(tableName = "keto_entries")
data class KetoEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val eventType: String,
    /** Per-serving calories. Effective total = calories × servings. */
    val calories: Double,
    /** Per-serving protein (g). Effective total = proteinG × servings. */
    val proteinG: Double,
    /** Per-serving fat (g). */
    val fatG: Double,
    /** Per-serving net carbs (g). */
    val netCarbsG: Double,
    val waterMl: Double = 0.0,
    val sodiumMg: Double = 0.0,
    val potassiumMg: Double = 0.0,
    val magnesiumMg: Double = 0.0,
    val entryDate: String,
    val eventTimestamp: String,
    val notes: String? = null,
    /** Number of servings consumed. Effective totals = per-serving values × servings.
     *  The SQL migration sets DEFAULT 1.0 for existing database rows; this Kotlin default
     *  is used when constructing new KetoEntry objects in code without specifying servings. */
    val servings: Double = 1.0,
    /** ID of the source Recipe if this entry was logged from the Recipe screen, otherwise null.
     *  When non-null the entry is "recipe-derived" and only servings may be edited. */
    val recipeId: Int? = null
)
