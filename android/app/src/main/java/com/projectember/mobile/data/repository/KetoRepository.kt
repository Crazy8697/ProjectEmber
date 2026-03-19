package com.projectember.mobile.data.repository

import com.projectember.mobile.data.local.dao.KetoDao
import com.projectember.mobile.data.local.entities.KetoEntry
import kotlinx.coroutines.flow.Flow

class KetoRepository(private val ketoDao: KetoDao) {
    fun getRecentEntries(limit: Int): Flow<List<KetoEntry>> = ketoDao.getRecentEntries(limit)
    fun getEntriesForDate(date: String): Flow<List<KetoEntry>> = ketoDao.getEntriesForDate(date)
    fun getEntriesFromDate(startDate: String): Flow<List<KetoEntry>> = ketoDao.getEntriesFromDate(startDate)

    suspend fun getEntryById(id: Int): KetoEntry? = ketoDao.getEntryById(id)

    suspend fun insertEntry(entry: KetoEntry) {
        ketoDao.insert(entry)
    }

    suspend fun insertEntryAndReturnId(entry: KetoEntry): Long =
        ketoDao.insertAndReturnId(entry)

    suspend fun updateEntry(entry: KetoEntry) {
        ketoDao.update(entry)
    }

    suspend fun deleteEntry(entry: KetoEntry) {
        ketoDao.delete(entry)
    }

    suspend fun replaceAll(entries: List<KetoEntry>) {
        ketoDao.deleteAll()
        ketoDao.insertAll(entries)
    }

    suspend fun getAllOnce(): List<KetoEntry> = ketoDao.getAllOnce()

    /**
     * Clears the recipeId from all keto entries that reference the given recipe,
     * preserving the historical nutrition snapshot. Call this before deleting the
     * recipe so that no dangling recipeId references are left in the database.
     */
    suspend fun clearRecipeReference(recipeId: Int) = ketoDao.clearRecipeReference(recipeId)

    /**
     * One-time startup repair: nulls out recipeId on any keto entry that references
     * a recipe which no longer exists, repairing pre-existing dangling references
     * created before the recipe-delete detach fix was in place.
     * Idempotent — safe to call on every app launch.
     */
    suspend fun clearDanglingRecipeReferences() = ketoDao.clearDanglingRecipeReferences()

    /** Clears recipeId from all keto entries (detaches all recipe references). */
    suspend fun clearAllRecipeReferences() = ketoDao.clearAllRecipeReferences()
}
