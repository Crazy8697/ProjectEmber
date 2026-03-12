package com.projectember.mobile.data.repository

import com.projectember.mobile.data.local.dao.RecipeDao
import com.projectember.mobile.data.local.entities.Recipe
import kotlinx.coroutines.flow.Flow

class RecipeRepository(private val recipeDao: RecipeDao) {
    fun getAllRecipes(): Flow<List<Recipe>> = recipeDao.getAllRecipes()

    suspend fun replaceAll(recipes: List<Recipe>) {
        recipeDao.deleteAll()
        recipeDao.insertAll(recipes)
    }
}
