package com.projectember.mobile.import

private const val LB_TO_KG = 0.45359237
private const val OZ_TO_ML = 29.5735
private const val MI_TO_KM = 1.60934
private const val FAHRENHEIT_OFFSET = 32.0
private const val FAHRENHEIT_SCALE = 5.0 / 9.0

private val TIME_KEYS = arrayOf("time", "entryTime", "timestamp")

internal data class ValidationResult(
    val valid: Boolean,
    val reason: String? = null,
    val normalizedDomain: String,
    val normalizedDate: String? = null,
    val normalizedTime: String? = null,
    val primaryValue: Double? = null
)

internal object ImportValidator {
    fun validate(domain: String, item: Map<String, Any?>): ValidationResult {
        val normalizedDomain = domain.lowercase()
        val date = item["date"]?.toString()
        return when (normalizedDomain) {
            "weight" -> validateWeight(normalizedDomain, date, item)
            "calories" -> validateSimpleMetric(normalizedDomain, date, item, arrayOf("calories", "value"), "missing calories")
            "protein" -> validateSimpleMetric(normalizedDomain, date, item, arrayOf("proteinG", "value"), "missing protein (proteinG)")
            "fat" -> validateSimpleMetric(normalizedDomain, date, item, arrayOf("fatG", "value"), "missing fat (fatG)")
            "total_carbs" -> validateSimpleMetric(normalizedDomain, date, item, arrayOf("totalCarbsG", "value"), "missing total carbs (totalCarbsG)")
            "net_carbs" -> validateSimpleMetric(normalizedDomain, date, item, arrayOf("netCarbsG", "value"), "missing net carbs (netCarbsG)")
            "fiber" -> validateSimpleMetric(normalizedDomain, date, item, arrayOf("fiberG", "value"), "missing fiber (fiberG)")
            "hydration" -> validateSimpleMetric(normalizedDomain, date, item, arrayOf("waterMl", "waterOz", "value"), "missing water amount") { valueKey, v ->
                when (valueKey) {
                    "waterMl" -> v
                    "waterOz" -> v?.times(OZ_TO_ML)
                    else -> v
                }
            }
            "sodium" -> validateSimpleMetric(normalizedDomain, date, item, arrayOf("sodiumMg", "value"), "missing sodium (sodiumMg)")
            "potassium" -> validateSimpleMetric(normalizedDomain, date, item, arrayOf("potassiumMg", "value"), "missing potassium (potassiumMg)")
            "magnesium" -> validateSimpleMetric(normalizedDomain, date, item, arrayOf("magnesiumMg", "value"), "missing magnesium (magnesiumMg)")
            "steps" -> validateSimpleMetric(normalizedDomain, date, item, arrayOf("steps", "value"), "missing steps", requireTime = true)
            "distance" -> validateSimpleMetric(normalizedDomain, date, item, arrayOf("distanceKm", "distanceMi", "value"), "missing distance", requireTime = true) { key, v ->
                when (key) {
                    "distanceMi" -> v?.times(MI_TO_KM)
                    else -> v
                }
            }
            "active_calories" -> validateSimpleMetric(normalizedDomain, date, item, arrayOf("caloriesBurned", "calories", "value"), "missing calories burned", requireTime = true)
            "exercise_sessions" -> validateSimpleMetric(normalizedDomain, date, item, arrayOf("sessions", "value"), "missing session count", requireTime = true)
            "heart_rate", "resting_heart_rate" -> validateSimpleMetric(normalizedDomain, date, item, arrayOf("bpm", "value", "value1"), "missing BPM", requireTime = true)
            "sleep" -> validateSimpleMetric(normalizedDomain, date, item, arrayOf("hours", "value"), "missing hours slept", requireTime = true)
            "blood_pressure" -> validateBloodPressure(normalizedDomain, date, item)
            "blood_glucose" -> validateSimpleMetric(normalizedDomain, date, item, arrayOf("mgDl", "value", "value1"), "missing glucose reading", requireTime = true)
            "body_temperature" -> validateSimpleMetric(normalizedDomain, date, item, arrayOf("celsius", "fahrenheit", "value"), "missing temperature", requireTime = true) { key, v ->
                when (key) {
                    "fahrenheit" -> v?.let { (it - FAHRENHEIT_OFFSET) * FAHRENHEIT_SCALE }
                    else -> v
                }
            }
            "oxygen_saturation" -> validateSimpleMetric(normalizedDomain, date, item, arrayOf("spo2", "value", "value1"), "missing SPO2", requireTime = true)
            "respiratory_rate" -> validateSimpleMetric(normalizedDomain, date, item, arrayOf("breathsPerMin", "value", "value1"), "missing respiratory rate", requireTime = true)
            else -> validateSimpleMetric(normalizedDomain, date, item, arrayOf("value", "value1"), "missing value")
        }
    }

