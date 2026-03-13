package com.projectember.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.projectember.mobile.data.local.KetoTargets
import com.projectember.mobile.data.local.KetoTargetsStore

class KetoTargetsViewModel(private val targetsStore: KetoTargetsStore) : ViewModel() {
    private val current get() = targetsStore.targets.value

    var calories by mutableStateOf(current.caloriesKcal.toInt().toString())
    var protein by mutableStateOf(current.proteinG.toInt().toString())
    var fat by mutableStateOf(current.fatG.toInt().toString())
    var netCarbs by mutableStateOf(current.netCarbsG.toInt().toString())
    var water by mutableStateOf(current.waterMl.toInt().toString())
    var sodium by mutableStateOf(current.sodiumMg.toInt().toString())
    var potassium by mutableStateOf(current.potassiumMg.toInt().toString())
    var magnesium by mutableStateOf(current.magnesiumMg.toInt().toString())

    fun save(): Boolean {
        val targets = KetoTargets(
            caloriesKcal = calories.toDoubleOrNull()?.takeIf { it > 0 } ?: return false,
            proteinG = protein.toDoubleOrNull()?.takeIf { it > 0 } ?: return false,
            fatG = fat.toDoubleOrNull()?.takeIf { it > 0 } ?: return false,
            netCarbsG = netCarbs.toDoubleOrNull()?.takeIf { it > 0 } ?: return false,
            waterMl = water.toDoubleOrNull()?.takeIf { it > 0 } ?: return false,
            sodiumMg = sodium.toDoubleOrNull()?.takeIf { it > 0 } ?: return false,
            potassiumMg = potassium.toDoubleOrNull()?.takeIf { it > 0 } ?: return false,
            magnesiumMg = magnesium.toDoubleOrNull()?.takeIf { it > 0 } ?: return false,
        )
        targetsStore.save(targets)
        return true
    }
}

class KetoTargetsViewModelFactory(
    private val targetsStore: KetoTargetsStore
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        KetoTargetsViewModel(targetsStore) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KetoTargetsScreen(
    viewModel: KetoTargetsViewModel,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily Targets") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Set your daily targets to see progress indicators on the dashboard.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            TargetField("Calories (kcal)", viewModel.calories) { viewModel.calories = it }
            TargetField("Protein (g)", viewModel.protein) { viewModel.protein = it }
            TargetField("Fat (g)", viewModel.fat) { viewModel.fat = it }
            TargetField("Net Carbs (g)", viewModel.netCarbs) { viewModel.netCarbs = it }
            TargetField("Water (mL)", viewModel.water) { viewModel.water = it }
            TargetField("Sodium (mg)", viewModel.sodium) { viewModel.sodium = it }
            TargetField("Potassium (mg)", viewModel.potassium) { viewModel.potassium = it }
            TargetField("Magnesium (mg)", viewModel.magnesium) { viewModel.magnesium = it }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (viewModel.save()) onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Targets", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun TargetField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}
