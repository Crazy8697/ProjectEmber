package com.projectember.mobile.sync

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.projectember.mobile.data.local.entities.ExerciseEntry
import com.projectember.mobile.data.local.entities.WeightEntry
import com.projectember.mobile.data.repository.ExerciseCategoryRepository
import com.projectember.mobile.data.repository.ExerciseRepository
import com.projectember.mobile.data.repository.WeightRepository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Centralised Health Connect integration layer.
 *
 * Responsibilities:
 * - Availability detection (`getSdkStatus`, `isAvailable`)
 * - Permission state (`hasAllPermissions`, [REQUIRED_PERMISSIONS])
 * - Data import: weight → Room `weight_entries`, exercise sessions → Room `exercise_entries`,
 *   steps (read-only, reported in summary but not persisted).
 *
 * This class must NOT be used directly from Composables; route all calls through
 * `SettingsViewModel` or `SyncManager`.
 */
class HealthConnectManager(
    private val context: Context,
    private val weightRepository: WeightRepository,
    private val exerciseRepository: ExerciseRepository,
    private val exerciseCategoryRepository: ExerciseCategoryRepository,
) {

    companion object {
        /** Permissions required for the Health Connect sync feature. */
        val REQUIRED_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        )

        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
        private val DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        private val SYNC_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        /** How far back to look on the first-ever sync (no prior `lastSyncTime`). */
        private const val INITIAL_WINDOW_DAYS = 30L
    }

    // ── Availability ──────────────────────────────────────────────────────────

    /** Returns one of [HealthConnectClient.SDK_AVAILABLE], [HealthConnectClient.SDK_UNAVAILABLE],
     *  or [HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED]. */
    fun getSdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    /** `true` if Health Connect is installed and ready on this device. */
    fun isAvailable(): Boolean = getSdkStatus() == HealthConnectClient.SDK_AVAILABLE

    fun getClient(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    // ── Permissions ───────────────────────────────────────────────────────────

    /** Returns `true` if every permission in [REQUIRED_PERMISSIONS] has been granted. */
    suspend fun hasAllPermissions(): Boolean {
        if (!isAvailable()) return false
        val granted = getClient().permissionController.getGrantedPermissions()
        return granted.containsAll(REQUIRED_PERMISSIONS)
    }

    // ── Data import ───────────────────────────────────────────────────────────

    /**
     * Reads health data from Health Connect and imports it into the local Room database.
     *
     * @param sinceTime  ISO-like timestamp ("yyyy-MM-dd HH:mm:ss") used as the start of the
     *   read window; if `null` the last [INITIAL_WINDOW_DAYS] days are used instead.
     * @return  A [HealthSyncImportResult] summarising what was imported.
     */
    suspend fun performSync(sinceTime: String? = null): HealthSyncImportResult {
        val client = getClient()
        val endTime = Instant.now()
        val startTime = resolveStartTime(sinceTime, endTime)

        var stepsTotal = 0L
        var weightImported = 0
        var exerciseImported = 0
        val errors = mutableListOf<String>()

        // ── Steps (aggregated count — not persisted) ──────────────────────────
        try {
            val stepsResp = client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                )
            )
            stepsTotal = stepsResp.records.sumOf { it.count }
        } catch (e: Exception) {
            errors.add("Steps: ${e.message ?: "read failed"}")
        }

        // ── Weight → Room weight_entries (upsert per day) ─────────────────────
        try {
            val weightResp = client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                )
            )
            val zone = ZoneId.systemDefault()
            for (record in weightResp.records) {
                val date = record.time.atZone(zone).toLocalDate().format(DATE_FMT)
                weightRepository.upsertForDate(
                    WeightEntry(entryDate = date, weightKg = record.weight.inKilograms)
                )
                weightImported++
            }
        } catch (e: Exception) {
            errors.add("Weight: ${e.message ?: "read failed"}")
        }

        // ── Exercise sessions → Room exercise_entries ─────────────────────────
        try {
            val categories = exerciseCategoryRepository.getAllCategoriesOnce()
            val exerciseResp = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                )
            )
            val zone = ZoneId.systemDefault()
            for (record in exerciseResp.records) {
                val localStart = record.startTime.atZone(zone).toLocalDateTime()
                val durationMin = ((record.endTime.epochSecond - record.startTime.epochSecond) / 60).toInt()
                val typeName = exerciseTypeName(record.exerciseType)
                val categoryName = exerciseTypeCategory(record.exerciseType)
                val categoryId = categories.firstOrNull {
                    it.name.equals(categoryName, ignoreCase = true)
                }?.id ?: categories.firstOrNull {
                    it.name.equals("Other", ignoreCase = true)
                }?.id ?: categories.firstOrNull()?.id ?: 0

                exerciseRepository.insertEntry(
                    ExerciseEntry(
                        entryDate = localStart.toLocalDate().format(DATE_FMT),
                        entryTime = localStart.format(TIME_FMT),
                        timestamp = localStart.format(DATETIME_FMT),
                        type = typeName,
                        subtype = null,
                        categoryId = categoryId,
                        durationMinutes = durationMin.takeIf { it > 0 },
                        caloriesBurned = null,
                        notes = "Imported from Health Connect",
                    )
                )
                exerciseImported++
            }
        } catch (e: Exception) {
            errors.add("Exercise: ${e.message ?: "read failed"}")
        }

        return HealthSyncImportResult(
            stepsLast30Days = stepsTotal,
            weightEntriesImported = weightImported,
            exerciseSessionsImported = exerciseImported,
            errors = errors,
        )
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun resolveStartTime(sinceTime: String?, endTime: Instant): Instant {
        if (sinceTime == null) return endTime.minusSeconds(INITIAL_WINDOW_DAYS * 24 * 60 * 60)
        return runCatching {
            LocalDateTime.parse(sinceTime, SYNC_TIME_FMT)
                .atZone(ZoneId.systemDefault())
                .toInstant()
        }.getOrElse { endTime.minusSeconds(INITIAL_WINDOW_DAYS * 24 * 60 * 60) }
    }

    private fun exerciseTypeName(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Running"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walking"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Biking"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Swimming"
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Weightlifting"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hiking"
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
        ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> "Stretching"
        ExerciseSessionRecord.EXERCISE_TYPE_CYCLING -> "Cycling"
        else -> "Exercise"
    }

    private fun exerciseTypeCategory(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
        ExerciseSessionRecord.EXERCISE_TYPE_CYCLING -> "Cardio"
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Strength"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA,
        ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> "Recovery"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Outdoor"
        else -> "Other"
    }
}
