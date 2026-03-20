package com.projectember.mobile.ui.import

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.projectember.mobile.import.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JsonImportScreen(
    onNavigateBack: () -> Unit,
    vm: JsonImportViewModel = viewModel()
) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsState()
    var pastedJson by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            // read content
            val content = readUriContent(ctx as Activity, uri)
            if (content != null) {
                pastedJson = content
                vm.parseAndPreview(content)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Advanced JSON Import") }, navigationIcon = {
                IconButton(onClick = onNavigateBack) { Text("Back") }
            })
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = pastedJson,
                onValueChange = { pastedJson = it },
                label = { Text("Paste JSON here") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.parseAndPreview(pastedJson) }) { Text("Preview") }
                Button(onClick = { launcher.launch("application/json") }) { Text("Choose file") }
                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))

            when (state) {
                is JsonImportState.Idle -> Text("Preview results will appear here.")
                is JsonImportState.Error -> Text("Error: ${(state as JsonImportState.Error).message}")
                is JsonImportState.Importing -> CircularProgressIndicator()
                is JsonImportState.Done -> {
                    val s = (state as JsonImportState.Done).summary
                    Text("Import complete: ${s.valid} added, ${s.duplicates} duplicates, ${s.invalid} invalid")
                }
                is JsonImportState.Preview -> {
                    val p = state as JsonImportState.Preview
                    Text("Domain: ${p.domain}")
                    Text("Total: ${p.summary.total}  Valid: ${p.summary.valid}  Invalid: ${p.summary.invalid}")
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                        items(p.items) { item ->
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(text = item.raw.toString(), maxLines = 2)
                                    if (!item.valid) Text("Invalid: ${item.reason}", color = MaterialTheme.colorScheme.error)
                                    if (item.isDuplicate) Text("Duplicate", color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.importConfirmed() }) { Text("Import Valid Items") }
                        Button(onClick = { /* TODO: allow import including duplicates */ }) { Text("Import including duplicates") }
                    }
                }
            }
        }
    }
}

private fun readUriContent(activity: Activity, uri: Uri): String? {
    return try {
        val cr = activity.contentResolver
        cr.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream)).use { br ->
                br.readText()
            }
        }
    } catch (e: Exception) {
        null
    }
}

