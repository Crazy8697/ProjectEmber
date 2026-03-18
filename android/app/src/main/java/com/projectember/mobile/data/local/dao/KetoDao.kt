package com.projectember.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.projectember.mobile.data.local.entities.KetoEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface KetoDao {
    @Query("SELECT * FROM keto_entries ORDER BY eventTimestamp DESC LIMIT :limit")
    fun getRecentEntries(limit: Int): Flow<List<KetoEntry>>

    @Query("SELECT * FROM keto_entries WHERE entryDate = :date ORDER BY eventTimestamp DESC")
    fun getEntriesForDate(date: String): Flow<List<KetoEntry>>

    @Query("SELECT * FROM keto_entries WHERE id = :id")
    suspend fun getEntryById(id: Int): KetoEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<KetoEntry>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: KetoEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAndReturnId(entry: KetoEntry): Long

    @Update
    suspend fun update(entry: KetoEntry)

    @Delete
    suspend fun delete(entry: KetoEntry)

    @Query("DELETE FROM keto_entries")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM keto_entries")
    suspend fun count(): Int

    @Query("SELECT * FROM keto_entries WHERE entryDate >= :startDate ORDER BY entryDate ASC, eventTimestamp ASC")
    fun getEntriesFromDate(startDate: String): Flow<List<KetoEntry>>

    @Query("SELECT * FROM keto_entries ORDER BY eventTimestamp DESC")
    suspend fun getAllOnce(): List<KetoEntry>

    /**
     * Detaches all keto entries that were logged from the given recipe.
     * Sets recipeId to NULL, preserving the historical entry and all its
     * nutrition values, so the log remains accurate after the recipe is deleted.
     */
    @Query("UPDATE keto_entries SET recipeId = NULL WHERE recipeId = :recipeId")
    suspend fun clearRecipeReference(recipeId: Int)

    /**
     * One-time startup repair: clears recipeId on any keto entry that references
     * a recipe which no longer exists in the recipes table.  Uses NOT EXISTS rather
     * than NOT IN to avoid NULL-semantics pitfalls.  The query is idempotent — it
     * is safe to call on every launch and is a no-op when the data is already clean.
     */
    @Query(
        """
        UPDATE keto_entries
        SET recipeId = NULL
        WHERE recipeId IS NOT NULL
          AND NOT EXISTS (
              SELECT 1 FROM recipes WHERE recipes.id = keto_entries.recipeId
          )
        """
    )
    suspend fun clearDanglingRecipeReferences()
}
