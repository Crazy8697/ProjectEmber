package com.projectember.mobile.data.repository

import com.projectember.mobile.data.local.dao.ExerciseCategoryDao
import com.projectember.mobile.data.local.entities.ExerciseCategory
import kotlinx.coroutines.flow.Flow

class ExerciseCategoryRepository(private val dao: ExerciseCategoryDao) {
    fun getAllCategories(): Flow<List<ExerciseCategory>> = dao.getAllCategories()
    suspend fun getAllCategoriesOnce(): List<ExerciseCategory> = dao.getAllCategoriesOnce()
    suspend fun getById(id: Int): ExerciseCategory? = dao.getById(id)
    suspend fun insert(category: ExerciseCategory): Long = dao.insert(category)
    suspend fun insertAll(categories: List<ExerciseCategory>) = dao.insertAll(categories)
    suspend fun delete(category: ExerciseCategory) = dao.delete(category)
    suspend fun count(): Int = dao.count()
}
