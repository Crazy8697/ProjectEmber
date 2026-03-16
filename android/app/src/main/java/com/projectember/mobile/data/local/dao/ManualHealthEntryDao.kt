package com.projectember.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.projectember.mobile.data.local.entities.ManualHealthEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface ManualHealthEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ManualHealthEntry)

    /** All entries for a given metric type, newest first. */
    @Query(
        "SELECT * FROM manual_health_entries WHERE metricType = :metricType " +
            "ORDER BY entryDate DESC, entryTime DESC, id DESC"
    )
    fun getAllForMetric(metricType: String): Flow<List<ManualHealthEntry>>

    /** All entries for a given metric on or after [fromDate], oldest first. */
    @Query(
        "SELECT * FROM manual_health_entries " +
            "WHERE metricType = :metricType AND entryDate >= :fromDate " +
            "ORDER BY entryDate ASC, entryTime ASC, id ASC"
    )
    fun getForMetricFromDate(metricType: String, fromDate: String): Flow<List<ManualHealthEntry>>

    /** Most recent single entry for a metric — for card display. */
    @Query(
        "SELECT * FROM manual_health_entries WHERE metricType = :metricType " +
            "ORDER BY entryDate DESC, entryTime DESC, id DESC LIMIT 1"
    )
    fun getLatestForMetric(metricType: String): Flow<ManualHealthEntry?>

    @Delete
    suspend fun delete(entry: ManualHealthEntry)

    @Query("DELETE FROM manual_health_entries")
    suspend fun deleteAll()

    @Query("SELECT * FROM manual_health_entries ORDER BY entryDate ASC, entryTime ASC, id ASC")
    suspend fun getAllOnce(): List<ManualHealthEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<ManualHealthEntry>)
}
