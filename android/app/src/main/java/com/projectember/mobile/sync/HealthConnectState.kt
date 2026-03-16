package com.projectember.mobile.sync

/**
 * Represents the current availability and permission state of Health Connect for display in the
 * UI.  The ViewModel derives this from [HealthConnectManager] and exposes it as a StateFlow.
 */
sealed class HealthConnectUiState {

    /** SDK status has not been checked yet (initial/loading). */
    data object Checking : HealthConnectUiState()

    /** Health Connect is not installed on this device. */
    data object NotInstalled : HealthConnectUiState()

    /**
     * Health Connect is installed but the required permissions have not been granted.
     *
     * @param canRequest true if the runtime permission flow is still available (i.e. the user
     *   has not permanently denied it).
     */
    data class PermissionsRequired(val canRequest: Boolean = true) : HealthConnectUiState()

    /** Health Connect is available and all required permissions are granted. Ready to sync. */
    data object Ready : HealthConnectUiState()

    /** A sync is currently in progress. */
    data object Syncing : HealthConnectUiState()

    /**
     * The most recent sync completed successfully.
     *
     * @param lastSyncTime Human-readable timestamp of the last successful sync.
     * @param summary Short summary of what was imported.
     */
    data class LastSyncSuccess(
        val lastSyncTime: String,
        val summary: String
    ) : HealthConnectUiState()

    /**
     * The most recent sync (or setup step) encountered an error.
     *
     * @param message User-facing description of the error.
     */
    data class Error(val message: String) : HealthConnectUiState()
}
