package com.projectember.mobile.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.projectember.mobile.MainActivity
import com.projectember.mobile.data.local.DailyRhythmStore
import com.projectember.mobile.data.local.KetoTargetsStore
import com.projectember.mobile.data.local.MealTimingStore
import com.projectember.mobile.data.local.UnitsPreferencesStore
import com.projectember.mobile.data.local.db.AppDatabase
import com.projectember.mobile.data.repository.ExerciseRepository
import com.projectember.mobile.data.repository.KetoRepository
import com.projectember.mobile.data.repository.WeightRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Today summary widget that mirrors the main app's Today card.
 * Uses Glance (Jetpack Compose for Widgets) to create a home screen widget.
 */
class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = fetchWidgetData(context)

        provideContent {
            GlanceTheme {
                TodayWidgetContent(data)
            }
        }
    }

    private suspend fun fetchWidgetData(context: Context): TodayWidgetData {
        return withContext(Dispatchers.IO) {
            try {
                val database = AppDatabase.getInstance(context)
                val ketoRepository = KetoRepository(database.ketoDao())
                val exerciseRepository = ExerciseRepository(database.exerciseEntryDao())
                val weightRepository = WeightRepository(database.weightDao())
                val targetsStore = KetoTargetsStore(context)
                val unitsPreferencesStore = UnitsPreferencesStore(context)
                val dailyRhythmStore = DailyRhythmStore(context)
                val mealTimingStore = MealTimingStore(context)

                val widgetRepo = TodayWidgetDataRepository(
                    context = context,
                    ketoRepository = ketoRepository,
                    exerciseRepository = exerciseRepository,
                    weightRepository = weightRepository,
                    targetsStore = targetsStore,
                    unitsPreferencesStore = unitsPreferencesStore,
                    dailyRhythmStore = dailyRhythmStore,
                    mealTimingStore = mealTimingStore
                )

                widgetRepo.getTodayData()
            } catch (e: Exception) {
                // Return default data on error
                TodayWidgetData()
            }
        }
    }
}

@Composable
fun TodayWidgetContent(data: TodayWidgetData) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = GlanceModifier.fillMaxWidth()
        ) {
            // Header with pacing badge
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today",
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (data.pacingStatus != null) {
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        text = formatPacingStatus(data.pacingStatus),
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(12.dp))

            // Calories
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (data.caloriesBurned > 0) "Calories (net)" else "Calories",
                    style = TextStyle(fontSize = 14.sp)
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = if (data.caloriesTarget > 0)
                        "%.0f / %.0f kcal".format(data.displayCalories, data.caloriesTarget)
                    else
                        "%.0f kcal".format(data.displayCalories),
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            if (data.caloriesBurned > 0) {
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = "food %.0f − %.0f burned".format(data.caloriesCurrent, data.caloriesBurned),
                    style = TextStyle(fontSize = 11.sp)
                )
            }

            Spacer(modifier = GlanceModifier.height(12.dp))

            // Macro grid (2x2)
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                // Row 1: Protein and Net Carbs
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MacroBox("P", data.proteinG, "g", GlanceModifier.defaultWeight())
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    MacroBox("NC", data.netCarbsG, "g", GlanceModifier.defaultWeight())
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                // Row 2: Fat and Na:K Ratio
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MacroBox("F", data.fatG, "g", GlanceModifier.defaultWeight())
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    MacroBox("Na:K", data.naKRatio, "", GlanceModifier.defaultWeight(), decimalPlaces = 2)
                }
            }

            Spacer(modifier = GlanceModifier.height(12.dp))

            // Water
            if (data.waterTarget > 0) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Water",
                        style = TextStyle(fontSize = 14.sp)
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = "%.0f / %.0f mL".format(data.waterMl, data.waterTarget),
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Spacer(modifier = GlanceModifier.height(8.dp))
            }

            // Weight
            if (data.weightKg != null) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Weight",
                        style = TextStyle(fontSize = 14.sp)
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = if (data.weightDate != null)
                            "%.1f %s  ·  %s".format(data.displayWeight, data.weightUnit.symbol, data.weightDate)
                        else
                            "%.1f %s".format(data.displayWeight, data.weightUnit.symbol),
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun MacroBox(
    label: String,
    value: Double,
    unit: String,
    modifier: GlanceModifier,
    decimalPlaces: Int = 0
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = TextStyle(fontSize = 11.sp)
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        val formattedValue = if (decimalPlaces > 0) {
            "%.${decimalPlaces}f%s".format(value, unit)
        } else {
            "%.0f%s".format(value, unit)
        }
        Text(
            text = formattedValue,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

fun formatPacingStatus(status: String): String {
    return when (status) {
        "ON_TRACK" -> "On Track"
        "BEHIND" -> "Behind"
        "AHEAD" -> "Ahead"
        "OVER_PACE" -> "Over"
        else -> ""
    }
}
