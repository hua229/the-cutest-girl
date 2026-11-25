package com.thecutestgirl.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun appText(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = 0.sp
)

/** 跟随设备默认字体与系统字号缩放，靠尺寸、字重和留白区分层次。 */
val AppTypography = Typography(
    headlineLarge = appText(32, 40, FontWeight.SemiBold),
    headlineMedium = appText(32, 40, FontWeight.SemiBold),
    headlineSmall = appText(24, 32, FontWeight.SemiBold),
    titleLarge = appText(22, 30, FontWeight.SemiBold),
    titleMedium = appText(17, 25, FontWeight.Medium),
    titleSmall = appText(15, 22, FontWeight.Medium),
    bodyLarge = appText(17, 26),
    bodyMedium = appText(14, 21),
    bodySmall = appText(12, 18),
    labelLarge = appText(16, 23, FontWeight.Medium),
    labelMedium = appText(14, 20),
    labelSmall = appText(12, 17)
)

/** 保持系统字形，只将数字设为等宽，避免日期切换时跳动。 */
val DateNumberStyle = appText(17, 25, FontWeight.Medium).copy(fontFeatureSettings = "tnum")

val BigNumeralStyle = appText(44, 52, FontWeight.Medium).copy(
    fontFeatureSettings = "tnum",
    letterSpacing = (-1).sp
)

val EmptyTitleStyle = appText(20, 28, FontWeight.Medium)
