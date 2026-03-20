package com.projectember.mobile.data.repository

import com.projectember.mobile.data.local.dao.ManualHealthEntryDao
import com.projectember.mobile.data.local.entities.ManualHealthEntry
import kotlinx.coroutines.flow.Flow

class ManualHealthEntryRepository(private val dao: ManualHealthEntryDao) {

    /** Save a new manual health entry. */
    suspend fun insert(entry: ManualHealthEntry) = dao.insert(entry)

    /** All entries for the given metric type, newest first. */
    fun getAllForMetric(metricType: String): Flow<List<ManualHealthEntry>> =
        dao.getAllForMetric(metricType)

    /** Entries for the given metric on or after [fromDate], oldest first. */
    fun getForMetricFromDate(metricType: String, fromDate: String): Flow<List<ManualHealthEntry>> =
        dao.getForMetricFromDate(metricType, fromDate)

    /** Latest single entry for the metric — drives card display. */
    fun getLatestForMetric(metricType: String): Flow<ManualHealthEntry?> =
        dao.getLatestForMetric(metricType)

    suspend fun delete(entry: ManualHealthEntry) = dao.delete(entry)

    suspend fun update(entry: ManualHealthEntry) = dao.update(entry)

    suspend fun update(entry: ManualHealthEntry) = dao.update(entry)

    suspend fun getAllOnce(): List<ManualHealthEntry> = dao.getAllOnce()

    suspend fun replaceAll(entries: List<ManualHealthEntry>) {
        dao.deleteAll()
        dao.insertAll(entries)
    }

    /** Latest entry per metric type — drives per-metric card priority display. */
    fun getLatestForAllMetrics(): Flow<List<ManualHealthEntry>> =
        dao.getLatestForAllMetrics()
}
