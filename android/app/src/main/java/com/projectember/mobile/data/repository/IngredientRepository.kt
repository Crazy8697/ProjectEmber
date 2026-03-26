package com.projectember.mobile.data.repository

import com.projectember.mobile.data.local.dao.IngredientDao
import com.projectember.mobile.data.local.entities.Ingredient
import kotlinx.coroutines.flow.Flow

class IngredientRepository(private val dao: IngredientDao) {
    fun getAllIngredients(): Flow<List<Ingredient>> = dao.getAllIngredients()
    suspend fun getById(id: Int): Ingredient? = dao.getById(id)
    suspend fun insert(ingredient: Ingredient): Long = dao.insert(ingredient)
    suspend fun update(ingredient: Ingredient) = dao.update(ingredient)
    suspend fun delete(ingredient: Ingredient) = dao.delete(ingredient)
    suspend fun count(): Int = dao.count()
    suspend fun findByBarcode(barcode: String): Ingredient? = dao.findByBarcode(barcode)
    suspend fun getAllOnce(): List<Ingredient> = dao.getAllIngredientsOnce()
    suspend fun replaceAll(ingredients: List<Ingredient>) {
        dao.deleteAll()
        dao.insertAll(ingredients)
    }
}
