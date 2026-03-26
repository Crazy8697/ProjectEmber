package com.projectember.mobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.local.entities.Ingredient
import com.projectember.mobile.data.repository.IngredientRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class IngredientIndexViewModel(
    private val ingredientRepository: IngredientRepository
) : ViewModel() {

    private val _allIngredients = ingredientRepository.getAllIngredients()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val ingredients: StateFlow<List<Ingredient>> =
        combine(_allIngredients, _searchQuery) { all, query ->
            if (query.isBlank()) all
            else all.filter { it.name.contains(query.trim(), ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _deleteResult = MutableStateFlow<String?>(null)
    val deleteResult: StateFlow<String?> = _deleteResult.asStateFlow()

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun deleteIngredient(ingredient: Ingredient) {
        viewModelScope.launch {
            ingredientRepository.delete(ingredient)
            _deleteResult.value = "\"${ingredient.name}\" deleted"
        }
    }

    fun clearDeleteResult() {
        _deleteResult.value = null
    }
}

class IngredientIndexViewModelFactory(
    private val ingredientRepository: IngredientRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        IngredientIndexViewModel(ingredientRepository) as T
}
