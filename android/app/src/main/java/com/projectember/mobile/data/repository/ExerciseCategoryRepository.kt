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

    /**
     * Returns true if a category with the same trimmed, case-insensitive name already exists.
     * Pass [excludeId] > 0 to allow a category to keep its own name (used for future rename
     * support).
     */
    suspend fun nameExists(name: String, excludeId: Int = 0): Boolean =
        if (excludeId > 0)
            dao.countByTrimmedNameExcluding(name, excludeId) > 0
        else
            dao.countByTrimmedName(name) > 0
}

