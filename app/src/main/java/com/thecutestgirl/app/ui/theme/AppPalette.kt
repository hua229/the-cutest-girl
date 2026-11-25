package com.thecutestgirl.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Material 语义色之外的浮层材质；内容表面仍使用 colorScheme.surface。 */
@Immutable
data class AppPalette(
    val hairline: Color,
    val glassSurface: Color,
    val glassHighlight: Color,
    val glassEdge: Color,
    val glassShadow: Color,
    val pillBody: Color,
    val pillHighlight: Color,
    val pillEdge: Color,
    val scrim: Color,
    val isDark: Boolean
)

val LightAppPalette = AppPalette(
    hairline = BorderHairline,
    glassSurface = Color(0xDDFFFFFF),
    glassHighlight = Color(0x66FFFFFF),
    glassEdge = Color(0x1A555561),
    glassShadow = Color(0x14202027),
    pillBody = Color(0x21AF4F70),
    pillHighlight = Color(0x66FFFFFF),
    pillEdge = Color(0x26FFFFFF),
    scrim = Color(0x66000000),
    isDark = false
)

val DarkAppPalette = AppPalette(
    hairline = Color(0x24FFFFFF),
    glassSurface = Color(0xE025252D),
    glassHighlight = Color(0x0FFFFFFF),
    glassEdge = Color(0x28FFFFFF),
    glassShadow = Color.Transparent,
    pillBody = Color(0x33E7A8BF),
    pillHighlight = Color(0x15FFFFFF),
    pillEdge = Color(0x26FFFFFF),
    scrim = Color(0x99000000),
    isDark = true
)

val LocalAppPalette = staticCompositionLocalOf { LightAppPalette }

val appPalette: AppPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalAppPalette.current