    private fun validateWeight(domain: String, date: String?, item: Map<String, Any?>): ValidationResult {
        if (date.isNullOrBlank()) return invalid(domain, "missing date")
        val kg = item.firstNumber("weightKg")
        val lb = item.firstNumber("weightLb")
        val normalized = kg ?: lb?.times(LB_TO_KG)
        return if (normalized == null) {
            invalid(domain, "missing weight (weightKg or weightLb)")
        } else {
            ValidationResult(true, normalizedDomain = domain, normalizedDate = date, normalizedTime = item.firstString(*TIME_KEYS), primaryValue = normalized)
        }
    }

    private fun validateBloodPressure(domain: String, date: String?, item: Map<String, Any?>): ValidationResult {
        if (date.isNullOrBlank()) return invalid(domain, "missing date")
        val time = item.firstString(*TIME_KEYS) ?: return invalid(domain, "missing time")
        val systolic = item.firstNumber("systolic", "value", "value1")
        val diastolic = item.firstNumber("diastolic", "value2")
        return if (systolic == null || diastolic == null) {
            invalid(domain, "missing systolic/diastolic")
        } else {
            ValidationResult(true, normalizedDomain = domain, normalizedDate = date, normalizedTime = time, primaryValue = systolic * 1000 + diastolic)
        }
    }

    private fun validateSimpleMetric(
        domain: String,
        date: String?,
        item: Map<String, Any?>,
        valueKeys: Array<String>,
        missingValueMessage: String,
        requireTime: Boolean = false,
        transform: (key: String, value: Double?) -> Double? = { _, v -> v }
    ): ValidationResult {
        if (date.isNullOrBlank()) return invalid(domain, "missing date")
        val time = if (requireTime) item.firstString(*TIME_KEYS) ?: return invalid(domain, "missing time") else item.firstString(*TIME_KEYS)
        val (keyUsed, rawValue) = firstValueWithKey(item, *valueKeys)
        val transformed = transform(keyUsed ?: "", rawValue)
        return if (transformed == null) {
            invalid(domain, missingValueMessage)
        } else {
            ValidationResult(true, normalizedDomain = domain, normalizedDate = date, normalizedTime = time, primaryValue = transformed)
        }
    }

    private fun invalid(domain: String, reason: String) = ValidationResult(false, reason, domain)

    private fun firstValueWithKey(item: Map<String, Any?>, vararg keys: String): Pair<String?, Double?> {
        for (key in keys) {
            val value = item.firstNumber(key)
            if (value != null) return key to value
        }
        return null to null
    }
}

private fun Map<String, Any?>.firstNumber(vararg keys: String): Double? {
    for (key in keys) {
        val raw = this[key] ?: continue
        val number = when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull()
            else -> null
        }
        if (number != null) return number
    }
    return null
}

private fun Map<String, Any?>.firstString(vararg keys: String): String? {
    for (key in keys) {
        val raw = this[key] ?: continue
        val value = when (raw) {
            is String -> raw
            else -> raw.toString()
        }
        if (value.isNotBlank()) return value
    }
    return null
}

