package com.projectember.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.projectember.mobile.data.local.entities.ExerciseEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseEntryDao {

    @Query("SELECT * FROM exercise_entries WHERE entryDate = :date ORDER BY timestamp DESC")
    fun getEntriesForDate(date: String): Flow<List<ExerciseEntry>>

    @Query("SELECT * FROM exercise_entries ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEntries(limit: Int): Flow<List<ExerciseEntry>>

    @Query("SELECT * FROM exercise_entries WHERE id = :id")
    suspend fun getEntryById(id: Int): ExerciseEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ExerciseEntry)

    @Update
    suspend fun update(entry: ExerciseEntry)

    @Delete
    suspend fun delete(entry: ExerciseEntry)

    @Query("DELETE FROM exercise_entries")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM exercise_entries")
    suspend fun count(): Int

    /** Returns the number of entries that reference the given category. */
    @Query("SELECT COUNT(*) FROM exercise_entries WHERE categoryId = :categoryId")
    suspend fun countEntriesForCategory(categoryId: Int): Int

    @Query("SELECT * FROM exercise_entries ORDER BY timestamp DESC")
    suspend fun getAllOnce(): List<ExerciseEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<ExerciseEntry>)
}
