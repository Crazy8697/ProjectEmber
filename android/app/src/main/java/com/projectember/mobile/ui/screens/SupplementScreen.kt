package com.projectember.mobile.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectember.mobile.data.local.entities.StackDefinition
import com.projectember.mobile.data.local.entities.SupplementEntry
import com.projectember.mobile.ui.theme.KetoAccent
import com.projectember.mobile.ui.theme.OnSurfaceVariant
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StacksScreen(
    viewModel: StacksViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAddDefinition: () -> Unit,
    onNavigateToEditDefinition: (Int) -> Unit,
    onNavigateToAddLog: () -> Unit,
    onNavigateToEditLog: (Int) -> Unit
) {
    val definitions by viewModel.definitions.collectAsState()
    val logEntries by viewModel.logEntries.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Library", "History")

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Stacks",
                        style = MaterialTheme.typography.headlineMedium,
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = if (selectedTab == 0) onNavigateToAddDefinition else onNavigateToAddLog,
                containerColor = KetoAccent
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = if (selectedTab == 0) "Add stack" else "Log entry",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> LibraryTab(
                    definitions = definitions,
                    onEditDefinition = { onNavigateToEditDefinition(it.id) },
                    onQuickLog = { definition ->
                        viewModel.quickLog(definition) { ketoLinked ->
                            coroutineScope.launch {
                                val msg = if (ketoLinked) {
                                    "Logged \"${definition.name}\" — Keto totals updated"
                                } else {
                                    "Logged \"${definition.name}\""
                                }
                                snackbarHostState.showSnackbar(msg)
                            }
                        }
                    },
                    onAddDefinition = onNavigateToAddDefinition
                )
                1 -> HistoryTab(
                    entries = logEntries,
                    onEditEntry = { onNavigateToEditLog(it.id) },
                    onAddLog = onNavigateToAddLog
                )
            }
        }
    }
}

// ── Library tab ───────────────────────────────────────────────────────────────

@Composable
private fun LibraryTab(
    definitions: List<StackDefinition>,
    onEditDefinition: (StackDefinition) -> Unit,
    onQuickLog: (StackDefinition) -> Unit,
    onAddDefinition: () -> Unit
) {
    if (definitions.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No stacks yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap + to save your first reusable stack.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(definitions, key = { it.id }) { definition ->
                StackDefinitionCard(
                    definition = definition,
                    onEdit = { onEditDefinition(definition) },
                    onLog = { onQuickLog(definition) }
                )
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StackDefinitionCard(
    definition: StackDefinition,
    onEdit: () -> Unit,
    onLog: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = definition.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    val doseLabel = buildString {
                        definition.defaultDose?.takeIf { it.isNotBlank() }?.let { append(it) }
                        definition.defaultUnit?.takeIf { it.isNotBlank() }?.let { append(" $it") }
                    }.trim()
                    if (doseLabel.isNotEmpty()) {
                        Text(
                            text = doseLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = KetoAccent,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                // Keto badge
                if (definition.hasNutritionData()) {
                    Badge(containerColor = KetoAccent.copy(alpha = 0.15f)) {
                        Text(
                            text = "Keto",
                            style = MaterialTheme.typography.labelSmall,
                            color = KetoAccent
                        )
                    }
                }
            }

            definition.notes?.takeIf { it.isNotBlank() }?.let { noteText ->
                Text(
                    text = noteText,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                    maxLines = 2
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f).padding(end = 6.dp)
                ) {
                    Text("Edit")
                }
                Button(
                    onClick = onLog,
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KetoAccent)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).padding(end = 4.dp)
                    )
                    Text("Log Now")
                }
            }
        }
    }
}

// ── History tab ───────────────────────────────────────────────────────────────

@Composable
private fun HistoryTab(
    entries: List<SupplementEntry>,
    onEditEntry: (SupplementEntry) -> Unit,
    onAddLog: () -> Unit
) {
    if (entries.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No logs yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Log a stack from the Library tab, or tap + for a one-off entry.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        val grouped = entries.groupBy { it.entryDate }
        val sortedDates = grouped.keys.sortedDescending()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            sortedDates.forEach { date ->
                item(key = "header_$date") {
                    Text(
                        text = formatStackDate(date),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                val dayEntries = grouped[date] ?: emptyList()
                items(dayEntries, key = { it.id }) { entry ->
                    StackLogCard(
                        entry = entry,
                        onClick = { onEditEntry(entry) }
                    )
                }
                item(key = "divider_$date") {
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StackLogCard(
    entry: SupplementEntry,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (entry.ketoEntryId != null) {
                        Badge(containerColor = KetoAccent.copy(alpha = 0.15f)) {
                            Text(
                                text = "K",
                                style = MaterialTheme.typography.labelSmall,
                                color = KetoAccent
                            )
                        }
                    }
                    Text(
                        text = entry.entryTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                }
            }

            val doseLabel = if (entry.unit != null) "${entry.dose} ${entry.unit}" else entry.dose
            if (doseLabel.isNotBlank()) {
                Text(
                    text = doseLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = KetoAccent,
                    fontWeight = FontWeight.Medium
                )
            }

            entry.notes?.takeIf { it.isNotBlank() }?.let { noteText ->
                Text(
                    text = noteText,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}

private fun formatStackDate(dateStr: String): String {
    return try {
        val date = java.time.LocalDate.parse(dateStr)
        val today = java.time.LocalDate.now()
        when {
            date == today -> "Today"
            date == today.minusDays(1) -> "Yesterday"
            else -> date.format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d, yyyy"))
        }
    } catch (_: Exception) {
        dateStr
    }
}

