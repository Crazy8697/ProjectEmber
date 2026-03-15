package com.projectember.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Ember Dark (default) ──────────────────────────────────────────────────────

private val EmberDarkColorScheme = darkColorScheme(
    primary = KetoAccent,
    onPrimary = OnSurface,
    primaryContainer = SurfaceMid,
    onPrimaryContainer = OnSurface,
    secondary = EmberOrange,
    onSecondary = OnSurface,
    secondaryContainer = EmberOrangeDark,
    onSecondaryContainer = OnSurface,
    tertiary = SuccessGreen,
    onTertiary = SurfaceDark,
    tertiaryContainer = Color(0xFF004D1F),
    onTertiaryContainer = SuccessGreen,
    background = SurfaceDark,
    onBackground = OnSurface,
    surface = SurfaceMid,
    onSurface = OnSurface,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = OnSurfaceVariant,
    error = ErrorRed,
    onError = OnSurface
)

// ── Midnight Blue ─────────────────────────────────────────────────────────────

private val MidnightColorScheme = darkColorScheme(
    primary = MidnightAccent,
    onPrimary = OnSurface,
    primaryContainer = MidnightSurfaceMid,
    onPrimaryContainer = OnSurface,
    secondary = MidnightSecondary,
    onSecondary = OnSurface,
    secondaryContainer = Color(0xFF3B0764),
    onSecondaryContainer = OnSurface,
    tertiary = MidnightAccentLight,
    onTertiary = MidnightSurfaceDark,
    tertiaryContainer = Color(0xFF1E3A5F),
    onTertiaryContainer = MidnightAccentLight,
    background = MidnightSurfaceDark,
    onBackground = OnSurface,
    surface = MidnightSurfaceMid,
    onSurface = OnSurface,
    surfaceVariant = MidnightSurfaceLight,
    onSurfaceVariant = OnSurfaceVariant,
    error = ErrorRed,
    onError = OnSurface
)

// ── Forest Dark ───────────────────────────────────────────────────────────────

private val ForestColorScheme = darkColorScheme(
    primary = ForestAccent,
    onPrimary = ForestSurfaceDark,
    primaryContainer = ForestSurfaceMid,
    onPrimaryContainer = OnSurface,
    secondary = ForestSecondary,
    onSecondary = OnSurface,
    secondaryContainer = Color(0xFF14532D),
    onSecondaryContainer = OnSurface,
    tertiary = ForestAccentLight,
    onTertiary = ForestSurfaceDark,
    tertiaryContainer = Color(0xFF166534),
    onTertiaryContainer = ForestAccentLight,
    background = ForestSurfaceDark,
    onBackground = OnSurface,
    surface = ForestSurfaceMid,
    onSurface = OnSurface,
    surfaceVariant = ForestSurfaceLight,
    onSurfaceVariant = OnSurfaceVariant,
    error = ErrorRed,
    onError = OnSurface
)

// ── CompositionLocal for active theme option ──────────────────────────────────

val LocalThemeOption = compositionLocalOf { ThemeOption.EMBER_DARK }

// ── Root theme composable ─────────────────────────────────────────────────────

@Composable
fun ProjectEmberTheme(
    themeOption: ThemeOption = ThemeOption.EMBER_DARK,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeOption) {
        ThemeOption.EMBER_DARK -> EmberDarkColorScheme
        ThemeOption.MIDNIGHT   -> MidnightColorScheme
        ThemeOption.FOREST     -> ForestColorScheme
    }
    CompositionLocalProvider(LocalThemeOption provides themeOption) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = EmberTypography,
            content = content
        )
    }
}
