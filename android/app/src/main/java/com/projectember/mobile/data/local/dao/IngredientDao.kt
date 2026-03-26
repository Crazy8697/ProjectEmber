package com.projectember.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.projectember.mobile.data.local.entities.Ingredient
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientDao {
    @Query("SELECT * FROM ingredients ORDER BY name ASC")
    fun getAllIngredients(): Flow<List<Ingredient>>

    @Query("SELECT * FROM ingredients WHERE id = :id")
    suspend fun getById(id: Int): Ingredient?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ingredient: Ingredient): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(ingredients: List<Ingredient>)

    @Update
    suspend fun update(ingredient: Ingredient)

    @Delete
    suspend fun delete(ingredient: Ingredient)

    @Query("SELECT COUNT(*) FROM ingredients")
    suspend fun count(): Int

    @Query("SELECT * FROM ingredients WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): Ingredient?

    @Query("SELECT * FROM ingredients ORDER BY name ASC")
    suspend fun getAllIngredientsOnce(): List<Ingredient>

    @Query("DELETE FROM ingredients")
    suspend fun deleteAll()
}
