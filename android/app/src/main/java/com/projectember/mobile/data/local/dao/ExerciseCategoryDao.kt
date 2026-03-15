package com.projectember.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.projectember.mobile.data.local.entities.ExerciseCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseCategoryDao {

    @Query("SELECT * FROM exercise_categories ORDER BY isBuiltIn DESC, name ASC")
    fun getAllCategories(): Flow<List<ExerciseCategory>>

    @Query("SELECT * FROM exercise_categories ORDER BY isBuiltIn DESC, name ASC")
    suspend fun getAllCategoriesOnce(): List<ExerciseCategory>

    @Query("SELECT * FROM exercise_categories WHERE id = :id")
    suspend fun getById(id: Int): ExerciseCategory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: ExerciseCategory): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<ExerciseCategory>)

    @Delete
    suspend fun delete(category: ExerciseCategory)

    @Query("SELECT COUNT(*) FROM exercise_categories")
    suspend fun count(): Int

    /**
     * Returns the number of categories whose trimmed, lower-cased name matches [name].
     * Used to prevent duplicate category names (exact, case, and whitespace variants).
     */
    @Query(
        "SELECT COUNT(*) FROM exercise_categories " +
            "WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name))"
    )
    suspend fun countByTrimmedName(name: String): Int

    /**
     * Like [countByTrimmedName] but excludes the category with [excludeId].
     * Used when renaming a category to allow keeping its own name.
     */
    @Query(
        "SELECT COUNT(*) FROM exercise_categories " +
            "WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) AND id != :excludeId"
    )
    suspend fun countByTrimmedNameExcluding(name: String, excludeId: Int): Int

    @Query("DELETE FROM exercise_categories")
    suspend fun deleteAll()
}
