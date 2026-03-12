package com.projectember.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val EmberDarkColorScheme = darkColorScheme(
    primary = EmberOrange,
    onPrimary = OnSurface,
    primaryContainer = EmberOrangeDark,
    onPrimaryContainer = OnSurface,
    secondary = EmberOrangeLight,
    onSecondary = SurfaceDark,
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
