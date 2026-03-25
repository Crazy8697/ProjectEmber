package com.projectember.mobile.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
import androidx.glance.appwidget.LinearProgressIndicator
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
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.projectember.mobile.MainActivity
import com.projectember.mobile.data.local.DailyRhythmStore
import com.projectember.mobile.data.local.KetoTargetsStore
import com.projectember.mobile.data.local.MealTimingStore
import com.projectember.mobile.data.local.ThemePreferencesStore
import com.projectember.mobile.data.local.UnitsPreferencesStore
import com.projectember.mobile.data.local.db.AppDatabase
import com.projectember.mobile.data.repository.ExerciseRepository
import com.projectember.mobile.data.repository.KetoRepository
import com.projectember.mobile.data.repository.WeightRepository
import com.projectember.mobile.ui.theme.colorSchemeForTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Today summary widget that mirrors the main app's Today card.
 * Uses Glance (Jetpack Compose for Widgets) to create a home screen widget.
 */
class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = fetchWidgetData(context)
        val scheme = colorSchemeForTheme(data.themeOption)

        provideContent {
            GlanceTheme(colors = ColorProviders(light = scheme, dark = scheme)) {
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

                val themeOption = ThemePreferencesStore(context).getTheme()
                widgetRepo.getTodayData().copy(themeOption = themeOption)
            } catch (e: Exception) {
                // Return default data on error
                TodayWidgetData()
            }
        }
    }
}

@Composable
fun TodayWidgetContent(data: TodayWidgetData) {
    val calPct = if (data.caloriesTarget > 0)
        (data.displayCalories / data.caloriesTarget).toFloat().coerceIn(0f, 1f) else 0f
    val waterPct = if (data.waterTarget > 0)
        (data.waterMl / data.waterTarget).toFloat().coerceIn(0f, 1f) else 0f

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(12.dp)
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
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (data.pacingStatus != null) {
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        text = formatPacingStatus(data.pacingStatus),
                        style = TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Calories label + value
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (data.caloriesBurned > 0) "Cal (net)" else "Calories",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = if (data.caloriesTarget > 0)
                        "%.0f / %.0f".format(data.displayCalories, data.caloriesTarget)
                    else
                        "%.0f kcal".format(data.displayCalories),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            // Calories progress bar
            if (data.caloriesTarget > 0) {
                Spacer(modifier = GlanceModifier.height(4.dp))
                LinearProgressIndicator(
                    progress = calPct,
                    modifier = GlanceModifier.fillMaxWidth(),
                    color = GlanceTheme.colors.primary,
                    backgroundColor = GlanceTheme.colors.surfaceVariant
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Macro row: P · NC · F · Na:K
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MacroBox("P", data.proteinG, "g", GlanceModifier.defaultWeight())
                MacroBox("NC", data.netCarbsG, "g", GlanceModifier.defaultWeight())
                MacroBox("F", data.fatG, "g", GlanceModifier.defaultWeight())
                MacroBox("Na:K", data.naKRatio, "", GlanceModifier.defaultWeight(), decimalPlaces = 2)
            }

            // Water (wrapped in sub-Column to stay within Glance's 10-child Column limit)
            if (data.waterTarget > 0) {
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Water",
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        )
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        Text(
                            text = "%.0f / %.0f mL".format(data.waterMl, data.waterTarget),
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = waterPct,
                        modifier = GlanceModifier.fillMaxWidth(),
                        color = GlanceTheme.colors.primary,
                        backgroundColor = GlanceTheme.colors.surfaceVariant
                    )
                }
            }

            // Weight (wrapped in sub-Column to stay within Glance's 10-child Column limit)
            if (data.weightKg != null) {
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Weight",
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        )
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        Text(
                            text = if (data.weightDate != null)
                                "%.1f %s  ·  %s".format(data.displayWeight, data.weightUnit.symbol, data.weightDate)
                            else
                                "%.1f %s".format(data.displayWeight, data.weightUnit.symbol),
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
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
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp
            )
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        val formattedValue = if (decimalPlaces > 0) {
            "%.${decimalPlaces}f%s".format(value, unit)
        } else {
            "%.0f%s".format(value, unit)
        }
        Text(
            text = formattedValue,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
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
