package com.projectember.mobile.data.label

import android.util.Log

/**
 * Parses raw OCR text from a nutrition facts label into a [NutritionParseResult].
 *
 * Design goals:
 * - Missing values remain null, never invented.
 * - Explicit zeros on the label become 0.0.
 * - Partial reads still produce useful drafts.
 * - U.S. nutrition label layout is the primary target; others degrade gracefully.
 */
object NutritionLabelParser {

    private const val TAG = "NutritionLabelParser"

    fun parse(rawText: String): NutritionParseResult {
        Log.d(TAG, "OCR_TEXT_CAPTURED: ${rawText.take(400)}")

        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val normLines = lines.map { it.lowercase() }

        val warnings = mutableListOf<String>()
        val missing = mutableListOf<String>()

        val serving = extractServing(lines, normLines)
        val calories = extractNumeric(normLines, listOf("calories", "calorie"))
        val fatG = extractNumeric(normLines, listOf("total fat"))
        val proteinG = extractNumeric(normLines, listOf("protein"))
        val totalCarbsG = extractNumeric(
            normLines,
            listOf("total carbohydrate", "total carb", "carbohydrate", "carbohydrates")
        )
        val fiberG = extractNumeric(normLines, listOf("dietary fiber", "total fiber", "fiber"))
        val sodiumMg = extractNumeric(normLines, listOf("sodium"))
        val potassiumMg = extractNumeric(normLines, listOf("potassium"))
        val magnesiumMg = extractNumeric(normLines, listOf("magnesium"))

        if (calories == null) missing += "calories"
        if (proteinG == null) missing += "protein"
        if (fatG == null) missing += "fat"
        if (totalCarbsG == null) missing += "total carbs"

        val result = NutritionParseResult(
            servingAmount = serving?.first,
            servingUnit = serving?.second,
            calories = calories,
            fatG = fatG,
            proteinG = proteinG,
            totalCarbsG = totalCarbsG,
            fiberG = fiberG,
            sodiumMg = sodiumMg,
            potassiumMg = potassiumMg,
            magnesiumMg = magnesiumMg,
            warnings = warnings,
            missingFields = missing
        )

        if (missing.isEmpty()) {
            Log.d(TAG, "NUTRITION_PARSE_RESULT: all core fields found — $result")
        } else {
            Log.d(TAG, "NUTRITION_PARSE_PARTIAL: missing=$missing — $result")
        }

        return result
    }

    /**
     * Scans normalized lines for the first line matching any of [labels] and extracts a number.
     * Falls back to the following line if the matching line has no number itself.
     * Skips percentage-only values.
     */
    private fun extractNumeric(normLines: List<String>, labels: List<String>): Double? {
        for (i in normLines.indices) {
            val line = normLines[i]
            if (labels.none { label -> line.contains(label) }) continue
            // Prefer a number on the same line (e.g. "Total Fat 4g")
            val fromSameLine = parseFirstNumber(line)
            if (fromSameLine != null) return fromSameLine
            // Fall back to the next line (some label formats put the value on the next line)
            if (i + 1 < normLines.size) {
                val fromNext = parseFirstNumber(normLines[i + 1])
                if (fromNext != null) return fromNext
            }
        }
        return null
    }

    /**
     * Extracts the first numeric value from [line], ignoring bare "%" values.
     * Handles: "4g", "230mg", "4.5 g", "110", "0".
     * Returns null if no number is found.
     */
    private fun parseFirstNumber(line: String): Double? {
        // Remove percentage tokens (e.g. "5%", "10 %") to avoid confusing %DV with nutrient amounts.
        val withoutPercent = line.replace(Regex("\\d+\\s*%"), " ").trim()
        if (withoutPercent.isBlank()) return null
        val match = Regex("(\\d+\\.?\\d*)\\s*(?:g|mg|mcg|kcal|cal)?\\b").find(withoutPercent)
        return match?.groupValues?.get(1)?.toDoubleOrNull()
    }

    /**
     * Extracts serving size as Pair(amount, unit).
     *
     * Priority order:
     * 1. Parenthetical metric: "Serving Size 1 cup (240g)" → (240.0, "g")
     * 2. Direct metric at line end: "Serving Size 28g"      → (28.0, "g")
     * 3. First number + word after "size": "Serving Size 2 oz" → (2.0, "oz")
     *
     * Returns null if no serving line is found or no parseable amount.
     */
    private fun extractServing(lines: List<String>, normLines: List<String>): Pair<Double, String>? {
        for (i in normLines.indices) {
            if (!normLines[i].contains("serving size")) continue
            val raw = lines.getOrElse(i) { normLines[i] }

            // 1. Parenthetical metric: (240g) or ( 240 ml )
            val paren = Regex("\\(\\s*(\\d+\\.?\\d*)\\s*(g|ml)\\s*\\)").find(raw)
            if (paren != null) {
                val amount = paren.groupValues[1].toDoubleOrNull() ?: continue
                return Pair(amount, paren.groupValues[2].lowercase())
            }

            // 2. Direct metric after "size": "28g", "100 ml"
            val afterSize = raw.lowercase().substringAfter("size").trim()
            val direct = Regex("(\\d+\\.?\\d*)\\s*(g|ml)\\b").find(afterSize)
            if (direct != null) {
                val amount = direct.groupValues[1].toDoubleOrNull() ?: continue
                return Pair(amount, direct.groupValues[2].lowercase())
            }

            // 3. First number + next word as unit (e.g. "1 cup", "2 oz", "1 tbsp")
            val fallback = Regex("(\\d+\\.?\\d*)\\s+([a-z]+)").find(afterSize)
            if (fallback != null) {
                val amount = fallback.groupValues[1].toDoubleOrNull() ?: continue
                val unit = fallback.groupValues[2]
                // Skip noise words
                if (unit !in setOf("per", "about", "approx", "serving", "container")) {
                    return Pair(amount, unit)
                }
            }
        }
        return null
    }
}
