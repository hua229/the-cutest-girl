package com.thecutestgirl.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * v4.1：全站浅色（简洁白 + 粉红）。
 * 取消深色方案——品牌方向要求白底粉红，浅色下玻璃卡与阴影的可读性最好。
 */
private val LightColors = lightColorScheme(
    primary = Pink,
    onPrimary = Color.White,
    primaryContainer = PinkSoft,
    onPrimaryContainer = PinkInk,
    secondary = MutedRose,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3EAEE),
    onSecondaryContainer = Color(0xFF3A242B),
    tertiary = EmberWarm,
    onTertiary = Color.White,
    tertiaryContainer = WarningDeep,
    onTertiaryContainer = Color(0xFF3A2600),
    background = CanvasLight,
    onBackground = TextPrimary,
    surface = CardWhite,
    onSurface = TextPrimary,
    surfaceVariant = PanelRaised,
    onSurfaceVariant = TextSecondary,
    surfaceTint = Pink,
    outline = OutlineNeutral,
    error = DangerRed,
    onError = Color.White,
    errorContainer = DangerDeep,
    onErrorContainer = Color(0xFF410002),
    scrim = ScrimBlack
)

@Composable
fun TheCutestGirlTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    // 品牌方向：简洁白 + 粉红；不跟随系统深色
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}