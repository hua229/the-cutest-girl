package com.thecutestgirl.app.ui.theme

import androidx.compose.ui.graphics.Color

// 温柔手账 · 奶油纸感。浅色是中性米纸，深色是无紫调的暖黑牛皮纸。
val CanvasLight = Color(0xFFF3EDE2)
val CardWhite = Color(0xFFFCF8F1)
val ElevatedLight = Color(0xFFFFFCF7)
val GlassTint = CardWhite // 兼容旧符号；纸面恒为不透明实色
val BorderHairline = Color(0xFFE2D6C2)
val DividerSoft = Color(0xFFD6C8B2)
val SegmentSlotLight = Color(0xFFEFE7D9)
val PhotoPaperLight = Color(0xFFFBF6EE)
val BarPaperLight = Color(0xFFFBF6EC)
val PaperInkLight = Color(0xFF8A7A62)
val TapeLight = Color(0x8CE7D9BE)
val PaperShadowLight = Color(0xFF6B5B45)

val TextPrimary = Color(0xFF2B2621)
val TextSecondary = Color(0xFF5C544A)
val TextMuted = Color(0xFF6E655A)

val Pink = Color(0xFFC92661)
val PinkInk = Pink // 粉容器上的短标签与选中文字
val PinkSoft = Color(0xFFF9E8EC)
val PinkMist = Color(0xFFFDF3F5)
val OnPinkLight = Color(0xFFFFF9F4)

val MutedRose = TextSecondary
val EmberWarm = Color(0xFF7C5220)
val SuccessGreen = Color(0xFF3B664A)
val WarningAmber = Color(0xFF7C5220)
val DangerRed = Color(0xFFA03C3A)
val SuccessDeep = Color(0xFFE8F0E9)
val WarningDeep = Color(0xFFF6EBD7)
val DangerDeep = Color(0xFFF7EAE7)

val OutlineNeutral = BorderHairline
val ScrimLight = Color(0x522B2621)
val ScrimBlack = ScrimLight // 兼容旧引用；深色请读 LocalAppPalette.scrim

// 旧组件符号继续保留，避免视觉迁移带动业务代码改名。
val Void = CanvasLight
val Panel = CardWhite
val PanelRaised = SegmentSlotLight
val OutlineDark = OutlineNeutral
val Plum = Pink
val PlumDeep = PinkSoft
val Cobalt = MutedRose
val CobaltDeep = PinkMist
val Ember = Pink
val EmberDeep = PinkMist
val PrimaryLight = Pink
val PrimaryDark = Pink

val CanvasDark = Color(0xFF171412)
val CardDark = Color(0xFF221E1A)
val ElevatedDark = Color(0xFF2C2722)
val PanelRaisedDark = Color(0xFF1C1815)
val DividerDark = Color(0xFF3B332C)
val DashedDark = Color(0xCC4A4038)
val PhotoPaperDark = CardDark
val BarPaperDark = Color(0xFF201B17)
val PaperInkDark = Color(0xFFC9B79B)
val TapeDark = Color(0x734A3A28)

val TextPrimaryDark = Color(0xFFF3ECE2)
val TextSecondaryDark = Color(0xFFC9BFB2)
val TextMutedDark = Color(0xFFADA293)

val PinkDark = Color(0xFFE8699C)
val PinkInkDark = Color(0xFF3A0F22)
val PinkContainerDark = Color(0xFF3A2029)
val PinkMistDark = Color(0xFF2A1C21)

val MutedRoseDark = TextSecondaryDark
val EmberDark = Color(0xFFD9AE72)
val SuccessDark = Color(0xFF8FB79A)
val WarningDark = Color(0xFFD9AE72)
val DangerDark = Color(0xFFE38C84)
val SuccessDeepDark = Color(0xFF253129)
val WarningDeepDark = Color(0xFF332A1C)
val DangerDeepDark = Color(0xFF33211F)

val OutlineNight = DividerDark
val ScrimDark = Color(0x7A000000)
