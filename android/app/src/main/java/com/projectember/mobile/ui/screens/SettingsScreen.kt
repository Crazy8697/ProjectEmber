package com.projectember.mobile.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
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
import com.projectember.mobile.data.local.EatingStyle
import com.projectember.mobile.data.local.FoodWeightUnit
import com.projectember.mobile.data.local.HealthMetric
import com.projectember.mobile.data.local.MealWindow
import com.projectember.mobile.data.local.VolumeUnit
import com.projectember.mobile.data.local.WeightUnit
import com.projectember.mobile.sync.HealthConnectUiState
import com.projectember.mobile.ui.theme.ThemeOption
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val isSyncing by viewModel.isSyncing.collectAsState()
    val healthConnectState by viewModel.healthConnectState.collectAsState()
    val exportState by viewModel.exportState.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val pendingImport by viewModel.pendingImport.collectAsState()
    val pendingEmailExport by viewModel.pendingEmailExport.collectAsState()
    val resetState by viewModel.resetState.collectAsState()
    val selectedTheme by viewModel.selectedTheme.collectAsState()
    val unitPrefs by viewModel.unitPreferences.collectAsState()
    val dailyRhythm by viewModel.dailyRhythm.collectAsState()
    val mealTiming by viewModel.mealTiming.collectAsState()
    val enabledMetrics by viewModel.enabledMetrics.collectAsState()
    val graphEnabledMetrics by viewModel.graphEnabledMetrics.collectAsState()

    val context = LocalContext.current

    // Danger Zone confirmation state
    var showResetConfirm1 by remember { mutableStateOf(false) }
    var showResetConfirm2 by remember { mutableStateOf(false) }

    // Export method chooser state
    var showExportChooser by remember { mutableStateOf(false) }

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

    // ── Health Connect permission launcher ────────────────────────────────────
    val hcPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        viewModel.onPermissionsResult(granted)
    }

    // Check HC status when the screen is first composed
    LaunchedEffect(Unit) {
        viewModel.checkHealthConnectStatus()
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

            // ── Daily Rhythm ────────────────────────────────────────────────
            SettingsSection(title = "Daily Rhythm") {
                Text(
                    text = "Helps the app calculate smarter pacing by using your actual waking and eating schedule instead of a flat midnight-to-midnight baseline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // ── Wake time ─────────────────────────────────────────────────
                var showWakePicker by remember { mutableStateOf(false) }
                SettingsTimeRow(
                    label = "Wake time",
                    hour = dailyRhythm.wakeHour,
                    minute = dailyRhythm.wakeMinute,
                    onEditClick = { showWakePicker = true }
                )
                if (showWakePicker) {
                    val tpState = rememberTimePickerState(
                        initialHour = dailyRhythm.wakeHour,
                        initialMinute = dailyRhythm.wakeMinute,
                        is24Hour = true
                    )
                    AlertDialog(
                        onDismissRequest = { showWakePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                showWakePicker = false
                                viewModel.setWakeTime(tpState.hour, tpState.minute)
                            }) { Text("OK") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showWakePicker = false }) { Text("Cancel") }
                        },
                        text = { TimePicker(state = tpState) }
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 0.5.dp
                )

                // ── Sleep time ────────────────────────────────────────────────
                var showSleepPicker by remember { mutableStateOf(false) }
                SettingsTimeRow(
                    label = "Sleep time",
                    hour = dailyRhythm.sleepHour,
                    minute = dailyRhythm.sleepMinute,
                    onEditClick = { showSleepPicker = true }
                )
                if (showSleepPicker) {
                    val tpState = rememberTimePickerState(
                        initialHour = dailyRhythm.sleepHour,
                        initialMinute = dailyRhythm.sleepMinute,
                        is24Hour = true
                    )
                    AlertDialog(
                        onDismissRequest = { showSleepPicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                showSleepPicker = false
                                viewModel.setSleepTime(tpState.hour, tpState.minute)
                            }) { Text("OK") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSleepPicker = false }) { Text("Cancel") }
                        },
                        text = { TimePicker(state = tpState) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Eating style preset ───────────────────────────────────────
                SettingsSubLabel(text = "Eating Style")
                EatingStyle.entries.forEachIndexed { index, style ->
                    SettingsRadioRow(
                        label = style.displayName,
                        selected = dailyRhythm.eatingStyle == style,
                        onClick = { viewModel.setEatingStyle(style) }
                    )
                    if (index < EatingStyle.entries.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }

            // ── Meal Timing (optional) ───────────────────────────────────────
            SettingsSection(title = "Meal Timing (Optional)") {
                Text(
                    text = "Refines pacing by using your actual meal windows. When left blank the app falls back to the Daily Rhythm setting above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // ── Breakfast ─────────────────────────────────────────────────
                MealWindowRow(
                    mealName = "Breakfast",
                    window = mealTiming.breakfastWindow,
                    defaultStartHour = 7,
                    defaultEndHour = 9,
                    onWindowChange = { viewModel.setBreakfastWindow(it) }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 0.5.dp
                )

                // ── Lunch ─────────────────────────────────────────────────────
                MealWindowRow(
                    mealName = "Lunch",
                    window = mealTiming.lunchWindow,
                    defaultStartHour = 12,
                    defaultEndHour = 13,
                    onWindowChange = { viewModel.setLunchWindow(it) }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 0.5.dp
                )

                // ── Dinner ────────────────────────────────────────────────────
                MealWindowRow(
                    mealName = "Dinner",
                    window = mealTiming.dinnerWindow,
                    defaultStartHour = 18,
                    defaultEndHour = 20,
                    onWindowChange = { viewModel.setDinnerWindow(it) }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 0.5.dp
                )

                // ── Snack ─────────────────────────────────────────────────────
                MealWindowRow(
                    mealName = "Snack",
                    window = mealTiming.snackWindow,
                    defaultStartHour = 15,
                    defaultEndHour = 16,
                    onWindowChange = { viewModel.setSnackWindow(it) }
                )
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

            // ── Health Connect / Sync ────────────────────────────────────────
            SettingsSection(title = "Health Connect") {
                when (val hcState = healthConnectState) {
                    is HealthConnectUiState.Checking -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Checking Health Connect…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }

                    is HealthConnectUiState.NotInstalled -> {
                        SettingsRow(
                            label = "Status",
                            value = "Health Connect not available on this device"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Health Connect is required for health data sync. " +
                                "It is built into Android 14+ and available as a separate " +
                                "app on earlier versions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is HealthConnectUiState.PermissionsRequired -> {
                        SettingsRow(label = "Status", value = "⚠️ Permissions required")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Project Ember needs read access to health and fitness data " +
                                "in Health Connect to display metrics across your screens.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                hcPermissionLauncher.launch(
                                    viewModel.healthConnectPermissions
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Health Connect Permissions")
                        }
                    }

                    is HealthConnectUiState.Ready -> {
                        SettingsRow(label = "Status", value = "✅ Ready to sync")
                        Spacer(modifier = Modifier.height(12.dp))
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
                                text = if (isSyncing) "Syncing…" else "Sync from Health Connect",
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                hcPermissionLauncher.launch(viewModel.healthConnectPermissions)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Manage Permissions")
                        }
                    }

                    is HealthConnectUiState.Syncing -> {
                        SettingsRow(label = "Status", value = "⏳ Syncing…")
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Reading Health Connect data…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }

                    is HealthConnectUiState.LastSyncSuccess -> {
                        SettingsRow(label = "Status", value = "✅ Synced")
                        SettingsRow(label = "Last Sync", value = hcState.lastSyncTime)
                        SettingsRow(label = "Result", value = hcState.summary)
                        Spacer(modifier = Modifier.height(12.dp))
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
                                text = if (isSyncing) "Syncing…" else "Sync Again",
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                hcPermissionLauncher.launch(viewModel.healthConnectPermissions)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Manage Permissions")
                        }
                    }

                    is HealthConnectUiState.Error -> {
                        SettingsRow(label = "Status", value = "❌ Error")
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = hcState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.checkHealthConnectStatus() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Retry")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                hcPermissionLauncher.launch(viewModel.healthConnectPermissions)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Manage Permissions")
                        }
                    }
                }
            }

            // ── Health Metrics ──────────────────────────────────────────────
            SettingsSection(title = "Health Metrics") {
                Text(
                    text = "Choose which health metrics are shown across the app. " +
                        "Disabled metrics are hidden from all screens. " +
                        "Enable the graph toggle to surface trend content for that metric.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Column header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Show",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(48.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = "Graph",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(48.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                SettingsSubLabel(text = "Keto Screen")
                HealthMetricRowWithGraph(
                    label = HealthMetric.WEIGHT.displayName,
                    checked = enabledMetrics[HealthMetric.WEIGHT] != false,
                    onCheckedChange = { viewModel.setMetricEnabled(HealthMetric.WEIGHT, it) },
                    graphChecked = graphEnabledMetrics[HealthMetric.WEIGHT] == true,
                    onGraphCheckedChange = { viewModel.setMetricGraphEnabled(HealthMetric.WEIGHT, it) },
                    graphEnabled = enabledMetrics[HealthMetric.WEIGHT] != false,
                )

                Spacer(modifier = Modifier.height(12.dp))
                SettingsSubLabel(text = "Exercise Screen")
                val exerciseMetrics = listOf(
                    HealthMetric.STEPS,
                    HealthMetric.DISTANCE,
                    HealthMetric.ACTIVE_CALORIES,
                    HealthMetric.EXERCISE_SESSIONS,
                )
                exerciseMetrics.forEachIndexed { index, metric ->
                    HealthMetricRowWithGraph(
                        label = metric.displayName,
                        checked = enabledMetrics[metric] != false,
                        onCheckedChange = { viewModel.setMetricEnabled(metric, it) },
                        graphChecked = graphEnabledMetrics[metric] == true,
                        onGraphCheckedChange = { viewModel.setMetricGraphEnabled(metric, it) },
                        graphEnabled = enabledMetrics[metric] != false,
                    )
                    if (index < exerciseMetrics.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                SettingsSubLabel(text = "Health Screen")
                val healthMetrics = listOf(
                    HealthMetric.HEART_RATE,
                    HealthMetric.RESTING_HEART_RATE,
                    HealthMetric.SLEEP,
                    HealthMetric.BLOOD_PRESSURE,
                    HealthMetric.BLOOD_GLUCOSE,
                    HealthMetric.BODY_TEMPERATURE,
                    HealthMetric.OXYGEN_SATURATION,
                    HealthMetric.RESPIRATORY_RATE,
                )
                healthMetrics.forEachIndexed { index, metric ->
                    HealthMetricRowWithGraph(
                        label = metric.displayName,
                        checked = enabledMetrics[metric] != false,
                        onCheckedChange = { viewModel.setMetricEnabled(metric, it) },
                        graphChecked = graphEnabledMetrics[metric] == true,
                        onGraphCheckedChange = { viewModel.setMetricGraphEnabled(metric, it) },
                        graphEnabled = enabledMetrics[metric] != false,
                    )
                    if (index < healthMetrics.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )
                    }
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

/**
 * A settings row that displays a time value and opens a time picker when tapped.
 */
@Composable
private fun SettingsTimeRow(
    label: String,
    hour: Int,
    minute: Int,
    onEditClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
        TextButton(onClick = onEditClick) {
            Text(
                text = "%02d:%02d".format(hour, minute),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * A row for one optional meal window with a toggle and individual start/end time pickers.
 * When the toggle is off the window is cleared (null); when on, tapping either time
 * opens the corresponding time picker dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealWindowRow(
    mealName: String,
    window: MealWindow?,
    defaultStartHour: Int,
    defaultEndHour: Int,
    onWindowChange: (MealWindow?) -> Unit
) {
    val isEnabled = window != null
    val startH = window?.startHour ?: defaultStartHour
    val startM = window?.startMinute ?: 0
    val endH = window?.endHour ?: defaultEndHour
    val endM = window?.endMinute ?: 0

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = mealName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp)
            )
            Switch(
                checked = isEnabled,
                onCheckedChange = { checked ->
                    if (checked) {
                        onWindowChange(MealWindow(startH, startM, endH, endM))
                    } else {
                        onWindowChange(null)
                    }
                }
            )
        }
        if (isEnabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Start:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { showStartPicker = true }) {
                    Text(
                        text = "%02d:%02d".format(startH, startM),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "End:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { showEndPicker = true }) {
                    Text(
                        text = "%02d:%02d".format(endH, endM),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    if (showStartPicker) {
        val tpState = rememberTimePickerState(
            initialHour = startH,
            initialMinute = startM,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showStartPicker = false
                    onWindowChange(MealWindow(tpState.hour, tpState.minute, endH, endM))
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = tpState) }
        )
    }

    if (showEndPicker) {
        val tpState = rememberTimePickerState(
            initialHour = endH,
            initialMinute = endM,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showEndPicker = false
                    onWindowChange(MealWindow(startH, startM, tpState.hour, tpState.minute))
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = tpState) }
        )
    }
}

/** A toggle row for a health metric with label on the left and Switch on the right. */
@Composable
private fun HealthMetricToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * A row for a health metric with two independent switches:
 * - **Show** (enabled/visible): whether the metric card appears at all.
 * - **Graph**: whether the trend/graph section is shown for this metric.
 *
 * The graph switch is dimmed and forced-off when the metric is disabled.
 */
@Composable
private fun HealthMetricRowWithGraph(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    graphChecked: Boolean,
    onGraphCheckedChange: (Boolean) -> Unit,
    graphEnabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.width(48.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Switch(
            checked = graphChecked && graphEnabled,
            onCheckedChange = { if (graphEnabled) onGraphCheckedChange(it) },
            enabled = graphEnabled,
            modifier = Modifier.width(48.dp)
        )
    }
}

