package com.projectember.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val EmberDarkColorScheme = darkColorScheme(
    primary = KetoAccent,
    onPrimary = OnSurface,
    primaryContainer = Color(0xFF0D47A1),
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

@Composable
fun ProjectEmberTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EmberDarkColorScheme,
        typography = EmberTypography,
        content = content
    )
}
