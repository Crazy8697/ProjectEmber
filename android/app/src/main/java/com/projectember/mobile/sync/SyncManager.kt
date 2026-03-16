package com.projectember.mobile.sync

import com.projectember.mobile.data.local.entities.SyncStatus
import com.projectember.mobile.data.repository.SyncRepository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Orchestrates manual health data synchronisation via Health Connect.
 *
 * All health-data access is delegated to [HealthConnectManager]; this class
 * is responsible for availability/permission gating and persisting the resulting
 * [SyncStatus] in the local Room database.
 */
class SyncManager(
    private val syncRepository: SyncRepository,
    private val healthConnectManager: HealthConnectManager,
) {

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    suspend fun triggerManualSync(): SyncResult {
        val now = LocalDateTime.now().format(dateTimeFormatter)

        syncRepository.updateSyncStatus(
            SyncStatus(
                id = 1,
                lastSyncTime = null,
                status = "syncing",
                message = "Syncing with Health Connect…",
                updatedAt = now,
            )
        )

        // ── Availability guard ────────────────────────────────────────────────
        if (!healthConnectManager.isAvailable()) {
            val failedAt = LocalDateTime.now().format(dateTimeFormatter)
            syncRepository.updateSyncStatus(
                SyncStatus(
                    id = 1,
                    lastSyncTime = null,
                    status = "error",
                    message = "Health Connect is not available on this device.",
                    updatedAt = failedAt,
                )
            )
            return SyncResult.Error("Health Connect not available")
        }

        // ── Permissions guard ─────────────────────────────────────────────────
        if (!healthConnectManager.hasAllPermissions()) {
            val failedAt = LocalDateTime.now().format(dateTimeFormatter)
            syncRepository.updateSyncStatus(
                SyncStatus(
                    id = 1,
                    lastSyncTime = null,
                    status = "error",
                    message = "Health Connect permissions not granted. Please grant them and retry.",
                    updatedAt = failedAt,
                )
            )
            return SyncResult.Error("Health Connect permissions not granted")
        }

        // ── Incremental sync ──────────────────────────────────────────────────
        return try {
            val lastSyncTime = syncRepository.getSyncStatusOnce()?.lastSyncTime
            val importResult = healthConnectManager.performSync(sinceTime = lastSyncTime)
            val syncedAt = LocalDateTime.now().format(dateTimeFormatter)

            syncRepository.updateSyncStatus(
                SyncStatus(
                    id = 1,
                    lastSyncTime = syncedAt,
                    status = "success",
                    message = buildSyncMessage(importResult),
                    updatedAt = syncedAt,
                )
            )
            SyncResult.Success(syncedAt)
        } catch (e: Exception) {
            val failedAt = LocalDateTime.now().format(dateTimeFormatter)
            syncRepository.updateSyncStatus(
                SyncStatus(
                    id = 1,
                    lastSyncTime = null,
                    status = "error",
                    message = "Sync failed: ${e.message}",
                    updatedAt = failedAt,
                )
            )
            SyncResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun buildSyncMessage(result: HealthSyncImportResult): String {
        val parts = mutableListOf<String>()
        if (result.weightEntriesImported > 0)
            parts.add("${result.weightEntriesImported} weight entry(s)")
        if (result.exerciseSessionsImported > 0)
            parts.add("${result.exerciseSessionsImported} exercise session(s)")
        if (result.stepsLast30Days > 0)
            parts.add("${result.stepsLast30Days} steps counted")
        val summary = if (parts.isEmpty()) "No new data found" else "Imported: ${parts.joinToString(", ")}"
        val errorPart = if (result.errors.isNotEmpty()) " (${result.errors.size} partial error(s))" else ""
        return summary + errorPart
    }
}

sealed class SyncResult {
    data class Success(val syncedAt: String) : SyncResult()
    data class Error(val message: String) : SyncResult()
}
