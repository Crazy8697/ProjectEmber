package com.projectember.mobile.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "recipe_category_prefs"
private const val KEY_CATEGORIES = "categories"

class RecipeCategoryStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Default categories mirror AddEditRecipeViewModel.CATEGORIES
    private val defaults = listOf(
        "General",
        "Breakfast",
        "Lunch",
        "Dinner",
        "Snack",
        "Side",
        "Sauce",
        "Drink",
        "Dessert"
    )

    private val initial: List<String> = prefs.getStringSet(KEY_CATEGORIES, null)
        ?.toList()?.sorted() ?: defaults

    private val _categories = MutableStateFlow(initial)
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        if (_categories.value.any { it.equals(trimmed, ignoreCase = true) }) return
        val new = (_categories.value + trimmed).sorted()
        prefs.edit().putStringSet(KEY_CATEGORIES, new.toSet()).apply()
        _categories.value = new
    }

    fun deleteCategory(name: String) {
        val new = _categories.value.filterNot { it.equals(name, ignoreCase = true) }
            .ifEmpty { defaults }
        prefs.edit().putStringSet(KEY_CATEGORIES, new.toSet()).apply()
        _categories.value = new
    }

    fun renameCategory(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        val new = _categories.value.map { if (it == oldName) trimmed else it }.distinct().sorted()
        prefs.edit().putStringSet(KEY_CATEGORIES, new.toSet()).apply()
        _categories.value = new
    }
}

