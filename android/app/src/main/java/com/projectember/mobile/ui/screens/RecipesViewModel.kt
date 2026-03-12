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
    }

    val recipes: StateFlow<List<Recipe>> = recipeRepository
        .getAllRecipes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _selectedRecipe = MutableStateFlow<Recipe?>(null)
    val selectedRecipe: StateFlow<Recipe?> = _selectedRecipe

    fun selectRecipe(recipe: Recipe) {
        _selectedRecipe.value = recipe
    }

    fun clearSelectedRecipe() {
        _selectedRecipe.value = null
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
