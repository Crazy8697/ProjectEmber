package com.projectember.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.projectember.mobile.data.local.entities.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncStatusDao {
    @Query("SELECT * FROM sync_status WHERE id = 1")
    fun getSyncStatus(): Flow<SyncStatus?>

    @Query("SELECT * FROM sync_status WHERE id = 1")
    suspend fun getSyncStatusOnce(): SyncStatus?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(status: SyncStatus)
}
