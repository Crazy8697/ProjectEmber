package com.projectember.mobile.data.local.entities

/**
 * A lightweight in-memory representation of a single recipe ingredient.
 * Not stored as its own Room entity; serialised into [Recipe.ingredientsRaw]
 * using a simple line-per-ingredient, tab-separated encoding:
 *   "<name>\t<amount>"  (amount may be blank)
 */
data class RecipeIngredient(
    val name: String,
    val amount: String = ""
)

/** Serialise a list of ingredients to a storable string (or null if empty). */
fun encodeIngredients(items: List<RecipeIngredient>): String? {
    // Filter blank names; strip tabs from both fields to keep the delimiter unambiguous.
    val meaningful = items.filter { it.name.isNotBlank() }
    if (meaningful.isEmpty()) return null
    return meaningful.joinToString("\n") {
        "${it.name.trim().replace("\t", " ")}\t${it.amount.trim().replace("\t", " ")}"
    }
}

/** Deserialise a stored ingredients string back to a list. */
fun decodeIngredients(raw: String?): List<RecipeIngredient> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.lines().mapNotNull { line ->
        val parts = line.split("\t", limit = 2)
        val name = parts.getOrNull(0)?.trim().orEmpty()
        if (name.isBlank()) return@mapNotNull null
        RecipeIngredient(name = name, amount = parts.getOrNull(1)?.trim().orEmpty())
    }
}
