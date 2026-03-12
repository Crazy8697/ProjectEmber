package com.projectember.mobile.data.repository

import com.projectember.mobile.data.local.dao.KetoDao
import com.projectember.mobile.data.local.entities.KetoEntry
import kotlinx.coroutines.flow.Flow

class KetoRepository(private val ketoDao: KetoDao) {
    fun getRecentEntries(limit: Int): Flow<List<KetoEntry>> = ketoDao.getRecentEntries(limit)
    fun getEntriesForDate(date: String): Flow<List<KetoEntry>> = ketoDao.getEntriesForDate(date)

    suspend fun insertEntry(entry: KetoEntry) {
        ketoDao.insert(entry)
    }

    suspend fun replaceAll(entries: List<KetoEntry>) {
        ketoDao.deleteAll()
        ketoDao.insertAll(entries)
    }
}
