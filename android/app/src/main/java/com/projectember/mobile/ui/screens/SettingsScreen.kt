package com.projectember.mobile.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.health.connect.client.PermissionController
import com.projectember.mobile.BuildConfig
import com.projectember.mobile.data.local.FoodWeightUnit
import com.projectember.mobile.data.local.VolumeUnit
import com.projectember.mobile.data.local.WeightUnit
import com.projectember.mobile.sync.HealthConnectAvailability
import com.projectember.mobile.sync.HealthConnectManager
import com.projectember.mobile.ui.theme.ThemeOption
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val healthConnectState by viewModel.healthConnectState.collectAsState()
    val exportState by viewModel.exportState.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val pendingImport by viewModel.pendingImport.collectAsState()
    val pendingEmailExport by viewModel.pendingEmailExport.collectAsState()
    val resetState by viewModel.resetState.collectAsState()
    val selectedTheme by viewModel.selectedTheme.collectAsState()
    val unitPrefs by viewModel.unitPreferences.collectAsState()

    val context = LocalContext.current

    // Danger Zone confirmation state
    var showResetConfirm1 by remember { mutableStateOf(false) }
    var showResetConfirm2 by remember { mutableStateOf(false) }

    // Export method chooser state
    var showExportChooser by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // ── Health Connect permission launcher ────────────────────────────────────
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        viewModel.onPermissionsResult(grantedPermissions)
    }

    // ── Check HC status on screen entry ──────────────────────────────────────
    LaunchedEffect(Unit) {
        viewModel.checkHealthConnectStatus()
    }

    // ── Snackbar feedback for export ─────────────────────────────────────────
    LaunchedEffect(exportState) {
        when (val s = exportState) {
            is BackupOpState.Success -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.clearExportState()
            }
            is BackupOpState.Error -> {
                snackbarHostState.showSnackbar("Export error: ${s.message}")
                viewModel.clearExportState()
            }
            else -> Unit
        }
    }

    // ── Snackbar feedback for import ─────────────────────────────────────────
    LaunchedEffect(importState) {
        when (val s = importState) {
            is BackupOpState.Success -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.clearImportState()
            }
            is BackupOpState.Error -> {
                snackbarHostState.showSnackbar("Import error: ${s.message}")
                viewModel.clearImportState()
            }
            else -> Unit
        }
    }

    // ── Snackbar feedback for reset ──────────────────────────────────────────
    LaunchedEffect(resetState) {
        when (val s = resetState) {
            is BackupOpState.Success -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.clearResetState()
            }
            is BackupOpState.Error -> {
                snackbarHostState.showSnackbar("Reset error: ${s.message}")
                viewModel.clearResetState()
            }
            else -> Unit
        }
    }

    // ── Email/share intent launcher for email export ──────────────────────────
    LaunchedEffect(pendingEmailExport) {
        val bytes = pendingEmailExport ?: return@LaunchedEffect
        try {
            val cacheFile = File(context.cacheDir, "ember_backup_${System.currentTimeMillis()}.json")
            cacheFile.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_SUBJECT, "Project Ember Backup")
                putExtra(Intent.EXTRA_TEXT, "Backup export attached/generated from the mobile app.")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Email Backup"))
        } catch (e: Exception) {
            snackbarHostState.showSnackbar(
                "Email export failed: ${e.message ?: "Could not prepare email export."}"
            )
        } finally {
            viewModel.clearPendingEmailExport()
        }
    }

    // ── SAF file-save launcher (Export) ──────────────────────────────────────
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportToUri(uri)
    }

    // ── SAF file-open launcher (Import) ──────────────────────────────────────
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.loadImport(uri)
    }

    val isBusy = exportState is BackupOpState.InProgress ||
        importState is BackupOpState.InProgress ||
        resetState is BackupOpState.InProgress

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ── Appearance ──────────────────────────────────────────────────
            SettingsSection(title = "Appearance") {
                SettingsSubLabel(text = "Theme")
                var themeDropdownExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = themeDropdownExpanded,
                    onExpandedChange = { themeDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedTheme.displayName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeDropdownExpanded)
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = themeDropdownExpanded,
                        onDismissRequest = { themeDropdownExpanded = false }
                    ) {
                        ThemeOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.displayName) },
                                onClick = {
                                    viewModel.setTheme(option)
                                    themeDropdownExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }
            }

            // ── Units & Measurements ────────────────────────────────────────
            SettingsSection(title = "Units & Measurements") {
                SettingsSubLabel(text = "Body Weight")
                WeightUnit.entries.forEachIndexed { index, unit ->
                    SettingsRadioRow(
                        label = "${unit.displayName} (${unit.symbol})",
                        selected = unitPrefs.weightUnit == unit,
                        onClick = { viewModel.setWeightUnit(unit) }
                    )
                    if (index < WeightUnit.entries.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                SettingsSubLabel(text = "Food Weight")
                FoodWeightUnit.entries.forEachIndexed { index, unit ->
                    SettingsRadioRow(
                        label = "${unit.displayName} (${unit.symbol})",
                        selected = unitPrefs.foodWeightUnit == unit,
                        onClick = { viewModel.setFoodWeightUnit(unit) }
                    )
                    if (index < FoodWeightUnit.entries.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                SettingsSubLabel(text = "Volume")
                VolumeUnit.entries.forEachIndexed { index, unit ->
                    SettingsRadioRow(
                        label = "${unit.displayName} (${unit.symbol})",
                        selected = unitPrefs.volumeUnit == unit,
                        onClick = { viewModel.setVolumeUnit(unit) }
                    )
                    if (index < VolumeUnit.entries.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }

            // ── Data Management ─────────────────────────────────────────────
            SettingsSection(title = "Data Management") {
                TextButton(
                    onClick = {
                        showExportChooser = true
                    },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (exportState is BackupOpState.InProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "  Exporting…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Export Data",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 0.5.dp
                )
                TextButton(
                    onClick = {
                        importLauncher.launch(arrayOf("application/json", "*/*"))
                    },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (importState is BackupOpState.InProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "  Importing…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Import Data",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── Health Connect Sync ─────────────────────────────────────────
            SettingsSection(title = "Health Connect Sync") {
                HealthConnectSyncSection(
                    hcState = healthConnectState,
                    syncStatus = syncStatus,
                    isSyncing = isSyncing,
                    onGrantPermissions = {
                        permissionLauncher.launch(HealthConnectManager.REQUIRED_PERMISSIONS)
                    },
                    onOpenHealthConnect = {
                        val settingsIntent = Intent().apply {
                            action = "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"
                        }
                        runCatching { context.startActivity(settingsIntent) }
                    },
                    onInstallHealthConnect = {
                        val playStoreIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=com.google.android.apps.healthdata")
                        )
                        runCatching { context.startActivity(playStoreIntent) }.onFailure {
                            // Fallback to browser if Play Store not available
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
                                )
                            )
                        }
                    },
                    onSync = { viewModel.triggerSync() },
                )
            }

            // ── Account ─────────────────────────────────────────────────────
            SettingsSection(title = "Account") {
                SettingsRow(label = "Pro Status", value = "Free")
            }

            // ── System ──────────────────────────────────────────────────────
            SettingsSection(title = "System") {
                SettingsRow(label = "App Version", value = BuildConfig.VERSION_NAME)
                SettingsRow(label = "Build", value = BuildConfig.VERSION_CODE.toString())
            }

            // ── Danger Zone ─────────────────────────────────────────────────
            SettingsSection(
                title = "Danger Zone",
                titleColor = MaterialTheme.colorScheme.error
            ) {
                Button(
                    onClick = { showResetConfirm1 = true },
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (resetState is BackupOpState.InProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onError
                        )
                        Text("  Resetting…", modifier = Modifier.padding(start = 4.dp))
                    } else {
                        Text("Reset App")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ── Export method chooser dialog ─────────────────────────────────────────
    if (showExportChooser) {
        AlertDialog(
            onDismissRequest = { showExportChooser = false },
            title = { Text("Export Backup") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = {
                            showExportChooser = false
                            exportLauncher.launch("ember_backup.json")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Save to device",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 0.5.dp
                    )
                    TextButton(
                        onClick = {
                            showExportChooser = false
                            viewModel.prepareEmailExport()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Email to myself",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportChooser = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Import overwrite confirmation dialog ─────────────────────────────────
    if (pendingImport != null) {
        val payload = pendingImport!!
        AlertDialog(
            onDismissRequest = { viewModel.cancelImport() },
            title = { Text("Overwrite All Data?") },
            text = {
                val exportDate = runCatching {
                    java.time.Instant.parse(payload.exportedAt)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                        .toString()
                }.getOrElse { payload.exportedAt.take(10) }
                Text(
                    "This will permanently replace all current app data with the backup " +
                        "from ${payload.appVersion} (exported $exportDate). " +
                        "This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmImport() }) {
                    Text("Restore", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelImport() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Reset App: first confirmation ────────────────────────────────────────
    if (showResetConfirm1) {
        AlertDialog(
            onDismissRequest = { showResetConfirm1 = false },
            title = { Text("Reset App?") },
            text = { Text("This will erase all local data. Are you sure you want to continue?") },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm1 = false
                    showResetConfirm2 = true
                }) {
                    Text("Continue", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm1 = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Reset App: second (final) confirmation ───────────────────────────────
    if (showResetConfirm2) {
        AlertDialog(
            onDismissRequest = { showResetConfirm2 = false },
            title = { Text("Are you absolutely sure?") },
            text = { Text("This action cannot be undone. All app data will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm2 = false
                    viewModel.resetAll()
                }) {
                    Text("Reset Everything", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm2 = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ── Health Connect Sync section ───────────────────────────────────────────────

@Composable
private fun HealthConnectSyncSection(
    hcState: com.projectember.mobile.sync.HealthConnectUiState,
    syncStatus: com.projectember.mobile.data.local.entities.SyncStatus?,
    isSyncing: Boolean,
    onGrantPermissions: () -> Unit,
    onOpenHealthConnect: () -> Unit,
    onInstallHealthConnect: () -> Unit,
    onSync: () -> Unit,
) {
    when (hcState.availability) {
        HealthConnectAvailability.CHECKING -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = "Checking Health Connect…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HealthConnectAvailability.NOT_SUPPORTED -> {
            HcStatusRow(
                icon = {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                },
                label = "Not supported",
                description = "Health Connect is not supported on this device.",
            )
        }

        HealthConnectAvailability.NOT_INSTALLED -> {
            HcStatusRow(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp),
                    )
                },
                label = "Health Connect not installed",
                description = "Install Health Connect to enable health data sync.",
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onInstallHealthConnect,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Install Health Connect")
            }
        }

        HealthConnectAvailability.AVAILABLE -> {
            if (!hcState.permissionsGranted) {
                // ── Permissions required ──────────────────────────────────
                HcStatusRow(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    label = "Permissions required",
                    description = "Grant Health Connect permissions to enable sync.",
                )
                hcState.errorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onGrantPermissions,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("  Grant Permissions", modifier = Modifier.padding(start = 4.dp))
                }
            } else {
                // ── Ready / sync status ───────────────────────────────────
                val statusText = when {
                    isSyncing -> "⏳ Syncing…"
                    syncStatus?.status == "success" -> "✅ Synced"
                    syncStatus?.status == "error" -> "❌ Sync error"
                    else -> "⏸ Ready to sync"
                }
                HcStatusRow(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    label = "Health Connect",
                    description = statusText,
                )

                syncStatus?.lastSyncTime?.let { time ->
                    SettingsRow(label = "Last sync", value = time)
                }

                if (syncStatus?.status == "error") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = syncStatus.message ?: "An error occurred during sync.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                } else {
                    syncStatus?.message?.takeIf { it.isNotBlank() }?.let { msg ->
                        SettingsRow(label = "Result", value = msg)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onSync,
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        text = if (isSyncing) "  Syncing…" else "  Sync Now",
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                OutlinedButton(
                    onClick = onOpenHealthConnect,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open Health Connect")
                }
            }
        }
    }
}

@Composable
private fun HcStatusRow(
    icon: @Composable () -> Unit,
    label: String,
    description: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon()
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Reusable setting composables ──────────────────────────────────────────────

@Composable
private fun SettingsSection(
    title: String,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = titleColor,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        thickness = 0.5.dp
    )
}

@Composable
private fun SettingsSubLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
    )
}

@Composable
private fun SettingsRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        TextButton(
            onClick = onClick,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

