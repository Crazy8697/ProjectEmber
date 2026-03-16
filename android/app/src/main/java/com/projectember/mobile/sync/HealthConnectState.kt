package com.projectember.mobile.sync

/** Availability of the Health Connect SDK on this device. */
enum class HealthConnectAvailability {
    /** Still determining status (initial state). */
    CHECKING,

    /** Health Connect is installed and ready to use. */
    AVAILABLE,

    /** Health Connect is not installed; user should be directed to the Play Store. */
    NOT_INSTALLED,

    /** The device/API level does not support Health Connect. */
    NOT_SUPPORTED,
}

/**
 * Snapshot of the current Health Connect + sync state shown in the Sync UI section.
 *
 * @param availability  Whether Health Connect is available on this device.
 * @param permissionsGranted  Whether all required Health Connect permissions are granted.
 * @param requiredPermissions  The set of permission strings that must be requested.
 * @param syncing  True while a sync is in progress.
 * @param lastSyncTime  Human-readable timestamp of the last successful sync, or null.
 * @param lastSyncMessage  Summary of what was imported on the last sync, or null.
 * @param errorMessage  Most recent error description, or null when no error.
 */
data class HealthConnectUiState(
    val availability: HealthConnectAvailability = HealthConnectAvailability.CHECKING,
    val permissionsGranted: Boolean = false,
    val requiredPermissions: Set<String> = emptySet(),
    val syncing: Boolean = false,
    val lastSyncTime: String? = null,
    val lastSyncMessage: String? = null,
    val errorMessage: String? = null,
)

/** Result of a single Health Connect data import pass. */
data class HealthSyncImportResult(
    val stepsLast30Days: Long = 0,
    val weightEntriesImported: Int = 0,
    val exerciseSessionsImported: Int = 0,
    val errors: List<String> = emptyList(),
)
