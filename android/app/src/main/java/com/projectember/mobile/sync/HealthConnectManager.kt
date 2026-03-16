package com.projectember.mobile.sync

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.projectember.mobile.data.local.entities.ExerciseCategory
import com.projectember.mobile.data.local.entities.ExerciseEntry
import com.projectember.mobile.data.local.entities.SyncStatus
import com.projectember.mobile.data.local.entities.WeightEntry
import com.projectember.mobile.data.repository.ExerciseCategoryRepository
import com.projectember.mobile.data.repository.ExerciseRepository
import com.projectember.mobile.data.repository.SyncRepository
import com.projectember.mobile.data.repository.WeightRepository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Centralized Health Connect layer.
 *
 * Responsibilities:
 * - Detect SDK availability and installation status.
 * - Declare the required permission set.
 * - Check whether permissions have been granted.
 * - Read and import Steps, Weight, and Exercise sessions.
 * - Deduplicate imported exercise entries by timestamp.
 */
class HealthConnectManager(
    private val context: Context,
    private val syncRepository: SyncRepository,
    private val weightRepository: WeightRepository,
    private val exerciseRepository: ExerciseRepository,
    private val exerciseCategoryRepository: ExerciseCategoryRepository
) {

    // ── Permission set ────────────────────────────────────────────────────────

    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    )

    // ── Formatters ────────────────────────────────────────────────────────────

    /** Date-only formatter for Room entryDate fields ("yyyy-MM-dd"). */
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** Short timestamp used for exercise entry deduplication ("yyyy-MM-dd HH:mm"). */
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /** Human-readable timestamp for sync status display ("yyyy-MM-dd HH:mm:ss"). */
    private val displayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    // ── Availability ──────────────────────────────────────────────────────────

    /**
     * Returns the SDK status for Health Connect on this device.
     * Possible values: [HealthConnectClient.SDK_UNAVAILABLE],
     * [HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED],
     * [HealthConnectClient.SDK_AVAILABLE].
     */
    fun getSdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    fun isAvailable(): Boolean =
        getSdkStatus() == HealthConnectClient.SDK_AVAILABLE

    // ── Permissions ───────────────────────────────────────────────────────────

    /**
     * Returns true if all [requiredPermissions] have been granted.
     * Must only be called when [isAvailable] is true.
     */
    suspend fun hasPermissions(): Boolean {
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(requiredPermissions)
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

    /**
     * Reads Health Connect data for the last [lookbackDays] days and imports it into Room.
     *
     * Returns a [SyncImportResult] describing what was imported or the error encountered.
     */
    suspend fun syncFromHealthConnect(lookbackDays: Long = 30L): SyncImportResult {
        val now = LocalDateTime.now()
        val nowDisplay = now.format(displayFormatter)

        return try {
            val client = HealthConnectClient.getOrCreate(context)

            val end = Instant.now()
            val start = end.minus(lookbackDays, ChronoUnit.DAYS)
            val timeRange = TimeRangeFilter.between(start, end)

            var stepsRead = 0          // read but not persisted (informational only)
            var weightsImported = 0
            var exercisesImported = 0

            // ── Steps ─────────────────────────────────────────────────────────
            // Steps are read for completeness but not yet stored in Ember's DB.
            // They are NOT counted as "imported" since no visible record is created.
            val stepsResponse = client.readRecords(
                ReadRecordsRequest(StepsRecord::class, timeRange)
            )
            stepsRead = stepsResponse.records.size

            // ── Weight ────────────────────────────────────────────────────────
            val weightResponse = client.readRecords(
                ReadRecordsRequest(WeightRecord::class, timeRange)
            )
            for (record in weightResponse.records) {
                val date = record.time
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .format(dateFormatter)
                val kg = record.weight.inKilograms
                weightRepository.upsertForDate(
                    WeightEntry(
                        entryDate = date,
                        weightKg = kg,
                        source = WeightEntry.SOURCE_HEALTH_CONNECT
                    )
                )
                weightsImported++
            }

            // ── Exercise sessions ─────────────────────────────────────────────
            val exerciseResponse = client.readRecords(
                ReadRecordsRequest(ExerciseSessionRecord::class, timeRange)
            )
            val importedTimestamps = exerciseRepository.getImportedTimestamps()
            val cardioId = findOrCreateCategory("Cardio")

            for (session in exerciseResponse.records) {
                val startLocal = session.startTime
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
                val entryDate = startLocal.toLocalDate().format(dateFormatter)
                val entryTime = startLocal.format(DateTimeFormatter.ofPattern("HH:mm"))
                val timestamp = startLocal.format(dateTimeFormatter)

                if (timestamp in importedTimestamps) continue  // already imported

                val durationMs = ChronoUnit.MILLIS.between(session.startTime, session.endTime)
                val durationMinutes = (durationMs / MILLIS_PER_MINUTE).toInt().coerceAtLeast(1)

                exerciseRepository.insertEntry(
                    ExerciseEntry(
                        entryDate = entryDate,
                        entryTime = entryTime,
                        timestamp = timestamp,
                        type = exerciseTypeLabel(session.exerciseType),
                        subtype = null,
                        categoryId = cardioId,
                        notes = HC_IMPORT_NOTE,
                        durationMinutes = durationMinutes,
                        caloriesBurned = null
                    )
                )
                exercisesImported++
            }

            // ── Update sync status ────────────────────────────────────────────
            val summary = buildSummary(weightsImported, exercisesImported)
            syncRepository.updateSyncStatus(
                SyncStatus(
                    id = 1,
                    lastSyncTime = nowDisplay,
                    status = "success",
                    message = summary,
                    updatedAt = nowDisplay
                )
            )

            SyncImportResult.Success(
                syncedAt = nowDisplay,
                stepsRead = stepsRead,
                weightsImported = weightsImported,
                exercisesImported = exercisesImported,
                summary = summary
            )
        } catch (e: Exception) {
            val message = e.message ?: "Unknown Health Connect error"
            syncRepository.updateSyncStatus(
                SyncStatus(
                    id = 1,
                    lastSyncTime = null,
                    status = "error",
                    message = "HC sync failed: $message",
                    updatedAt = nowDisplay
                )
            )
            SyncImportResult.Error(message)
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /** Returns the id of the named exercise category, creating it if it does not exist. */
    private suspend fun findOrCreateCategory(name: String): Int {
        val existing = exerciseCategoryRepository.getAllCategoriesOnce()
            .firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (existing != null) return existing.id
        return exerciseCategoryRepository.insert(
            ExerciseCategory(name = name, isBuiltIn = false)
        ).toInt()
    }

    private fun buildSummary(weights: Int, exercises: Int): String {
        val parts = mutableListOf<String>()
        if (weights > 0) parts.add("$weights weight entr${if (weights != 1) "ies" else "y"}")
        if (exercises > 0) parts.add("$exercises exercise session${if (exercises != 1) "s" else ""}")
        return if (parts.isEmpty()) "Nothing new to import" else "Imported: ${parts.joinToString(", ")}"
    }

    private fun exerciseTypeLabel(exerciseType: Int): String = when (exerciseType) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Running"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walking"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Cycling"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Swimming"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Strength Training"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hiking"
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "Rowing"
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Elliptical"
        else -> "Workout"
    }

    companion object {
        /** Note tag stored on exercise entries imported from Health Connect. */
        const val HC_IMPORT_NOTE = "Imported from Health Connect"

        private const val MILLIS_PER_MINUTE = 60_000L
    }
}

// ── Result types ──────────────────────────────────────────────────────────────

sealed class SyncImportResult {
    data class Success(
        val syncedAt: String,
        /** Number of step records read from HC (not persisted). */
        val stepsRead: Int,
        val weightsImported: Int,
        val exercisesImported: Int,
        val summary: String
    ) : SyncImportResult()

    data class Error(val message: String) : SyncImportResult()
}
