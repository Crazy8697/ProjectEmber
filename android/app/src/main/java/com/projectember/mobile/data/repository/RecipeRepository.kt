package com.projectember.mobile.data.repository

import com.projectember.mobile.data.local.dao.RecipeDao
import com.projectember.mobile.data.local.entities.Recipe
import kotlinx.coroutines.flow.Flow

class RecipeRepository(private val recipeDao: RecipeDao) {
    fun getAllRecipes(): Flow<List<Recipe>> = recipeDao.getAllRecipes()

    suspend fun getRecipeById(id: Int): Recipe? = recipeDao.getById(id)

    suspend fun insertRecipe(recipe: Recipe): Long = recipeDao.insert(recipe)

    suspend fun updateRecipe(recipe: Recipe) = recipeDao.update(recipe)

    suspend fun deleteRecipeById(id: Int) = recipeDao.deleteById(id)

    suspend fun replaceAll(recipes: List<Recipe>) {
        recipeDao.deleteAll()
        recipeDao.insertAll(recipes)
    }
}
