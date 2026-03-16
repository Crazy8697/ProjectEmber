package com.projectember.mobile.data.repository

import com.projectember.mobile.data.local.dao.SyncStatusDao
import com.projectember.mobile.data.local.entities.SyncStatus
import kotlinx.coroutines.flow.Flow

class SyncRepository(private val syncStatusDao: SyncStatusDao) {
    fun getSyncStatus(): Flow<SyncStatus?> = syncStatusDao.getSyncStatus()
    suspend fun updateSyncStatus(status: SyncStatus) = syncStatusDao.upsert(status)
    suspend fun getLastSyncStatusOnce(): SyncStatus? = syncStatusDao.getSyncStatusOnce()
}
