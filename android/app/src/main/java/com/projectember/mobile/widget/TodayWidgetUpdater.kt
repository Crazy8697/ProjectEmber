package com.projectember.mobile.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Helper class to manage widget updates.
 * Provides methods to trigger widget refreshes when app data changes.
 */
object TodayWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Update all Today widgets.
     * Call this when keto entries, exercise, weight, or targets change.
     */
    fun updateWidgets(context: Context) {
        scope.launch {
            try {
                TodayWidget().updateAll(context)
            } catch (e: Exception) {
                // Silently fail if widgets aren't added or update fails
            }
        }
    }

    /**
     * Check if any Today widgets are active.
     * Can be used to optimize whether updates are needed.
     */
    suspend fun hasActiveWidgets(context: Context): Boolean {
        return try {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(TodayWidget::class.java).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
}
