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
import com.projectember.mobile.data.local.FoodWeightUnit
import com.projectember.mobile.data.local.KetoTargets
import com.projectember.mobile.data.local.KetoTargetsStore
import com.projectember.mobile.data.local.UnitPreferences
import com.projectember.mobile.data.local.UnitsPreferencesStore
import com.projectember.mobile.data.local.VolumeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class KetoTargetsViewModel(
    private val targetsStore: KetoTargetsStore,
    private val unitsPreferencesStore: UnitsPreferencesStore? = null
) : ViewModel() {
    // Snapshot unit preferences at creation time so load and save use the same units.
    private val prefs: UnitPreferences = unitsPreferencesStore?.getPreferences() ?: UnitPreferences()
    private val foodUnit: FoodWeightUnit get() = prefs.foodWeightUnit
    private val volUnit: VolumeUnit get() = prefs.volumeUnit

    /** Exposed to the Screen so labels update correctly. */
    val unitPreferences: StateFlow<UnitPreferences> = MutableStateFlow(prefs)

    private val current get() = targetsStore.targets.value

    var calories by mutableStateOf(current.caloriesKcal.toInt().toString())
    // Protein, fat, net carbs displayed in selected food-weight unit
    var protein by mutableStateOf(
        formatTarget(foodUnit.fromG(current.proteinG))
    )
    var fat by mutableStateOf(
        formatTarget(foodUnit.fromG(current.fatG))
    )
    var netCarbs by mutableStateOf(
        formatTarget(foodUnit.fromG(current.netCarbsG))
    )
    // Water displayed in selected volume unit
    var water by mutableStateOf(
        formatTarget(volUnit.fromMl(current.waterMl))
    )
    var sodium by mutableStateOf(current.sodiumMg.toInt().toString())
    var potassium by mutableStateOf(current.potassiumMg.toInt().toString())
    var magnesium by mutableStateOf(current.magnesiumMg.toInt().toString())

    fun save(): Boolean {
        val storedProtein = foodUnit.toG(protein.toDoubleOrNull() ?: return false).takeIf { it > 0 } ?: return false
        val storedFat = foodUnit.toG(fat.toDoubleOrNull() ?: return false).takeIf { it > 0 } ?: return false
        val storedNetCarbs = foodUnit.toG(netCarbs.toDoubleOrNull() ?: return false).takeIf { it > 0 } ?: return false
        val storedWater = volUnit.toMl(water.toDoubleOrNull() ?: return false).takeIf { it > 0 } ?: return false

        val targets = KetoTargets(
            caloriesKcal = calories.toDoubleOrNull()?.takeIf { it > 0 } ?: return false,
            proteinG = storedProtein,
            fatG = storedFat,
            netCarbsG = storedNetCarbs,
            waterMl = storedWater,
            sodiumMg = sodium.toDoubleOrNull()?.takeIf { it > 0 } ?: return false,
            potassiumMg = potassium.toDoubleOrNull()?.takeIf { it > 0 } ?: return false,
            magnesiumMg = magnesium.toDoubleOrNull()?.takeIf { it > 0 } ?: return false,
        )
        targetsStore.save(targets)
        return true
    }

    companion object {
        private fun formatTarget(d: Double): String =
            if (d == 0.0) "" else d.toBigDecimal().stripTrailingZeros().toPlainString()
    }
}

class KetoTargetsViewModelFactory(
    private val targetsStore: KetoTargetsStore,
    private val unitsPreferencesStore: UnitsPreferencesStore? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        KetoTargetsViewModel(targetsStore, unitsPreferencesStore) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KetoTargetsScreen(
    viewModel: KetoTargetsViewModel,
    onNavigateBack: () -> Unit
) {
    val unitPrefs by viewModel.unitPreferences.collectAsState()
    val foodSym = unitPrefs.foodWeightUnit.symbol
    val volSym  = unitPrefs.volumeUnit.symbol

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
            TargetField("Protein ($foodSym)", viewModel.protein) { viewModel.protein = it }
            TargetField("Fat ($foodSym)", viewModel.fat) { viewModel.fat = it }
            TargetField("Net Carbs ($foodSym)", viewModel.netCarbs) { viewModel.netCarbs = it }
            TargetField("Water ($volSym)", viewModel.water) { viewModel.water = it }
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
