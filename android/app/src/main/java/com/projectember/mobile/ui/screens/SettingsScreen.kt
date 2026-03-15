package com.projectember.mobile.ui.screens

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
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectember.mobile.BuildConfig
import com.projectember.mobile.data.local.FoodWeightUnit
import com.projectember.mobile.data.local.VolumeUnit
import com.projectember.mobile.data.local.WeightUnit
import com.projectember.mobile.ui.theme.ThemeOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val exportState by viewModel.exportState.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val pendingImport by viewModel.pendingImport.collectAsState()
    val resetState by viewModel.resetState.collectAsState()
    val selectedTheme by viewModel.selectedTheme.collectAsState()
    val unitPrefs by viewModel.unitPreferences.collectAsState()

    // Danger Zone confirmation state
    var showResetConfirm1 by remember { mutableStateOf(false) }
    var showResetConfirm2 by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

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
                ThemeOption.entries.forEachIndexed { index, option ->
                    SettingsRadioRow(
                        label = option.displayName,
                        selected = selectedTheme == option,
                        onClick = { viewModel.setTheme(option) }
                    )
                    if (index < ThemeOption.entries.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )
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
                        exportLauncher.launch("ember_backup.json")
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

            // ── Sync ────────────────────────────────────────────────────────
            SettingsSection(title = "Sync") {
                val statusText = when {
                    isSyncing -> "Syncing..."
                    syncStatus?.status == "success" -> "✅ Synced"
                    syncStatus?.status == "error" -> "❌ Sync error"
                    else -> "⏳ Never synced"
                }

                SettingsRow(label = "Sync Status", value = statusText)

                syncStatus?.lastSyncTime?.let { time ->
                    SettingsRow(label = "Last Sync Time", value = time)
                } ?: SettingsRow(label = "Last Sync Time", value = "—")

                syncStatus?.message?.let { msg ->
                    SettingsRow(label = "Message", value = msg)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.triggerSync() },
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = if (isSyncing) "  Syncing..." else "  Manual Sync",
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
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

