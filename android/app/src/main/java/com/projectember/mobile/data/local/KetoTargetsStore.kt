package com.projectember.mobile.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class KetoTargetsStore(context: Context) {
    private val prefs = context.getSharedPreferences("keto_targets", Context.MODE_PRIVATE)

    private val _targets = MutableStateFlow(load())
    val targets: StateFlow<KetoTargets> = _targets.asStateFlow()

    private fun load() = KetoTargets(
        caloriesKcal = prefs.getFloat("calories", 2000f).toDouble(),
        proteinG = prefs.getFloat("protein", 100f).toDouble(),
        fatG = prefs.getFloat("fat", 150f).toDouble(),
        netCarbsG = prefs.getFloat("net_carbs", 20f).toDouble(),
        waterMl = prefs.getFloat("water", 2000f).toDouble(),
        sodiumMg = prefs.getFloat("sodium", 2300f).toDouble(),
        potassiumMg = prefs.getFloat("potassium", 3500f).toDouble(),
        magnesiumMg = prefs.getFloat("magnesium", 400f).toDouble()
    )

    fun save(targets: KetoTargets) {
        prefs.edit()
            .putFloat("calories", targets.caloriesKcal.toFloat())
            .putFloat("protein", targets.proteinG.toFloat())
            .putFloat("fat", targets.fatG.toFloat())
            .putFloat("net_carbs", targets.netCarbsG.toFloat())
            .putFloat("water", targets.waterMl.toFloat())
            .putFloat("sodium", targets.sodiumMg.toFloat())
            .putFloat("potassium", targets.potassiumMg.toFloat())
            .putFloat("magnesium", targets.magnesiumMg.toFloat())
            .apply()
        _targets.value = targets
    }
}
