package com.thecutestgirl.app.ui.theme

import androidx.compose.ui.graphics.Color

// 中性的页面与卡面，玫粉只用于主操作和选中状态。
val CanvasLight = Color(0xFFF5F5F7)
val CanvasDark = Color(0xFF111114)
val CardWhite = Color(0xFFFFFFFF)
val GlassTint = Color(0xE6FFFFFF)
val BorderHairline = Color(0x1423232B)
val DividerSoft = BorderHairline

val TextPrimary = Color(0xFF202027)
val TextSecondary = Color(0xFF686871)
val TextMuted = Color(0xFF777780)

val Pink = Color(0xFFAF4F70)
val PinkInk = Color(0xFF74314A)
val PinkSoft = Color(0xFFF4E6EC)
val PinkMist = Color(0xFFFBF5F8)
val MutedRose = Color(0xFF806573)
val EmberWarm = Color(0xFF95651F)

val SuccessGreen = Color(0xFF36765A)
val WarningAmber = EmberWarm
val DangerRed = Color(0xFFBC3E49)
val SuccessDeep = Color(0xFFE8F3ED)
val WarningDeep = Color(0xFFF7EFDF)
val DangerDeep = Color(0xFFFAE9EB)
val OutlineNeutral = Color(0xFFDADAE1)
val ScrimBlack = Color(0x66000000)

// 保留既有常量名供旧调用过渡；新组件从 colorScheme / appPalette 读取动态颜色。
val Void = CanvasLight
val Panel = CardWhite
val PanelRaised = Color(0xFFF0F0F4)
val OutlineDark = OutlineNeutral
val Plum = Pink
val PlumDeep = PinkSoft
val Cobalt = MutedRose
val CobaltDeep = Color(0xFFEEE9EE)
val Ember = Pink
val EmberDeep = PinkMist
val PrimaryLight = Pink
val PrimaryDark = Color(0xFFE7A8BF)
