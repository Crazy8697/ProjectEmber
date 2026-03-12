package com.projectember.mobile.sync

import com.projectember.mobile.data.local.entities.SyncStatus
import com.projectember.mobile.data.repository.SyncRepository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * SyncManager is a stub for manual sync with the Project Ember NAS/server.
 * Full sync logic (fetching remote data, merging into Room) will be added in a future version.
 */
class SyncManager(private val syncRepository: SyncRepository) {

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    suspend fun triggerManualSync(): SyncResult {
        val now = LocalDateTime.now().format(dateTimeFormatter)

        syncRepository.updateSyncStatus(
            SyncStatus(
                id = 1,
                lastSyncTime = null,
                status = "syncing",
                message = "Sync in progress...",
                updatedAt = now
            )
        )

        return try {
            // TODO: Implement real sync with Ember server
            // val ketoData = apiService.getKetoEntries()
            // val recipeData = apiService.getRecipes()
            // ketoRepository.replaceAll(ketoData.map { it.toEntity() })
            // recipeRepository.replaceAll(recipeData.map { it.toEntity() })

            val syncedAt = LocalDateTime.now().format(dateTimeFormatter)
            syncRepository.updateSyncStatus(
                SyncStatus(
                    id = 1,
                    lastSyncTime = syncedAt,
                    status = "success",
                    message = "Sync complete (stub)",
                    updatedAt = syncedAt
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
                    updatedAt = failedAt
                )
            )
            SyncResult.Error(e.message ?: "Unknown error")
        }
    }
}

sealed class SyncResult {
    data class Success(val syncedAt: String) : SyncResult()
    data class Error(val message: String) : SyncResult()
}
