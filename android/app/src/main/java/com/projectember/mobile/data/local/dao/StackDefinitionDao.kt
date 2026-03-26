package com.projectember.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.projectember.mobile.data.local.entities.StackDefinition
import kotlinx.coroutines.flow.Flow

@Dao
interface StackDefinitionDao {

    @Query("SELECT * FROM stack_definitions ORDER BY name ASC")
    fun getAll(): Flow<List<StackDefinition>>

    @Query("SELECT * FROM stack_definitions WHERE id = :id")
    suspend fun getById(id: Int): StackDefinition?

    @Insert
    suspend fun insert(definition: StackDefinition): Long

    @Update
    suspend fun update(definition: StackDefinition)

    @Delete
    suspend fun delete(definition: StackDefinition)

    @Query("SELECT * FROM stack_definitions ORDER BY name ASC")
    suspend fun getAllOnce(): List<StackDefinition>

    @Query("SELECT * FROM stack_definitions WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): StackDefinition?

    @Query("DELETE FROM stack_definitions")
    suspend fun deleteAll()
}
