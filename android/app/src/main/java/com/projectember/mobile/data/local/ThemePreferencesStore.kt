package com.projectember.mobile.data.local

import android.content.Context
import com.projectember.mobile.ui.theme.ThemeOption
import com.projectember.mobile.widget.TodayWidgetUpdater
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "theme_prefs"
private const val KEY_THEME = "selected_theme"

/**
 * Stores and retrieves the user's selected [ThemeOption] using SharedPreferences.
 * Exposes a [Flow] so any observing composable can react to theme changes immediately.
 */
class ThemePreferencesStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeFlow = MutableStateFlow(loadTheme())
    val themeFlow: Flow<ThemeOption> = _themeFlow.asStateFlow()

    fun getTheme(): ThemeOption = _themeFlow.value

    fun setTheme(theme: ThemeOption) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
        _themeFlow.value = theme
        TodayWidgetUpdater.updateWidgets(appContext)
    }

    private fun loadTheme(): ThemeOption =
        runCatching {
            ThemeOption.valueOf(prefs.getString(KEY_THEME, ThemeOption.EMBER_DARK.name)!!)
        }.getOrDefault(ThemeOption.EMBER_DARK)
}
