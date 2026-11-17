package com.thecutestgirl.app.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.platform.LocalDensity

private val LightColors = lightColorScheme(
    primary = Pink,
    onPrimary = OnPinkLight,
    primaryContainer = PinkSoft,
    onPrimaryContainer = Pink,
    secondary = TextSecondary,
    onSecondary = OnPinkLight,
    secondaryContainer = SegmentSlotLight,
    onSecondaryContainer = TextPrimary,
    tertiary = EmberWarm,
    onTertiary = OnPinkLight,
    tertiaryContainer = WarningDeep,
    onTertiaryContainer = EmberWarm,
    background = CanvasLight,
    onBackground = TextPrimary,
    surface = CardWhite,
    onSurface = TextPrimary,
    surfaceVariant = SegmentSlotLight,
    onSurfaceVariant = TextSecondary,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = CardWhite,
    surfaceContainerLow = CardWhite,
    surfaceContainer = CardWhite,
    surfaceContainerHigh = ElevatedLight,
    surfaceContainerHighest = ElevatedLight,
    outline = OutlineNeutral,
    outlineVariant = DividerSoft,
    error = DangerRed,
    onError = OnPinkLight,
    errorContainer = DangerDeep,
    onErrorContainer = DangerRed,
    scrim = ScrimLight
)

private val DarkColors = darkColorScheme(
    primary = PinkDark,
    onPrimary = PinkInkDark,
    primaryContainer = PinkContainerDark,
    onPrimaryContainer = PinkDark,
    secondary = TextSecondaryDark,
    onSecondary = CanvasDark,
    secondaryContainer = PanelRaisedDark,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = EmberDark,
    onTertiary = CanvasDark,
    tertiaryContainer = WarningDeepDark,
    onTertiaryContainer = EmberDark,
    background = CanvasDark,
    onBackground = TextPrimaryDark,
    surface = CardDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = PanelRaisedDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = CanvasDark,
    surfaceContainerLow = CardDark,
    surfaceContainer = CardDark,
    surfaceContainerHigh = ElevatedDark,
    surfaceContainerHighest = ElevatedDark,
    outline = OutlineNight,
    outlineVariant = DashedDark,
    error = DangerDark,
    onError = CanvasDark,
    errorContainer = DangerDeepDark,
    onErrorContainer = DangerDark,
    scrim = ScrimDark
)

private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = NoIndicationNode()

    override fun hashCode(): Int = 0

    override fun equals(other: Any?): Boolean = other === this

    private class NoIndicationNode : Modifier.Node(), DrawModifierNode {
        override fun ContentDrawScope.draw() {
            drawContent()
        }
    }
}

@Composable
fun TheCutestGirlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkAppPalette else LightAppPalette
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val degradation = if (LocalDensity.current.fontScale >= 2f) {
        DegradationLevel.L7
    } else {
        DegradationLevel.L0
    }
    val contentAlpha = remember { Animatable(1f) }
    var firstComposition by remember { mutableStateOf(true) }
    LaunchedEffect(darkTheme) {
        if (firstComposition) {
            firstComposition = false
        } else {
            contentAlpha.snapTo(0f)
            contentAlpha.animateTo(1f, tween(durationMillis = 220))
        }
    }
    CompositionLocalProvider(
        LocalAppPalette provides palette,
        LocalDegradationLevel provides degradation,
        LocalIndication provides NoIndication
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes
        ) {
            Box(modifier = Modifier.graphicsLayer { alpha = contentAlpha.value }) {
                content()
            }
        }
    }
}
