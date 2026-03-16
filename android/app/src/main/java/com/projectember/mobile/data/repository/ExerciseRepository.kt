package com.projectember.mobile.data.repository

import com.projectember.mobile.data.local.dao.ExerciseEntryDao
import com.projectember.mobile.data.local.entities.ExerciseEntry
import kotlinx.coroutines.flow.Flow

class ExerciseRepository(private val dao: ExerciseEntryDao) {
    fun getEntriesForDate(date: String): Flow<List<ExerciseEntry>> = dao.getEntriesForDate(date)
    fun getRecentEntries(limit: Int): Flow<List<ExerciseEntry>> = dao.getRecentEntries(limit)
    suspend fun getEntryById(id: Int): ExerciseEntry? = dao.getEntryById(id)
    suspend fun insertEntry(entry: ExerciseEntry) = dao.insert(entry)
    suspend fun updateEntry(entry: ExerciseEntry) = dao.update(entry)
    suspend fun deleteEntry(entry: ExerciseEntry) = dao.delete(entry)
    suspend fun countEntriesForCategory(categoryId: Int): Int =
        dao.countEntriesForCategory(categoryId)

    suspend fun getAllOnce(): List<ExerciseEntry> = dao.getAllOnce()

    /** Returns set of timestamps for entries previously imported from Health Connect. */
    suspend fun getImportedTimestamps(): Set<String> = dao.getImportedTimestamps().toSet()

    suspend fun replaceAll(entries: List<ExerciseEntry>) {
        dao.deleteAll()
        dao.insertAll(entries)
    }
}

