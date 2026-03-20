package com.projectember.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.projectember.mobile.data.local.entities.WeightEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {

    /**
     * Latest entry by date/time ordering — used for the dashboard weight card.
     *
     * This selects the newest row by `entryDate` (descending). When multiple
     * rows share the same date, `id DESC` is used as a deterministic
     * tiebreaker (most-recently-inserted wins). This avoids relying solely on
     * insertion order when newer dates exist in the table.
     */
    @Query("SELECT * FROM weight_entries ORDER BY entryDate DESC, id DESC LIMIT 1")
    fun getLatestEntry(): Flow<WeightEntry?>

    /** All entries with an entryDate on or after [fromDate], sorted oldest → newest. */
    @Query(
        "SELECT * FROM weight_entries WHERE entryDate >= :fromDate " +
            "ORDER BY entryDate ASC, id ASC"
    )
    fun getEntriesFromDate(fromDate: String): Flow<List<WeightEntry>>

    /** All entries within an inclusive date range, sorted oldest → newest. */
    @Query(
        "SELECT * FROM weight_entries " +
            "WHERE entryDate >= :fromDate AND entryDate <= :toDate " +
            "ORDER BY entryDate ASC, id ASC"
    )
    fun getEntriesInRange(fromDate: String, toDate: String): Flow<List<WeightEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: WeightEntry)

    /** Deletes all entries for [date] (enforces one-entry-per-day invariant). */
    @Query("DELETE FROM weight_entries WHERE entryDate = :date")
    suspend fun deleteByDate(date: String)

    @Query("SELECT COUNT(*) FROM weight_entries")
    suspend fun count(): Int

    @Query("SELECT * FROM weight_entries ORDER BY entryDate ASC, id ASC")
    suspend fun getAllOnce(): List<WeightEntry>

    /** All entries, newest first — used for the weight history screen. */
    @Query("SELECT * FROM weight_entries ORDER BY entryDate DESC, id DESC")
    fun getAllEntries(): Flow<List<WeightEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<WeightEntry>)

    @Delete
    suspend fun delete(entry: WeightEntry)

    @Query("DELETE FROM weight_entries")
    suspend fun deleteAll()
}
