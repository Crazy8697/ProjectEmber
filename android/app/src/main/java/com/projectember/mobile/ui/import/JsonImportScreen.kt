package com.projectember.mobile.ui.import

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.projectember.mobile.import.*
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JsonImportScreen(
    onNavigateBack: () -> Unit,
    initialDomain: String? = null,
    vm: JsonImportViewModel? = null
) {
    val ctx = LocalContext.current
    val viewModel: JsonImportViewModel = vm ?: viewModel(factory = JsonImportViewModel.factory(ctx))
    val state by viewModel.state.collectAsState()
    var pastedJson by remember { mutableStateOf("") }
    var showReferenceDialog by remember { mutableStateOf(false) }
    val contentScrollState = rememberScrollState()
    val templateScrollState = rememberScrollState()

    data class TemplateExample(val title: String, val domain: String, val json: String)

    val baseTemplates = remember {
        listOf(
            TemplateExample(
                title = "Weight",
                domain = "weight",
                json = """
                    {
                      "schemaVersion": 1,
                      "domain": "weight",
                      "items": [
                        { "date": "2026-03-20", "weightKg": 72.3, "source": "import" },
                        { "date": "2026-03-19", "weightLb": 165.4, "source": "import" }
                      ]
                    }
                """.trimIndent()
            ),
            TemplateExample(
                title = "Calories",
                domain = "calories",
                json = """
                    {
                      "schemaVersion": 1,
                      "domain": "calories",
                      "items": [
                        { "date": "2026-03-20", "calories": 1850 },
                        { "date": "2026-03-19", "calories": 2100 }
                      ]
                    }
                """.trimIndent()
            ),
            TemplateExample(
                title = "Protein",
                domain = "protein",
                json = """
                    {
                      "schemaVersion": 1,
                      "domain": "protein",
                      "items": [
                        { "date": "2026-03-20", "proteinG": 140.0 },
                        { "date": "2026-03-19", "proteinG": 128.5 }
                      ]
                    }
                """.trimIndent()
            ),
            TemplateExample(
                title = "Fat",
                domain = "fat",
                json = """
                    {
                      "schemaVersion": 1,
                      "domain": "fat",
                      "items": [
                        { "date": "2026-03-20", "fatG": 110.2 },
                        { "date": "2026-03-19", "fatG": 104.7 }
                      ]
                    }
                """.trimIndent()
            ),
            TemplateExample(
                title = "Total carbs",
                domain = "total_carbs",
                json = """
                    {
                      "schemaVersion": 1,
                      "domain": "total_carbs",
                      "items": [
                        { "date": "2026-03-20", "totalCarbsG": 60.0 },
                        { "date": "2026-03-19", "totalCarbsG": 55.0 }
                      ]
                    }
                """.trimIndent()
            ),
            TemplateExample(
                title = "Net carbs",
                domain = "net_carbs",
                json = """
                    {
                      "schemaVersion": 1,
                      "domain": "net_carbs",
                      "items": [
                        { "date": "2026-03-20", "netCarbsG": 23.5 },
                        { "date": "2026-03-19", "netCarbsG": 18.0 }
                      ]
                    }
                """.trimIndent()
            ),
            TemplateExample(
                title = "Fiber",
                domain = "fiber",
                json = """
                    {
                      "schemaVersion": 1,
                      "domain": "fiber",
                      "items": [
                        { "date": "2026-03-20", "fiberG": 26.0 },
                        { "date": "2026-03-19", "fiberG": 28.0 }
                      ]
                    }
                """.trimIndent()
            ),
            TemplateExample(
                title = "Water / hydration",
                domain = "hydration",
                json = """
                    {
                      "schemaVersion": 1,
                      "domain": "hydration",
                      "items": [
                        { "date": "2026-03-20", "waterMl": 3200 },
                        { "date": "2026-03-19", "waterMl": 2800 }
                      ]
                    }
                """.trimIndent()
            ),
            TemplateExample(
                title = "Sodium",
                domain = "sodium",
                json = """
                    {
                      "schemaVersion": 1,
                      "domain": "sodium",
                      "items": [
                        { "date": "2026-03-20", "sodiumMg": 4300 },
                        { "date": "2026-03-19", "sodiumMg": 4100 }
                      ]
                    }
                """.trimIndent()
            ),
            TemplateExample(
                title = "Potassium",
                domain = "potassium",
                json = """
                    {
                      "schemaVersion": 1,
                      "domain": "potassium",
                      "items": [
                        { "date": "2026-03-20", "potassiumMg": 3600 },
                        { "date": "2026-03-19", "potassiumMg": 3400 }
                      ]
                    }
                """.trimIndent()
            ),
            TemplateExample(
                title = "Magnesium",
                domain = "magnesium",
                json = """
                    {
                      "schemaVersion": 1,
                      "domain": "magnesium",
                      "items": [
                        { "date": "2026-03-20", "magnesiumMg": 420 },
                        { "date": "2026-03-19", "magnesiumMg": 390 }
                      ]
                    }
                """.trimIndent()
            ),
            TemplateExample(
                title = "Steps",
                domain = "steps",
                json = """
                    {
                      "schemaVersion": 1,
                      "domain": "steps",
                      "items": [
                        { "date": "2026-03-20", "steps": 11234, "time": "22:00" },
                        { "date": "2026-03-19", "steps": 9875, "time": "21:45" }
                      ]
                    }
                """.trimIndent()
            ),
            TemplateExample(
                title = "Distance",
                domain = "distance",
                json = """
                    {
                      "schemaVersion": 1,
                      "domain": "distance",
                      "items": [
                        { "date": "2026-03-20", "distanceKm": 6.2, "time": "22:00" },
                        { "date": "2026-03-19", "distanceKm": 4.8, "time": "21:45" }
                      ]
                    }
                """.trimIndent()
            ),
            TemplateExample(
                title = "Active calories",
                domain = "active_calories",
                json = """
                    {
                      "schemaVersion": 1,
                      "domain": "active_calories",
                      "items": [
                        { "date": "2026-03-20", "caloriesBurned": 620, "time": "22:00" },
                        { "date": "2026-03-19", "caloriesBurned": 580, "time": "21:45" }
                      ]
                    }
                """.trimIndent()
            ),
            TemplateExample(
                title = "Exercise sessions",
                domain = "exercise_sessions",
                json = """
                    {
                      "schemaVersion": 1,
                      "domain": "exercise_sessions",
                      "items": [
                        { "date": "2026-03-20", "sessions": 2, "time": "22:00" },
                        { "date": "2026-03-19", "sessions": 1, "time": "21:45" }
                      ]
                    }
                """.trimIndent()
            ),
            TemplateExample(
                title = "Heart rate",
                domain = "heart_rate",
                json = """
                    {
                      "schemaVersion": 1,
                      "domain": "heart_rate",
                      "items": [
                        { "date": "2026-03-20", "time": "08:30", "bpm": 62 },
                        { "date": "2026-03-19", "time": "07:55", "bpm": 64 }
                      ]
                    }
                """.trimIndent()
            ),
            TemplateExample(
                title = "Resting heart rate",
                domain = "resting_heart_rate",
                json = """
                    {
                      "schemaVersion": 1,
                      "domain": "resting_heart_rate",
                      "items": [
                        { "date": "2026-03-20", "time": "06:30", "bpm": 54 },
                        { "date": "2026-03-19", "time": "06:30", "bpm": 55 }
                      ]
                    }
                """.trimIndent()
            ),
            TemplateExample(
                title = "Sleep",
                domain = "sleep",
                json = """
                    {
                      "schemaVersion": 1,
                      "domain": "sleep",
                      "items": [
                        { "date": "2026-03-20", "time": "07:00", "hours": 7.8 },
                        { "date": "2026-03-19", "time": "07:05", "hours": 7.1 }
                      ]
                    }
                """.trimIndent()
            ),
            TemplateExample(
                title = "Blood pressure",
                domain = "blood_pressure",
                json = """
                    {
                      "schemaVersion": 1,
                      "domain": "blood_pressure",
                      "items": [
                        { "date": "2026-03-20", "time": "08:30", "systolic": 118, "diastolic": 74 },
                        { "date": "2026-03-19", "time": "08:15", "systolic": 121, "diastolic": 76 }
                      ]
                    }
                """.trimIndent()
            ),
            TemplateExample(
                title = "Blood glucose",
                domain = "blood_glucose",
                json = """
                    {
                      "schemaVersion": 1,
                      "domain": "blood_glucose",
                      "items": [
                        { "date": "2026-03-20", "time": "07:30", "mgDl": 92 },
                        { "date": "2026-03-19", "time": "07:15", "mgDl": 95 }
                      ]
                    }
                """.trimIndent()
            ),
            TemplateExample(
                title = "Body temperature",
                domain = "body_temperature",
                json = """
                    {
                      "schemaVersion": 1,
                      "domain": "body_temperature",
                      "items": [
                        { "date": "2026-03-20", "time": "07:00", "celsius": 36.6 },
                        { "date": "2026-03-19", "time": "07:00", "celsius": 36.7 }
                      ]
                    }
                """.trimIndent()
            ),
            TemplateExample(
                title = "Oxygen saturation",
                domain = "oxygen_saturation",
                json = """
                    {
                      "schemaVersion": 1,
                      "domain": "oxygen_saturation",
                      "items": [
                        { "date": "2026-03-20", "time": "07:30", "spo2": 98 },
                        { "date": "2026-03-19", "time": "07:20", "spo2": 97 }
                      ]
                    }
                """.trimIndent()
            ),
            TemplateExample(
                title = "Respiratory rate",
                domain = "respiratory_rate",
                json = """
                    {
                      "schemaVersion": 1,
                      "domain": "respiratory_rate",
                      "items": [
                        { "date": "2026-03-20", "time": "07:30", "breathsPerMin": 13 },
                        { "date": "2026-03-19", "time": "07:30", "breathsPerMin": 14 }
                      ]
                    }
                """.trimIndent()
            )
        )
    }

    val templateExamples = remember(initialDomain, baseTemplates) {
        if (initialDomain.isNullOrBlank()) baseTemplates
        else {
            val domainKey = initialDomain.lowercase()
            val (matched, others) = baseTemplates.partition { it.domain == domainKey }
            matched + others
        }
    }

    val activeTemplateJson = remember(templateExamples) {
        templateExamples.firstOrNull()?.json ?: baseTemplates.first().json
    }

    val templateReferenceText = remember(templateExamples) {
        buildString {
            appendLine("// Shared schema")
            appendLine("{")
            appendLine("  \"schemaVersion\": 1,")
            appendLine("  \"domain\": \"<metric-domain>\",")
            appendLine("  \"items\": [ ... ]")
            appendLine("}")
            appendLine()
            templateExamples.forEach { example ->
                appendLine("// ${example.title}")
                appendLine(example.json)
                appendLine()
            }
        }.trim()
    }

    val domainHints = remember {
        mapOf(
            "weight" to "Use weightKg or weightLb per item.",
            "calories" to "Provide 'calories' per day (kcal).",
            "protein" to "Provide protein grams using 'proteinG'.",
            "fat" to "Provide fat grams using 'fatG'.",
            "total_carbs" to "Provide total carbs using 'totalCarbsG'.",
            "net_carbs" to "Provide net carbs using 'netCarbsG'.",
            "fiber" to "Provide fiber using 'fiberG'.",
            "hydration" to "Provide water intake via 'waterMl'.",
            "sodium" to "Provide sodium in milligrams using 'sodiumMg'.",
            "potassium" to "Provide potassium in milligrams using 'potassiumMg'.",
            "magnesium" to "Provide magnesium in milligrams using 'magnesiumMg'.",
            "steps" to "Provide 'steps' along with a timestamp.",
            "distance" to "Provide 'distanceKm' (or convertible) plus time.",
            "active_calories" to "Provide 'caloriesBurned' plus time.",
            "exercise_sessions" to "Provide session count via 'sessions' plus time.",
            "heart_rate" to "Provide BPM readings with time stamps.",
            "resting_heart_rate" to "Provide resting BPM with time.",
            "sleep" to "Provide hours slept via 'hours' with a completion time.",
            "blood_pressure" to "Provide 'systolic' and 'diastolic' with time.",
            "blood_glucose" to "Provide blood glucose via 'mgDl' with time.",
            "body_temperature" to "Provide temperature via 'celsius' (or °F) with time.",
            "oxygen_saturation" to "Provide SPO2 percentage via 'spo2' with time.",
            "respiratory_rate" to "Provide breaths per minute via 'breathsPerMin' with time."
        )
    }

    // If launched from a metric history screen, this suggested domain will pre-fill the import domain.
    LaunchedEffect(initialDomain) { viewModel.setSuggestedDomain(initialDomain) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            // read content
            val content = readUriContent(ctx as Activity, uri)
            if (content != null) {
                pastedJson = content
                viewModel.parseAndPreview(content)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("JSON Import") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(contentScrollState)
        ) {
            OutlinedTextField(
                value = pastedJson,
                onValueChange = { pastedJson = it },
                label = { Text("Paste JSON here") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )

            Spacer(Modifier.height(8.dp))

            // 2x2 button grid (Template / Clear on first row, Choose File / Preview on second row)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            pastedJson = activeTemplateJson
                            viewModel.reset()
                        }
                    ) { Text("Template") }

                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            pastedJson = ""
                            viewModel.reset()
                        }
                    ) { Text("Clear") }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { launcher.launch("application/json") }
                    ) { Text("Choose File") }

                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.parseAndPreview(pastedJson) }
                    ) { Text("Preview") }
                }
            }

            domainHints[initialDomain?.lowercase()]?.let { hint ->
                Spacer(Modifier.height(8.dp))
                Text(hint, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showReferenceDialog = true }) { Text("View supported domains") }

            Spacer(Modifier.height(12.dp))

            when (state) {
                is JsonImportState.Idle -> Text("Preview results will appear here.")
                is JsonImportState.Error -> Text("Error: ${(state as JsonImportState.Error).message}")
                is JsonImportState.Importing -> CircularProgressIndicator()
                is JsonImportState.Done -> {
                    val s = (state as JsonImportState.Done).summary
                    Text("Import complete: ${s.valid} added, ${s.duplicates} duplicates, ${s.invalid} invalid")
                    if (s.errors.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Issues detected", style = MaterialTheme.typography.labelLarge)
                        s.errors.forEach { err ->
                            Text("• $err", style = MaterialTheme.typography.bodySmall)
                        }
                    }
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
                        Button(onClick = { viewModel.importConfirmed() }) { Text("Import Valid Items") }
                        Button(onClick = { viewModel.importConfirmed(includeDuplicates = true) }) {
                            Text("Import including duplicates")
                        }
                    }
                }
            }
        }

        if (showReferenceDialog) {
            AlertDialog(
                onDismissRequest = { showReferenceDialog = false },
                confirmButton = {
                    TextButton(onClick = { showReferenceDialog = false }) { Text("Close") }
                },
                title = { Text("Supported domains") },
                text = {
                    SelectionContainer {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 360.dp)
                                .verticalScroll(templateScrollState)
                        ) {
                            Text(
                                templateReferenceText,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            )
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
    } catch (_: Exception) {
        null
    }
}
