package com.projectember.mobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.local.entities.KetoEntry
import com.projectember.mobile.data.local.entities.Recipe
import com.projectember.mobile.data.repository.KetoRepository
import com.projectember.mobile.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class RecipesViewModel(
    private val recipeRepository: RecipeRepository,
    private val ketoRepository: KetoRepository
) : ViewModel() {

    companion object {
        private const val DATE_FORMAT = "yyyy-MM-dd"
        private const val TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm"
        const val ALL_CATEGORIES = "All"
    }

    private val _allRecipes: StateFlow<List<Recipe>> = recipeRepository
        .getAllRecipes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _selectedCategory = MutableStateFlow(ALL_CATEGORIES)
    val selectedCategory: StateFlow<String> = _selectedCategory

    val recipes: StateFlow<List<Recipe>> = combine(_allRecipes, _selectedCategory) { all, cat ->
        if (cat == ALL_CATEGORIES) all else all.filter { it.category == cat }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val availableCategories: StateFlow<List<String>> = _allRecipes
        .map { all -> listOf(ALL_CATEGORIES) + all.map { it.category }.distinct().sorted() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = listOf(ALL_CATEGORIES)
        )

    private val _selectedRecipe = MutableStateFlow<Recipe?>(null)
    val selectedRecipe: StateFlow<Recipe?> = _selectedRecipe

    fun selectRecipe(recipe: Recipe) {
        _selectedRecipe.value = recipe
    }

    fun clearSelectedRecipe() {
        _selectedRecipe.value = null
    }

    fun onCategorySelected(category: String) {
        _selectedCategory.value = category
    }

    fun deleteSelectedRecipe(onDone: () -> Unit) {
        val recipe = _selectedRecipe.value ?: return
        viewModelScope.launch {
            recipeRepository.deleteRecipeById(recipe.id)
            _selectedRecipe.value = null
            onDone()
        }
    }

    fun logRecipeToKeto(recipe: Recipe, onDone: () -> Unit, onError: ((Throwable) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val now = LocalDateTime.now()
                ketoRepository.insertEntry(
                    KetoEntry(
                        label = recipe.name,
                        eventType = "meal",
                        calories = recipe.calories,
                        proteinG = recipe.proteinG,
                        fatG = recipe.fatG,
                        netCarbsG = recipe.netCarbsG,
                        entryDate = now.format(DateTimeFormatter.ofPattern(DATE_FORMAT)),
                        eventTimestamp = now.format(DateTimeFormatter.ofPattern(TIMESTAMP_FORMAT)),
                        notes = recipe.ketoNotes
                    )
                )
                onDone()
            } catch (e: Exception) {
                onError?.invoke(e)
            }
        }
    }
}

class RecipesViewModelFactory(
    private val recipeRepository: RecipeRepository,
    private val ketoRepository: KetoRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        RecipesViewModel(recipeRepository, ketoRepository) as T
}
