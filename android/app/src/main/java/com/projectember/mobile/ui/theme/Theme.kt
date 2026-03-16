package com.projectember.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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

// ── Slate ─────────────────────────────────────────────────────────────────────

private val SlateColorScheme = darkColorScheme(
    primary = SlateAccent,
    onPrimary = OnSurface,
    primaryContainer = SlateSurfaceMid,
    onPrimaryContainer = OnSurface,
    secondary = SlateSecondary,
    onSecondary = OnSurface,
    secondaryContainer = Color(0xFF1E3A52),
    onSecondaryContainer = OnSurface,
    tertiary = SlateAccentLight,
    onTertiary = SlateSurfaceDark,
    tertiaryContainer = Color(0xFF253547),
    onTertiaryContainer = SlateAccentLight,
    background = SlateSurfaceDark,
    onBackground = OnSurface,
    surface = SlateSurfaceMid,
    onSurface = OnSurface,
    surfaceVariant = SlateSurfaceLight,
    onSurfaceVariant = OnSurfaceVariant,
    error = ErrorRed,
    onError = OnSurface
)

// ── Crimson ───────────────────────────────────────────────────────────────────

private val CrimsonColorScheme = darkColorScheme(
    primary = CrimsonAccent,
    onPrimary = OnSurface,
    primaryContainer = CrimsonSurfaceMid,
    onPrimaryContainer = OnSurface,
    secondary = CrimsonSecondary,
    onSecondary = OnSurface,
    secondaryContainer = Color(0xFF450A0A),
    onSecondaryContainer = OnSurface,
    tertiary = CrimsonAccentLight,
    onTertiary = CrimsonSurfaceDark,
    tertiaryContainer = Color(0xFF3B0808),
    onTertiaryContainer = CrimsonAccentLight,
    background = CrimsonSurfaceDark,
    onBackground = OnSurface,
    surface = CrimsonSurfaceMid,
    onSurface = OnSurface,
    surfaceVariant = CrimsonSurfaceLight,
    onSurfaceVariant = OnSurfaceVariant,
    error = ErrorRed,
    onError = OnSurface
)

// ── Dusk ──────────────────────────────────────────────────────────────────────

private val DuskColorScheme = darkColorScheme(
    primary = DuskAccent,
    onPrimary = OnSurface,
    primaryContainer = DuskSurfaceMid,
    onPrimaryContainer = OnSurface,
    secondary = DuskSecondary,
    onSecondary = OnSurface,
    secondaryContainer = Color(0xFF500724),
    onSecondaryContainer = OnSurface,
    tertiary = DuskAccentLight,
    onTertiary = DuskSurfaceDark,
    tertiaryContainer = Color(0xFF2E1065),
    onTertiaryContainer = DuskAccentLight,
    background = DuskSurfaceDark,
    onBackground = OnSurface,
    surface = DuskSurfaceMid,
    onSurface = OnSurface,
    surfaceVariant = DuskSurfaceLight,
    onSurfaceVariant = OnSurfaceVariant,
    error = ErrorRed,
    onError = OnSurface
)

// ── Arctic (light) ────────────────────────────────────────────────────────────

private val ArcticColorScheme = lightColorScheme(
    primary = ArcticPrimary,
    onPrimary = ArcticOnPrimary,
    primaryContainer = ArcticSurfaceVariant,
    onPrimaryContainer = ArcticOnBackground,
    secondary = ArcticSecondary,
    onSecondary = ArcticOnPrimary,
    secondaryContainer = Color(0xFFBADAF0),
    onSecondaryContainer = ArcticOnBackground,
    tertiary = ArcticPrimary,
    onTertiary = ArcticOnPrimary,
    tertiaryContainer = ArcticSurfaceVariant,
    onTertiaryContainer = ArcticOnBackground,
    background = ArcticBackground,
    onBackground = ArcticOnBackground,
    surface = ArcticSurface,
    onSurface = ArcticOnSurface,
    surfaceVariant = ArcticSurfaceVariant,
    onSurfaceVariant = ArcticOnSurfaceVariant,
    error = Color(0xFFDC2626),
    onError = ArcticOnPrimary
)

// ── Abyss ─────────────────────────────────────────────────────────────────────

private val AbyssColorScheme = darkColorScheme(
    primary = AbyssAccent,
    onPrimary = AbyssSurfaceDark,
    primaryContainer = AbyssSurfaceMid,
    onPrimaryContainer = OnSurface,
    secondary = AbyssSecondary,
    onSecondary = OnSurface,
    secondaryContainer = Color(0xFF0C3844),
    onSecondaryContainer = OnSurface,
    tertiary = AbyssAccentLight,
    onTertiary = AbyssSurfaceDark,
    tertiaryContainer = Color(0xFF0E3A4A),
    onTertiaryContainer = AbyssAccentLight,
    background = AbyssSurfaceDark,
    onBackground = OnSurface,
    surface = AbyssSurfaceMid,
    onSurface = OnSurface,
    surfaceVariant = AbyssSurfaceLight,
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
        ThemeOption.SLATE      -> SlateColorScheme
        ThemeOption.CRIMSON    -> CrimsonColorScheme
        ThemeOption.DUSK       -> DuskColorScheme
        ThemeOption.ARCTIC     -> ArcticColorScheme
        ThemeOption.ABYSS      -> AbyssColorScheme
    }
    CompositionLocalProvider(LocalThemeOption provides themeOption) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = EmberTypography,
            content = content
        )
    }
}
