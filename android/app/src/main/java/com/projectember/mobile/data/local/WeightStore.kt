package com.projectember.mobile.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WeightStore(context: Context) {

    data class WeightEntry(val weightKg: Double, val date: String)

    private val prefs = context.getSharedPreferences("weight_log", Context.MODE_PRIVATE)

    private val _lastEntry = MutableStateFlow(load())
    val lastEntry: StateFlow<WeightEntry?> = _lastEntry.asStateFlow()

    private fun load(): WeightEntry? {
        val kg = prefs.getFloat("weight_kg", 0f).toDouble()
        val date = prefs.getString("weight_date", "") ?: ""
        return if (kg > 0 && date.isNotBlank()) WeightEntry(kg, date) else null
    }

    fun save(weightKg: Double, date: String) {
        prefs.edit()
            .putFloat("weight_kg", weightKg.toFloat())
            .putString("weight_date", date)
            .apply()
        _lastEntry.value = WeightEntry(weightKg, date)
    }
}
