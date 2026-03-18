package com.projectember.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.projectember.mobile.data.local.entities.SupplementEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplementEntryDao {

    @Query("SELECT * FROM supplement_entries ORDER BY entryDate DESC, entryTime DESC, id DESC")
    fun getAll(): Flow<List<SupplementEntry>>

    @Query("SELECT * FROM supplement_entries WHERE id = :id")
    suspend fun getById(id: Int): SupplementEntry?

    @Insert
    suspend fun insert(entry: SupplementEntry): Long

    @Update
    suspend fun update(entry: SupplementEntry)

    @Delete
    suspend fun delete(entry: SupplementEntry)

    @Query("SELECT * FROM supplement_entries ORDER BY entryDate ASC, entryTime ASC, id ASC")
    suspend fun getAllOnce(): List<SupplementEntry>

    @Query("DELETE FROM supplement_entries")
    suspend fun deleteAll()
}
