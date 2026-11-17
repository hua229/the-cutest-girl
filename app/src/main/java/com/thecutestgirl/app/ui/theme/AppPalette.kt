package com.thecutestgirl.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/** M3 没有槽位、但必须随主题一起切换的纸张材质与中性表面。 */
@Immutable
data class AppPalette(
    val hairline: Color,
    val cardGlow: Color,
    val dashedFill: Color,
    val dashedStroke: Color,
    val buttonFill: Color,
    val chromeTop: Color,
    val pillHighlight: Color,
    val pillEdge: Color,
    val hazeTint: Color,
    val hazeGloss: Color,
    val mutedText: Color,
    val accentText: Color,
    val success: Color,
    val successBackground: Color,
    val scrim: Color,
    val shadowAmbientAlpha: Float,
    val shadowSpotAlpha: Float,
    val elevatedSurface: Color,
    val paperInk: Color,
    val paperShadow: Color,
    val disabledContainer: Color,
    val segmentSlot: Color,
    val photoPlaceholder: Color,
    val tape: Color,
    val paperDotAlpha: Float,
    val paperLineAlpha: Float
)

internal val LightAppPalette = AppPalette(
    hairline = BorderHairline,
    cardGlow = ElevatedLight,
    dashedFill = CardWhite,
    dashedStroke = DividerSoft.copy(alpha = 0.70f),
    buttonFill = PinkSoft,
    chromeTop = ElevatedLight,
    pillHighlight = ElevatedLight,
    pillEdge = BorderHairline,
    hazeTint = BarPaperLight,
    hazeGloss = ElevatedLight,
    mutedText = TextMuted,
    accentText = Pink,
    success = SuccessGreen,
    successBackground = SuccessDeep,
    scrim = ScrimLight,
    shadowAmbientAlpha = 0.08f,
    shadowSpotAlpha = 0.08f,
    elevatedSurface = ElevatedLight,
    paperInk = PaperInkLight,
    paperShadow = PaperShadowLight,
    disabledContainer = BorderHairline,
    segmentSlot = SegmentSlotLight,
    photoPlaceholder = PhotoPaperLight,
    tape = TapeLight,
    paperDotAlpha = 0.025f,
    paperLineAlpha = 0.018f
)

internal val DarkAppPalette = AppPalette(
    hairline = DividerDark,
    cardGlow = Color(0xFF4A4038).copy(alpha = 0.45f),
    dashedFill = CardDark,
    dashedStroke = DashedDark,
    buttonFill = PinkContainerDark,
    chromeTop = ElevatedDark,
    pillHighlight = ElevatedDark,
    pillEdge = DividerDark,
    hazeTint = BarPaperDark,
    hazeGloss = ElevatedDark,
    mutedText = TextMutedDark,
    accentText = PinkDark,
    success = SuccessDark,
    successBackground = SuccessDeepDark,
    scrim = ScrimDark,
    shadowAmbientAlpha = 0f,
    shadowSpotAlpha = 0f,
    elevatedSurface = ElevatedDark,
    paperInk = PaperInkDark,
    paperShadow = Color.Transparent,
    disabledContainer = DividerDark,
    segmentSlot = PanelRaisedDark,
    photoPlaceholder = PhotoPaperDark,
    tape = TapeDark,
    paperDotAlpha = 0.03f,
    paperLineAlpha = 0.02f
)

val LocalAppPalette = compositionLocalOf { LightAppPalette }

/** 单一降级出口；数值越大，关闭的装饰越多，功能与可用性永不降级。 */
enum class DegradationLevel(val level: Int) {
    L0(0), L1(1), L2(2), L3(3), L4(4), L5(5), L6(6), L7(7), L8(8)
}

val LocalDegradationLevel = compositionLocalOf { DegradationLevel.L0 }
