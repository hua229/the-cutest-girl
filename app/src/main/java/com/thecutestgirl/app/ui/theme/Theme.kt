package com.thecutestgirl.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Pink,
    onPrimary = Color.White,
    primaryContainer = PinkSoft,
    onPrimaryContainer = PinkInk,
    secondary = MutedRose,
    onSecondary = Color.White,
    secondaryContainer = CobaltDeep,
    onSecondaryContainer = Color(0xFF433641),
    tertiary = EmberWarm,
    onTertiary = Color.White,
    tertiaryContainer = WarningDeep,
    onTertiaryContainer = Color(0xFF503A16),
    background = CanvasLight,
    onBackground = TextPrimary,
    surface = CardWhite,
    onSurface = TextPrimary,
    surfaceVariant = PanelRaised,
    onSurfaceVariant = TextSecondary,
    surfaceTint = Color.Transparent,
    outline = OutlineNeutral,
    outlineVariant = Color(0xFFE6E6EC),
    inverseSurface = Color(0xFF303036),
    inverseOnSurface = Color(0xFFF7F7FA),
    inversePrimary = PrimaryDark,
    surfaceBright = CardWhite,
    surfaceDim = Color(0xFFE9E9EF),
    surfaceContainerLowest = CardWhite,
    surfaceContainerLow = Color(0xFFFAFAFC),
    surfaceContainer = CardWhite,
    surfaceContainerHigh = PanelRaised,
    surfaceContainerHighest = Color(0xFFE9E9EF),
    error = DangerRed,
    onError = Color.White,
    errorContainer = DangerDeep,
    onErrorContainer = Color(0xFF71323B),
    scrim = Color.Black
)

private val DarkColors = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = Color(0xFF3E202E),
    primaryContainer = Color(0xFF49303D),
    onPrimaryContainer = Color(0xFFF6DBE5),
    secondary = Color(0xFFCDB8C3),
    onSecondary = Color(0xFF342B33),
    secondaryContainer = Color(0xFF393039),
    onSecondaryContainer = Color(0xFFEADDE5),
    tertiary = Color(0xFFE6C08B),
    onTertiary = Color(0xFF3C2C15),
    tertiaryContainer = Color(0xFF463725),
    onTertiaryContainer = Color(0xFFF4DBB8),
    background = CanvasDark,
    onBackground = Color(0xFFF1F1F5),
    surface = Color(0xFF1D1D22),
    onSurface = Color(0xFFF1F1F5),
    surfaceVariant = Color(0xFF2A2A31),
    onSurfaceVariant = Color(0xFFB4B4BF),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF55555F),
    outlineVariant = Color(0xFF36363F),
    inverseSurface = Color(0xFFE9E9EF),
    inverseOnSurface = Color(0xFF28282F),
    inversePrimary = Pink,
    surfaceBright = Color(0xFF38383F),
    surfaceDim = CanvasDark,
    surfaceContainerLowest = Color(0xFF0D0D10),
    surfaceContainerLow = Color(0xFF19191E),
    surfaceContainer = Color(0xFF1D1D22),
    surfaceContainerHigh = Color(0xFF26262C),
    surfaceContainerHighest = Color(0xFF303037),
    error = Color(0xFFF1A7AE),
    onError = Color(0xFF4D2028),
    errorContainer = Color(0xFF4A2B32),
    onErrorContainer = Color(0xFFF8DDE0),
    scrim = Color.Black
)

@Composable
fun TheCutestGirlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalAppPalette provides if (darkTheme) DarkAppPalette else LightAppPalette
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
