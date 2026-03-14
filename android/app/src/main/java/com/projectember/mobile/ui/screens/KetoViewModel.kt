package com.projectember.mobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.local.KetoTargets
import com.projectember.mobile.data.local.KetoTargetsStore
import com.projectember.mobile.data.local.entities.KetoEntry
import com.projectember.mobile.data.local.entities.WeightEntry
import com.projectember.mobile.data.local.entities.effectiveCalories
import com.projectember.mobile.data.repository.KetoRepository
import com.projectember.mobile.data.repository.WeightRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class DayTotals(
    val label: String,
    val date: String,
    val calories: Double,
    val netCarbsG: Double,
    val proteinG: Double = 0.0,
    val fatG: Double = 0.0,
    val waterMl: Double = 0.0,
    val sodiumMg: Double = 0.0,
    val potassiumMg: Double = 0.0,
    val magnesiumMg: Double = 0.0,
    /** Weight logged on this day (kg), or null if none. */
    val weightKg: Double? = null
)

class KetoViewModel(
    private val ketoRepository: KetoRepository,
    val targetsStore: KetoTargetsStore,
    private val weightRepository: WeightRepository
) : ViewModel() {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val today: String = LocalDate.now().format(dateFormatter)
    private val sevenDaysAgo: String = LocalDate.now().minusDays(6).format(dateFormatter)

    private val _selectedDate = MutableStateFlow(today)
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    fun setSelectedDate(date: String) {
        _selectedDate.value = date
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedDateEntries: StateFlow<List<KetoEntry>> = _selectedDate
        .flatMapLatest { date -> ketoRepository.getEntriesForDate(date) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val targets: StateFlow<KetoTargets> = targetsStore.targets

    /** Latest logged body weight entry from Room. */
    val lastWeightEntry: StateFlow<WeightEntry?> = weightRepository
        .getLatestEntry()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    fun logWeight(weightKg: Double) {
        val date = LocalDate.now().format(dateFormatter)
        viewModelScope.launch(Dispatchers.IO) {
            weightRepository.insert(WeightEntry(entryDate = date, weightKg = weightKg))
        }
    }

    // ── Trends controls ──────────────────────────────────────────────────────
    private val _trendsMetric = MutableStateFlow("calories")
    val trendsMetric: StateFlow<String> = _trendsMetric.asStateFlow()

    private val _trendsFromDate = MutableStateFlow(sevenDaysAgo)
    val trendsFromDate: StateFlow<String> = _trendsFromDate.asStateFlow()

    private val _trendsToDate = MutableStateFlow(today)
    val trendsToDate: StateFlow<String> = _trendsToDate.asStateFlow()

    private val _trendsMode = MutableStateFlow("daily") // "daily" or "rolling"
    val trendsMode: StateFlow<String> = _trendsMode.asStateFlow()

    private val _trendsRollingDays = MutableStateFlow(3)
    val trendsRollingDays: StateFlow<Int> = _trendsRollingDays.asStateFlow()

    fun setTrendsMetric(metric: String) { _trendsMetric.value = metric }
    fun setTrendsFromDate(date: String) { _trendsFromDate.value = date }
    fun setTrendsToDate(date: String) { _trendsToDate.value = date }
    fun setTrendsMode(mode: String) { _trendsMode.value = mode }
    fun setTrendsRollingDays(days: Int) { _trendsRollingDays.value = days }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val trendsData: StateFlow<List<DayTotals>> =
        combine(_trendsFromDate, _trendsToDate) { from, to -> from to to }
            .flatMapLatest { (fromDate, toDate) ->
                combine(
                    ketoRepository.getEntriesFromDate(fromDate),
                    weightRepository.getEntriesInRange(fromDate, toDate)
                ) { ketoEntries, weightEntries ->
                    val from = try {
                        LocalDate.parse(fromDate, dateFormatter)
                    } catch (_: Exception) {
                        LocalDate.now().minusDays(6)
                    }
                    val to = try {
                        LocalDate.parse(toDate, dateFormatter)
                    } catch (_: Exception) {
                        LocalDate.now()
                    }
                    val numDays = ((to.toEpochDay() - from.toEpochDay() + 1)
                        .toInt()).coerceIn(1, 90)
                    val dayShortFmt = DateTimeFormatter.ofPattern("EEE")
                    // Build a map from date → latest weight for that day (by highest id).
                    val weightByDate: Map<String, Double> = weightEntries
                        .groupBy { it.entryDate }
                        .mapValues { (_, entries) ->
                            // Use maxByOrNull so the most recently inserted entry per day wins.
                            entries.maxByOrNull { it.id }!!.weightKg
                        }
                    (0 until numDays).map { offset ->
                        val date = from.plusDays(offset.toLong())
                        val dateStr = date.format(dateFormatter)
                        val dayEntries = ketoEntries.filter { it.entryDate == dateStr }
                        DayTotals(
                            label = date.format(dayShortFmt),
                            date = dateStr,
                            calories = dayEntries.sumOf { it.effectiveCalories() }
                                .coerceAtLeast(0.0),
                            netCarbsG = dayEntries.sumOf { it.netCarbsG },
                            proteinG = dayEntries.sumOf { it.proteinG },
                            fatG = dayEntries.sumOf { it.fatG },
                            waterMl = dayEntries.sumOf { it.waterMl },
                            sodiumMg = dayEntries.sumOf { it.sodiumMg },
                            potassiumMg = dayEntries.sumOf { it.potassiumMg },
                            magnesiumMg = dayEntries.sumOf { it.magnesiumMg },
                            weightKg = weightByDate[dateStr]
                        )
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    val weeklyData: StateFlow<List<DayTotals>> = ketoRepository
        .getEntriesFromDate(sevenDaysAgo)
        .map { entries ->
            val dayShortFmt = DateTimeFormatter.ofPattern("EEE")
            (0..6).map { offset ->
                val date = LocalDate.now().minusDays(6 - offset.toLong())
                val dateStr = date.format(dateFormatter)
                val dayEntries = entries.filter { it.entryDate == dateStr }
                val cals = dayEntries.sumOf { it.effectiveCalories() }
                DayTotals(
                    label = date.format(dayShortFmt),
                    date = dateStr,
                    calories = cals.coerceAtLeast(0.0),
                    netCarbsG = dayEntries.sumOf { it.netCarbsG },
                    proteinG = dayEntries.sumOf { it.proteinG },
                    fatG = dayEntries.sumOf { it.fatG },
                    waterMl = dayEntries.sumOf { it.waterMl },
                    sodiumMg = dayEntries.sumOf { it.sodiumMg },
                    potassiumMg = dayEntries.sumOf { it.potassiumMg },
                    magnesiumMg = dayEntries.sumOf { it.magnesiumMg }
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )
}

class KetoViewModelFactory(
    private val ketoRepository: KetoRepository,
    private val targetsStore: KetoTargetsStore,
    private val weightRepository: WeightRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        KetoViewModel(ketoRepository, targetsStore, weightRepository) as T
}
