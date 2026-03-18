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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectember.mobile.data.local.entities.SupplementEntry
import com.projectember.mobile.ui.theme.KetoAccent
import com.projectember.mobile.ui.theme.OnSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplementScreen(
    viewModel: SupplementViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAddEntry: () -> Unit,
    onNavigateToEditEntry: (Int) -> Unit
) {
    val entries by viewModel.entries.collectAsState()

    // Group entries by date (most recent date first)
    val grouped = entries.groupBy { it.entryDate }
    val sortedDates = grouped.keys.sortedDescending()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Supplements",
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddEntry,
                containerColor = KetoAccent
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add supplement",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { paddingValues ->
        if (entries.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No supplements logged yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap + to log your first supplement.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                sortedDates.forEach { date ->
                    item(key = "header_$date") {
                        Text(
                            text = formatSupplementDate(date),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = OnSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                    val dayEntries = grouped[date] ?: emptyList()
                    items(dayEntries, key = { it.id }) { entry ->
                        SupplementEntryCard(
                            entry = entry,
                            onClick = { onNavigateToEditEntry(entry.id) }
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SupplementEntryCard(
    entry: SupplementEntry,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                Text(
                    text = entry.entryTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }

            val doseLabel = if (entry.unit != null) {
                "${entry.dose} ${entry.unit}"
            } else {
                entry.dose
            }
            Text(
                text = doseLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = KetoAccent,
                fontWeight = FontWeight.Medium
            )

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

private fun formatSupplementDate(dateStr: String): String {
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
