package com.projectember.mobile.sync

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
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
import java.time.LocalDate
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

    /**
     * Full set of Health Connect read permissions used across all features.
     * Presented to the user via the Manage Permissions flow in Settings.
     */
    val requiredPermissions: Set<String> = setOf(
        // Sync / import permissions (existing)
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        // Activity metrics (Exercise screen)
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        // Vitals (Health screen)
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getReadPermission(BloodGlucoseRecord::class),
        HealthPermission.getReadPermission(BodyTemperatureRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(RespiratoryRateRecord::class),
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

    // ── Live health-metrics reads (Health screen) ─────────────────────────────

    /**
     * Returns the set of Health Connect permissions currently granted on this device.
     * Must only be called when [isAvailable] is true.
     */
    suspend fun getGrantedPermissions(): Set<String> {
        val client = HealthConnectClient.getOrCreate(context)
        return client.permissionController.getGrantedPermissions()
    }

    /**
     * Reads the latest vitals/health metrics from Health Connect for the past [lookbackDays]
     * days and returns a [HealthMetricsSnapshot].  Metrics whose permission has not been
     * granted will have null values in the snapshot.
     *
     * This method performs live reads and does NOT persist data to Room.
     */
    suspend fun readLatestHealthMetrics(lookbackDays: Long = 30L): HealthMetricsSnapshot {
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()

        val end = Instant.now()
        val start = end.minus(lookbackDays, ChronoUnit.DAYS)
        val timeRange = TimeRangeFilter.between(start, end)

        // ── Heart rate ────────────────────────────────────────────────────────
        var heartRateBpm: Long? = null
        var heartRateTs: String? = null
        if (HealthPermission.getReadPermission(HeartRateRecord::class) in granted) {
            val records = client.readRecords(
                ReadRecordsRequest(HeartRateRecord::class, timeRange)
            ).records
            records.maxByOrNull { it.endTime }?.let { r ->
                heartRateBpm = r.samples.lastOrNull()?.beatsPerMinute
                heartRateTs = r.endTime.atZone(ZoneId.systemDefault())
                    .toLocalDateTime().format(displayFormatter)
            }
        }

        // ── Resting heart rate ────────────────────────────────────────────────
        var restingHeartRateBpm: Long? = null
        var restingHeartRateTs: String? = null
        if (HealthPermission.getReadPermission(RestingHeartRateRecord::class) in granted) {
            val records = client.readRecords(
                ReadRecordsRequest(RestingHeartRateRecord::class, timeRange)
            ).records
            records.maxByOrNull { it.time }?.let { r ->
                restingHeartRateBpm = r.beatsPerMinute
                restingHeartRateTs = r.time.atZone(ZoneId.systemDefault())
                    .toLocalDateTime().format(displayFormatter)
            }
        }

        // ── Blood pressure ────────────────────────────────────────────────────
        var bpSystolic: Double? = null
        var bpDiastolic: Double? = null
        var bpTs: String? = null
        if (HealthPermission.getReadPermission(BloodPressureRecord::class) in granted) {
            val records = client.readRecords(
                ReadRecordsRequest(BloodPressureRecord::class, timeRange)
            ).records
            records.maxByOrNull { it.time }?.let { r ->
                bpSystolic = r.systolic.inMillimetersOfMercury
                bpDiastolic = r.diastolic.inMillimetersOfMercury
                bpTs = r.time.atZone(ZoneId.systemDefault())
                    .toLocalDateTime().format(displayFormatter)
            }
        }

        // ── Blood glucose ─────────────────────────────────────────────────────
        var bgMmol: Double? = null
        var bgTs: String? = null
        if (HealthPermission.getReadPermission(BloodGlucoseRecord::class) in granted) {
            val records = client.readRecords(
                ReadRecordsRequest(BloodGlucoseRecord::class, timeRange)
            ).records
            records.maxByOrNull { it.time }?.let { r ->
                bgMmol = r.level.inMillimolesPerLiter
                bgTs = r.time.atZone(ZoneId.systemDefault())
                    .toLocalDateTime().format(displayFormatter)
            }
        }

        // ── Body temperature ──────────────────────────────────────────────────
        var tempC: Double? = null
        var tempTs: String? = null
        if (HealthPermission.getReadPermission(BodyTemperatureRecord::class) in granted) {
            val records = client.readRecords(
                ReadRecordsRequest(BodyTemperatureRecord::class, timeRange)
            ).records
            records.maxByOrNull { it.time }?.let { r ->
                tempC = r.temperature.inCelsius
                tempTs = r.time.atZone(ZoneId.systemDefault())
                    .toLocalDateTime().format(displayFormatter)
            }
        }

        // ── Oxygen saturation ─────────────────────────────────────────────────
        var spo2Pct: Double? = null
        var spo2Ts: String? = null
        if (HealthPermission.getReadPermission(OxygenSaturationRecord::class) in granted) {
            val records = client.readRecords(
                ReadRecordsRequest(OxygenSaturationRecord::class, timeRange)
            ).records
            records.maxByOrNull { it.time }?.let { r ->
                spo2Pct = r.percentage.value
                spo2Ts = r.time.atZone(ZoneId.systemDefault())
                    .toLocalDateTime().format(displayFormatter)
            }
        }

        // ── Respiratory rate ──────────────────────────────────────────────────
        var rrBreath: Double? = null
        var rrTs: String? = null
        if (HealthPermission.getReadPermission(RespiratoryRateRecord::class) in granted) {
            val records = client.readRecords(
                ReadRecordsRequest(RespiratoryRateRecord::class, timeRange)
            ).records
            records.maxByOrNull { it.time }?.let { r ->
                rrBreath = r.rate
                rrTs = r.time.atZone(ZoneId.systemDefault())
                    .toLocalDateTime().format(displayFormatter)
            }
        }

        // ── Sleep ─────────────────────────────────────────────────────────────
        var sleepSummary: SleepSessionSummary? = null
        if (HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) {
            val records = client.readRecords(
                ReadRecordsRequest(SleepSessionRecord::class, timeRange)
            ).records
            records.maxByOrNull { it.endTime }?.let { r ->
                val durationMs = ChronoUnit.MILLIS.between(r.startTime, r.endTime)
                val totalMinutes = (durationMs / 60_000L).toInt()
                val hours = totalMinutes / 60
                val mins = totalMinutes % 60
                sleepSummary = SleepSessionSummary(
                    date = r.startTime.atZone(ZoneId.systemDefault())
                        .toLocalDate().format(dateFormatter),
                    durationDisplay = if (hours > 0) "${hours}h ${mins}m" else "${mins}m",
                    durationHours = totalMinutes / 60.0
                )
            }
        }

        return HealthMetricsSnapshot(
            grantedPermissions = granted,
            heartRateBpm = heartRateBpm,
            heartRateTimestamp = heartRateTs,
            restingHeartRateBpm = restingHeartRateBpm,
            restingHeartRateTimestamp = restingHeartRateTs,
            bloodPressureSystolicMmHg = bpSystolic,
            bloodPressureDiastolicMmHg = bpDiastolic,
            bloodPressureTimestamp = bpTs,
            bloodGlucoseMmol = bgMmol,
            bloodGlucoseTimestamp = bgTs,
            bodyTemperatureCelsius = tempC,
            bodyTemperatureTimestamp = tempTs,
            oxygenSaturationPct = spo2Pct,
            oxygenSaturationTimestamp = spo2Ts,
            respiratoryRateBreath = rrBreath,
            respiratoryRateTimestamp = rrTs,
            latestSleepSession = sleepSummary,
        )
    }

    /**
     * Reads today's activity summary (steps, distance, active/total calories) from Health
     * Connect.  Metrics whose permission is not granted will be null in the result.
     *
     * This method performs live reads and does NOT persist data to Room.
     */
    suspend fun readTodayActivitySummary(): ActivitySummary {
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()

        val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val now = Instant.now()
        val timeRange = TimeRangeFilter.between(todayStart, now)

        var stepsToday: Long? = null
        if (HealthPermission.getReadPermission(StepsRecord::class) in granted) {
            val records = client.readRecords(
                ReadRecordsRequest(StepsRecord::class, timeRange)
            ).records
            if (records.isNotEmpty()) {
                stepsToday = records.sumOf { it.count }
            }
        }

        var distanceMeters: Double? = null
        if (HealthPermission.getReadPermission(DistanceRecord::class) in granted) {
            val records = client.readRecords(
                ReadRecordsRequest(DistanceRecord::class, timeRange)
            ).records
            if (records.isNotEmpty()) {
                distanceMeters = records.sumOf { it.distance.inMeters }
            }
        }

        var activeCalKcal: Double? = null
        if (HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in granted) {
            val records = client.readRecords(
                ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, timeRange)
            ).records
            if (records.isNotEmpty()) {
                activeCalKcal = records.sumOf { it.energy.inKilocalories }
            }
        }

        var totalCalKcal: Double? = null
        if (HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class) in granted) {
            val records = client.readRecords(
                ReadRecordsRequest(TotalCaloriesBurnedRecord::class, timeRange)
            ).records
            if (records.isNotEmpty()) {
                totalCalKcal = records.sumOf { it.energy.inKilocalories }
            }
        }

        return ActivitySummary(
            stepsToday = stepsToday,
            distanceMeters = distanceMeters,
            activeCaloriesKcal = activeCalKcal,
            totalCaloriesKcal = totalCalKcal,
        )
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

        // ── Per-metric permission strings ─────────────────────────────────────
        // These are stable String constants so callers don't need to import HC record types.

        val PERM_STEPS = HealthPermission.getReadPermission(StepsRecord::class)
        val PERM_DISTANCE = HealthPermission.getReadPermission(DistanceRecord::class)
        val PERM_ACTIVE_CALORIES = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
        val PERM_TOTAL_CALORIES = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
        val PERM_HEART_RATE = HealthPermission.getReadPermission(HeartRateRecord::class)
        val PERM_RESTING_HEART_RATE = HealthPermission.getReadPermission(RestingHeartRateRecord::class)
        val PERM_SLEEP = HealthPermission.getReadPermission(SleepSessionRecord::class)
        val PERM_BLOOD_PRESSURE = HealthPermission.getReadPermission(BloodPressureRecord::class)
        val PERM_BLOOD_GLUCOSE = HealthPermission.getReadPermission(BloodGlucoseRecord::class)
        val PERM_BODY_TEMPERATURE = HealthPermission.getReadPermission(BodyTemperatureRecord::class)
        val PERM_OXYGEN_SATURATION = HealthPermission.getReadPermission(OxygenSaturationRecord::class)
        val PERM_RESPIRATORY_RATE = HealthPermission.getReadPermission(RespiratoryRateRecord::class)
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
