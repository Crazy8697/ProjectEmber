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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectember.mobile.data.keto.KetoCandidateItem
import com.projectember.mobile.data.keto.KetoDuplicateHandling
import com.projectember.mobile.data.keto.KetoImportPreview
import com.projectember.mobile.data.keto.KetoParseError

private enum class KetoImportMethod { FILE, PASTE }

/** Template shown to users who click "Load Template" in the paste import flow. */
private val KETO_IMPORT_TEMPLATE = """
{
  "schemaVersion": 1,
  "exportedAt": "2026-03-18T12:00:00Z",
  "ketoEntries": [
    {
      "label": "Scrambled Eggs",
      "eventType": "meal",
      "calories": 320,
      "proteinG": 22,
      "fatG": 24,
      "netCarbsG": 2,
      "waterMl": 0,
      "sodiumMg": 380,
      "potassiumMg": 200,
      "magnesiumMg": 20,
      "entryDate": "2026-03-18",
      "eventTimestamp": "2026-03-18 08:15",
      "notes": "Morning eggs",
      "servings": 1.0
    }
  ]
}
""".trimIndent()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KetoNerdModeScreen(
    viewModel: KetoNerdModeViewModel,
    onNavigateBack: () -> Unit
) {
    val importState by viewModel.importState.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val duplicateHandling by viewModel.duplicateHandling.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    var importMethod by remember { mutableStateOf(KetoImportMethod.FILE) }
    var pasteText by remember { mutableStateOf("") }
    var showTemplateReplaceDialog by remember { mutableStateOf(false) }

    // ── Template replace confirmation dialog ──────────────────────────────────
    if (showTemplateReplaceDialog) {
        AlertDialog(
            onDismissRequest = { showTemplateReplaceDialog = false },
            title = { Text("Replace JSON?") },
            text = { Text("This will replace your current JSON content with the template. Any unsaved changes will be lost. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    pasteText = KETO_IMPORT_TEMPLATE
                    showTemplateReplaceDialog = false
                }) {
                    Text("Replace")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTemplateReplaceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── SAF file-open launcher (Import) ──────────────────────────────────────
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.loadImportFromUri(uri)
    }

    // ── Import state feedback ─────────────────────────────────────────────────
    LaunchedEffect(importState) {
        when (val s = importState) {
            is NerdModeOpState.Success -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.clearImportState()
            }
            is NerdModeOpState.Error -> {
                snackbarHostState.showSnackbar("Import error: ${s.message}")
                viewModel.clearImportState()
            }
            else -> Unit
        }
    }

    val isBusy = importState is NerdModeOpState.InProgress

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Keto Advanced Tools",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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

            // ── Info banner ───────────────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Keto JSON import for power users. " +
                        "Normal Keto logging is available from the Keto Tracker screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }

            // ── Import section ────────────────────────────────────────────────
            KetoNerdModeSection(title = "Import Keto Entries") {
                Text(
                    text = "Import Keto entries from a JSON file or by pasting JSON directly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Method toggle
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KetoImportMethodButton(
                        label = "From File",
                        selected = importMethod == KetoImportMethod.FILE,
                        onClick = { importMethod = KetoImportMethod.FILE },
                        modifier = Modifier.weight(1f)
                    )
                    KetoImportMethodButton(
                        label = "Paste JSON",
                        selected = importMethod == KetoImportMethod.PASTE,
                        onClick = { importMethod = KetoImportMethod.PASTE },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                when (importMethod) {
                    KetoImportMethod.FILE -> {
                        Button(
                            onClick = { importFileLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Reading…")
                            } else {
                                Icon(
                                    Icons.Default.FileUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Choose File")
                            }
                        }
                    }

                    KetoImportMethod.PASTE -> {
                        // ── Helper buttons ────────────────────────────────────
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (pasteText.isBlank()) {
                                        pasteText = KETO_IMPORT_TEMPLATE
                                    } else {
                                        showTemplateReplaceDialog = true
                                    }
                                },
                                enabled = !isBusy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Load Template")
                            }
                            OutlinedButton(
                                onClick = { pasteText = "" },
                                enabled = !isBusy && pasteText.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Clear")
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Paste Keto JSON here. Use 'Load Template' to see the expected format.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = pasteText,
                            onValueChange = { pasteText = it },
                            label = { Text("Paste JSON here") },
                            placeholder = { Text("{ \"ketoEntries\": [ … ] }") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            maxLines = 20,
                            enabled = !isBusy
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.validatePasteImport(pasteText) },
                            enabled = !isBusy && pasteText.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Validating…")
                            } else {
                                Text("Validate JSON")
                            }
                        }
                    }
                }
            }

            // ── Import preview (shows after successful validation) ─────────────
            if (preview != null) {
                KetoImportPreviewSection(
                    preview = preview!!,
                    duplicateHandling = duplicateHandling,
                    onDuplicateHandlingChange = viewModel::setDuplicateHandling,
                    onCommit = { viewModel.commitImport() },
                    onCancel = { viewModel.cancelImport() },
                    isBusy = isBusy
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Reusable section card ─────────────────────────────────────────────────────

@Composable
private fun KetoNerdModeSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

// ── Import method toggle button ───────────────────────────────────────────────

@Composable
private fun KetoImportMethodButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

// ── Import preview section ────────────────────────────────────────────────────

@Composable
private fun KetoImportPreviewSection(
    preview: KetoImportPreview,
    duplicateHandling: KetoDuplicateHandling,
    onDuplicateHandlingChange: (KetoDuplicateHandling) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
    isBusy: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Import Preview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Summary
            KetoPreviewSummaryRow(preview)

            // Invalid entries (if any)
            if (preview.invalid.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Entries that will be skipped (${preview.invalid.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(4.dp))
                preview.invalid.forEach { err -> KetoInvalidEntryRow(err) }
            }

            // Valid entries (summary, up to 10)
            if (preview.valid.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Valid entries (${preview.valid.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                preview.valid.take(10).forEach { item -> KetoValidEntryRow(item) }
                if (preview.valid.size > 10) {
                    Text(
                        text = "…and ${preview.valid.size - 10} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }

            // Duplicate handling (only shown when there are duplicates)
            if (preview.duplicates.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Duplicate handling (${preview.duplicates.size} found)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "A duplicate matches an existing entry by label, date, and timestamp.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                KetoDuplicateHandling.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = duplicateHandling == option,
                            onClick = { onDuplicateHandlingChange(option) }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = when (option) {
                                    KetoDuplicateHandling.SKIP -> "Skip duplicates"
                                    KetoDuplicateHandling.IMPORT_AS_NEW -> "Import as new"
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = when (option) {
                                    KetoDuplicateHandling.SKIP -> "Existing entries are kept unchanged"
                                    KetoDuplicateHandling.IMPORT_AS_NEW -> "Duplicates are added alongside existing ones"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = onCancel,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onCommit,
                    enabled = !isBusy && preview.valid.isNotEmpty(),
                    modifier = Modifier.weight(2f)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Importing…")
                    } else {
                        Text("Commit Import")
                    }
                }
            }
        }
    }
}

// ── Preview summary ───────────────────────────────────────────────────────────

@Composable
private fun KetoPreviewSummaryRow(preview: KetoImportPreview) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        KetoSummaryLine(
            label = "Found",
            value = "${preview.totalFound} entr${if (preview.totalFound != 1) "ies" else "y"}"
        )
        KetoSummaryLine(
            label = "Valid",
            value = "${preview.valid.size}",
            iconOk = preview.valid.isNotEmpty()
        )
        if (preview.invalid.isNotEmpty()) {
            KetoSummaryLine(
                label = "Will be skipped",
                value = "${preview.invalid.size}",
                isError = true
            )
        }
        if (preview.duplicates.isNotEmpty()) {
            KetoSummaryLine(
                label = "Duplicates",
                value = "${preview.duplicates.size}"
            )
        }
    }
}

@Composable
private fun KetoSummaryLine(
    label: String,
    value: String,
    iconOk: Boolean = false,
    isError: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isError) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
        } else if (iconOk) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
        if (isError || iconOk) Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

// ── Invalid entry row ─────────────────────────────────────────────────────────

@Composable
private fun KetoInvalidEntryRow(err: KetoParseError) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .size(14.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = err.reason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

// ── Valid entry row ───────────────────────────────────────────────────────────

@Composable
private fun KetoValidEntryRow(item: KetoCandidateItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (item.isDuplicate) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = buildString {
                append(item.candidate.label)
                append(" (${item.candidate.entryDate})")
                if (item.isDuplicate) append(" — duplicate")
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (item.isDuplicate) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}
