package com.projectember.mobile.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/**
 * Glance ActionCallback that triggers an immediate widget data refresh.
 * Called when the user taps the refresh button in the widget header.
 * Re-runs the full widget update flow: re-reads all Today data, rebuilds UI.
 */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        TodayWidgetUpdater.updateWidgets(context)
    }
}
