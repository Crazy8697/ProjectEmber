package com.projectember.mobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.local.RecipeCategoryStore
import com.projectember.mobile.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BulkCategoryViewModel(
    private val recipeRepository: RecipeRepository,
    private val recipeCategoryStore: RecipeCategoryStore
) : ViewModel() {

    val recipes = recipeRepository.getAllRecipes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<String>> = recipeCategoryStore.categories

    private val _targetCategory = MutableStateFlow<String?>(null)
    val targetCategory: StateFlow<String?> = _targetCategory.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedIds: StateFlow<Set<Int>> = _selectedIds.asStateFlow()

    /** Non-null while a success message should be displayed. */
    private val _applyResult = MutableStateFlow<String?>(null)
    val applyResult: StateFlow<String?> = _applyResult.asStateFlow()

    /** True only when both a target category and at least one recipe are chosen. */
    val canApply: StateFlow<Boolean> = combine(_targetCategory, _selectedIds) { cat, ids ->
        cat != null && ids.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setTargetCategory(category: String) {
        _targetCategory.value = category
    }

    fun toggleRecipe(id: Int) {
        _selectedIds.update { current -> if (id in current) current - id else current + id }
    }

    fun selectAll() {
        _selectedIds.value = recipes.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun applyCategory() {
        val category = _targetCategory.value ?: return
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val toUpdate = recipeRepository.getAllOnce().filter { it.id in ids }
            toUpdate.forEach { recipe ->
                recipeRepository.updateRecipe(recipe.copy(category = category))
            }
            val count = toUpdate.size
            _applyResult.value = "Updated $count recipe${if (count == 1) "" else "s"}"
            _selectedIds.value = emptySet()
        }
    }

    fun clearApplyResult() {
        _applyResult.value = null
    }
}

class BulkCategoryViewModelFactory(
    private val recipeRepository: RecipeRepository,
    private val recipeCategoryStore: RecipeCategoryStore
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        BulkCategoryViewModel(recipeRepository, recipeCategoryStore) as T
}
