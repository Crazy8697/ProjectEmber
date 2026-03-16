package com.projectember.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectember.mobile.data.local.HealthMetric
import com.projectember.mobile.sync.HealthConnectManager
import com.projectember.mobile.sync.HealthMetricsSnapshot
import com.projectember.mobile.sync.SleepSessionSummary
import com.projectember.mobile.ui.theme.OnSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(
    viewModel: HealthViewModel,
    onNavigateBack: () -> Unit,
) {
    val enabledMetrics by viewModel.enabledMetrics.collectAsState()
    val healthData by viewModel.healthData.collectAsState()

    // Refresh whenever the screen is entered
    LaunchedEffect(Unit) { viewModel.refreshHealthScreen() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Health") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Determine which health metrics the user has enabled
            val healthMetricKeys = listOf(
                HealthMetric.HEART_RATE,
                HealthMetric.RESTING_HEART_RATE,
                HealthMetric.SLEEP,
                HealthMetric.BLOOD_PRESSURE,
                HealthMetric.BLOOD_GLUCOSE,
                HealthMetric.BODY_TEMPERATURE,
                HealthMetric.OXYGEN_SATURATION,
                HealthMetric.RESPIRATORY_RATE,
            )
            val anyEnabled = healthMetricKeys.any { enabledMetrics[it] != false }

            if (!anyEnabled) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No health metrics enabled",
                            style = MaterialTheme.typography.titleMedium,
                            color = OnSurfaceVariant
                        )
                        Text(
                            text = "Enable metrics in Settings → Health Metrics",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariant
                        )
                    }
                }
            } else {
                when (val state = healthData) {
                    is HealthDataState.NotInstalled -> {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Health Connect not available",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = OnSurfaceVariant
                                )
                                Text(
                                    text = "Health Connect is required to display health metrics.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnSurfaceVariant
                                )
                            }
                        }
                    }
                    is HealthDataState.Loading -> {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text(
                                    text = "Loading health data…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnSurfaceVariant
                                )
                            }
                        }
                    }
                    is HealthDataState.Error -> {
                        item {
                            Text(
                                text = "Could not load health data: ${state.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                    is HealthDataState.Ready -> {
                        val snapshot = state.snapshot
                        val granted = snapshot.grantedPermissions

                        if (enabledMetrics[HealthMetric.HEART_RATE] != false) {
                            item {
                                HealthMetricCard(
                                    label = "HEART RATE",
                                    value = when {
                                        HealthConnectManager.PERM_HEART_RATE !in granted ->
                                            null to "Permission needed"
                                        snapshot.heartRateBpm != null ->
                                            "${snapshot.heartRateBpm} bpm" to snapshot.heartRateTimestamp
                                        else -> null to "No data yet"
                                    },
                                    permissionNeeded = HealthConnectManager.PERM_HEART_RATE !in granted
                                )
                            }
                        }

                        if (enabledMetrics[HealthMetric.RESTING_HEART_RATE] != false) {
                            item {
                                HealthMetricCard(
                                    label = "RESTING HEART RATE",
                                    value = when {
                                        HealthConnectManager.PERM_RESTING_HEART_RATE !in granted ->
                                            null to "Permission needed"
                                        snapshot.restingHeartRateBpm != null ->
                                            "${snapshot.restingHeartRateBpm} bpm" to snapshot.restingHeartRateTimestamp
                                        else -> null to "No data yet"
                                    },
                                    permissionNeeded = HealthConnectManager.PERM_RESTING_HEART_RATE !in granted
                                )
                            }
                        }

                        if (enabledMetrics[HealthMetric.SLEEP] != false) {
                            item {
                                HealthSleepCard(
                                    permissionNeeded = HealthConnectManager.PERM_SLEEP !in granted,
                                    session = snapshot.latestSleepSession
                                )
                            }
                        }

                        if (enabledMetrics[HealthMetric.BLOOD_PRESSURE] != false) {
                            item {
                                HealthMetricCard(
                                    label = "BLOOD PRESSURE",
                                    value = when {
                                        HealthConnectManager.PERM_BLOOD_PRESSURE !in granted ->
                                            null to "Permission needed"
                                        snapshot.bloodPressureSystolicMmHg != null ->
                                            "%.0f / %.0f mmHg".format(
                                                snapshot.bloodPressureSystolicMmHg,
                                                snapshot.bloodPressureDiastolicMmHg ?: 0.0
                                            ) to snapshot.bloodPressureTimestamp
                                        else -> null to "No data yet"
                                    },
                                    permissionNeeded = HealthConnectManager.PERM_BLOOD_PRESSURE !in granted
                                )
                            }
                        }

                        if (enabledMetrics[HealthMetric.BLOOD_GLUCOSE] != false) {
                            item {
                                HealthMetricCard(
                                    label = "BLOOD GLUCOSE",
                                    value = when {
                                        HealthConnectManager.PERM_BLOOD_GLUCOSE !in granted ->
                                            null to "Permission needed"
                                        snapshot.bloodGlucoseMmol != null ->
                                            "%.1f mmol/L".format(snapshot.bloodGlucoseMmol) to snapshot.bloodGlucoseTimestamp
                                        else -> null to "No data yet"
                                    },
                                    permissionNeeded = HealthConnectManager.PERM_BLOOD_GLUCOSE !in granted
                                )
                            }
                        }

                        if (enabledMetrics[HealthMetric.BODY_TEMPERATURE] != false) {
                            item {
                                HealthMetricCard(
                                    label = "BODY TEMPERATURE",
                                    value = when {
                                        HealthConnectManager.PERM_BODY_TEMPERATURE !in granted ->
                                            null to "Permission needed"
                                        snapshot.bodyTemperatureCelsius != null ->
                                            "%.1f °C".format(snapshot.bodyTemperatureCelsius) to snapshot.bodyTemperatureTimestamp
                                        else -> null to "No data yet"
                                    },
                                    permissionNeeded = HealthConnectManager.PERM_BODY_TEMPERATURE !in granted
                                )
                            }
                        }

                        if (enabledMetrics[HealthMetric.OXYGEN_SATURATION] != false) {
                            item {
                                HealthMetricCard(
                                    label = "OXYGEN SATURATION",
                                    value = when {
                                        HealthConnectManager.PERM_OXYGEN_SATURATION !in granted ->
                                            null to "Permission needed"
                                        snapshot.oxygenSaturationPct != null ->
                                            "%.0f%%".format(snapshot.oxygenSaturationPct) to snapshot.oxygenSaturationTimestamp
                                        else -> null to "No data yet"
                                    },
                                    permissionNeeded = HealthConnectManager.PERM_OXYGEN_SATURATION !in granted
                                )
                            }
                        }

                        if (enabledMetrics[HealthMetric.RESPIRATORY_RATE] != false) {
                            item {
                                HealthMetricCard(
                                    label = "RESPIRATORY RATE",
                                    value = when {
                                        HealthConnectManager.PERM_RESPIRATORY_RATE !in granted ->
                                            null to "Permission needed"
                                        snapshot.respiratoryRateBreath != null ->
                                            "%.0f breaths/min".format(snapshot.respiratoryRateBreath) to snapshot.respiratoryRateTimestamp
                                        else -> null to "No data yet"
                                    },
                                    permissionNeeded = HealthConnectManager.PERM_RESPIRATORY_RATE !in granted
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

/**
 * Card for a latest-value style health metric.
 *
 * @param label  All-caps label.
 * @param value  Pair of (data string, empty/error string). Only one non-null at a time.
 * @param permissionNeeded  When true the card uses an error-tinted subtitle.
 */
@Composable
private fun HealthMetricCard(
    label: String,
    value: Pair<String?, String?>,
    permissionNeeded: Boolean = false,
) {
    val (dataValue, subtitleText) = value
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (permissionNeeded)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (permissionNeeded)
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (dataValue != null) {
                Text(
                    text = dataValue,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (subtitleText != null) {
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (permissionNeeded)
                        MaterialTheme.colorScheme.error
                    else
                        OnSurfaceVariant
                )
            }
        }
    }
}

/** Card specifically for the sleep session metric. */
@Composable
private fun HealthSleepCard(
    permissionNeeded: Boolean,
    session: SleepSessionSummary?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (permissionNeeded)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (permissionNeeded)
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "SLEEP",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            when {
                permissionNeeded -> {
                    Text(
                        text = "Permission needed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                session != null -> {
                    Text(
                        text = session.durationDisplay,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = session.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
                else -> {
                    Text(
                        text = "No data yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant
                    )
                }
            }
        }
    }
}
