package com.projectember.mobile.data.local

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "units_prefs"
private const val KEY_WEIGHT_UNIT = "weight_unit"
private const val KEY_FOOD_WEIGHT_UNIT = "food_weight_unit"
private const val KEY_VOLUME_UNIT = "volume_unit"

/**
 * Stores and retrieves unit preferences using SharedPreferences.
 * Conversions occur at the display/input boundary — stored values always remain
 * in base units (kg, g, mL).
 */
class UnitsPreferencesStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _preferences = MutableStateFlow(loadFromPrefs())
    val preferencesFlow: Flow<UnitPreferences> = _preferences.asStateFlow()

    fun getPreferences(): UnitPreferences = _preferences.value

    fun setWeightUnit(unit: WeightUnit) {
        prefs.edit().putString(KEY_WEIGHT_UNIT, unit.name).apply()
        _preferences.value = _preferences.value.copy(weightUnit = unit)
    }

    fun setFoodWeightUnit(unit: FoodWeightUnit) {
        prefs.edit().putString(KEY_FOOD_WEIGHT_UNIT, unit.name).apply()
        _preferences.value = _preferences.value.copy(foodWeightUnit = unit)
    }

    fun setVolumeUnit(unit: VolumeUnit) {
        prefs.edit().putString(KEY_VOLUME_UNIT, unit.name).apply()
        _preferences.value = _preferences.value.copy(volumeUnit = unit)
    }

    private fun loadFromPrefs(): UnitPreferences {
        val weightUnit = runCatching {
            WeightUnit.valueOf(prefs.getString(KEY_WEIGHT_UNIT, WeightUnit.KG.name)!!)
        }.getOrDefault(WeightUnit.KG)

        val foodWeightUnit = runCatching {
            FoodWeightUnit.valueOf(prefs.getString(KEY_FOOD_WEIGHT_UNIT, FoodWeightUnit.G.name)!!)
        }.getOrDefault(FoodWeightUnit.G)

        val volumeUnit = runCatching {
            VolumeUnit.valueOf(prefs.getString(KEY_VOLUME_UNIT, VolumeUnit.ML.name)!!)
        }.getOrDefault(VolumeUnit.ML)

        return UnitPreferences(
            weightUnit = weightUnit,
            foodWeightUnit = foodWeightUnit,
            volumeUnit = volumeUnit
        )
    }
}
