package com.projectember.mobile.data.repository

import com.projectember.mobile.data.local.dao.WeightDao
import com.projectember.mobile.data.local.entities.WeightEntry
import kotlinx.coroutines.flow.Flow

class WeightRepository(private val dao: WeightDao) {

    /** Observable latest weight entry — drives the dashboard card. */
    fun getLatestEntry(): Flow<WeightEntry?> = dao.getLatestEntry()

    /** Weight entries from [fromDate] onward (inclusive), oldest first. */
    fun getEntriesFromDate(fromDate: String): Flow<List<WeightEntry>> =
        dao.getEntriesFromDate(fromDate)

    /** Weight entries within an inclusive date range, oldest first. */
    fun getEntriesInRange(fromDate: String, toDate: String): Flow<List<WeightEntry>> =
        dao.getEntriesInRange(fromDate, toDate)

    /** Insert (or replace-by-id) a weight entry. */
    suspend fun insert(entry: WeightEntry) = dao.insert(entry)

    /**
     * Upsert a weight entry for a specific date — enforces the one-entry-per-day rule.
     * Any existing entries for [entry.entryDate] are deleted before the new entry is inserted,
     * so duplicate same-day entries can never accumulate.
     */
    suspend fun upsertForDate(entry: WeightEntry) {
        dao.deleteByDate(entry.entryDate)
        dao.insert(entry)
    }

    /** All weight entries, newest first — drives the history screen. */
    fun getAllEntries(): Flow<List<WeightEntry>> = dao.getAllEntries()

    suspend fun delete(entry: WeightEntry) = dao.delete(entry)

    suspend fun count(): Int = dao.count()

    suspend fun getAllOnce(): List<WeightEntry> = dao.getAllOnce()

    suspend fun replaceAll(entries: List<WeightEntry>) {
        dao.deleteAll()
        dao.insertAll(entries)
    }
}
