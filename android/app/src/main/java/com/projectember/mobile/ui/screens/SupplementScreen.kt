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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectember.mobile.data.local.entities.StackDefinition
import com.projectember.mobile.ui.theme.KetoAccent
import com.projectember.mobile.ui.theme.OnSurfaceVariant
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StacksScreen(
    viewModel: StacksViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAddDefinition: () -> Unit,
    onNavigateToEditDefinition: (Int) -> Unit
) {
    val definitions by viewModel.definitions.collectAsState()
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
                onClick = onNavigateToAddDefinition,
                containerColor = KetoAccent
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add stack",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { paddingValues ->
        if (definitions.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
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
                    text = "Tap + to save your first reusable Keto stack.",
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(definitions, key = { it.id }) { definition ->
                    StackDefinitionCard(
                        definition = definition,
                        onEdit = { onNavigateToEditDefinition(definition.id) },
                        onLog = {
                            viewModel.quickLog(definition) { ketoLinked ->
                                coroutineScope.launch {
                                    val msg = if (ketoLinked) {
                                        "\"${definition.name}\" logged to Keto"
                                    } else {
                                        "\"${definition.name}\" logged"
                                    }
                                    snackbarHostState.showSnackbar(msg)
                                }
                            }
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
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
    val isLoggable = definition.hasNutritionData()

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
                verticalAlignment = Alignment.Top
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
                    if (isLoggable) {
                        Text(
                            text = "Logs to Keto",
                            style = MaterialTheme.typography.labelSmall,
                            color = KetoAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            text = "Add nutrition values to enable Keto logging",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                        )
                    }
                }
                if (isLoggable) {
                    Badge(containerColor = KetoAccent.copy(alpha = 0.20f)) {
                        Text(
                            text = "Keto",
                            style = MaterialTheme.typography.labelSmall,
                            color = KetoAccent,
                            fontWeight = FontWeight.Bold
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

            if (isLoggable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp)
                    ) {
                        Text("Edit")
                    }
                    Button(
                        onClick = onLog,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KetoAccent)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 4.dp)
                        )
                        Text("Log to Keto")
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Edit")
                }
            }
        }
    }
}
