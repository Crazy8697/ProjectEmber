package com.projectember.mobile.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.local.entities.Recipe
import com.projectember.mobile.data.repository.RecipeRepository
import kotlinx.coroutines.launch

class AddEditRecipeViewModel(
    private val recipeRepository: RecipeRepository,
    private val editRecipeId: Int? = null
) : ViewModel() {

    companion object {
        val CATEGORIES = listOf(
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
    }

    val isEditMode: Boolean get() = editRecipeId != null

    private var originalRecipe: Recipe? = null

    var name by mutableStateOf("")
        private set
    var category by mutableStateOf("General")
        private set
    var description by mutableStateOf("")
        private set

    var nameError by mutableStateOf<String?>(null)
        private set

    init {
        if (editRecipeId != null) {
            viewModelScope.launch {
                val recipe = recipeRepository.getRecipeById(editRecipeId)
                if (recipe != null) {
                    originalRecipe = recipe
                    name = recipe.name
                    category = recipe.category
                    description = recipe.description ?: ""
                }
            }
        }
    }

    fun onNameChange(value: String) {
        name = value
        nameError = null
    }

    fun onCategoryChange(value: String) {
        category = value
    }

    fun onDescriptionChange(value: String) {
        description = value
    }

    fun save(onSuccess: () -> Unit) {
        if (name.isBlank()) {
            nameError = "Recipe name is required"
            return
        }

        viewModelScope.launch {
            val existing = originalRecipe
            if (existing != null) {
                recipeRepository.updateRecipe(
                    existing.copy(
                        name = name.trim(),
                        category = category,
                        description = description.trim().ifBlank { null }
                    )
                )
            } else {
                recipeRepository.insertRecipe(
                    Recipe(
                        name = name.trim(),
                        category = category,
                        description = description.trim().ifBlank { null },
                        calories = 0.0,
                        proteinG = 0.0,
                        fatG = 0.0,
                        netCarbsG = 0.0
                    )
                )
            }
            onSuccess()
        }
    }

    fun deleteRecipe(onSuccess: () -> Unit) {
        val id = editRecipeId ?: return
        viewModelScope.launch {
            recipeRepository.deleteRecipeById(id)
            onSuccess()
        }
    }
}

class AddEditRecipeViewModelFactory(
    private val recipeRepository: RecipeRepository,
    private val editRecipeId: Int? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AddEditRecipeViewModel(recipeRepository, editRecipeId) as T
}
