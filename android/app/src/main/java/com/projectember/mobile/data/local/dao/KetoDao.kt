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
}
