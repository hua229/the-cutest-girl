package com.thecutestgirl.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppTypography = Typography(
    headlineMedium = Typography().headlineMedium.copy(
        fontSize = 30.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 38.sp
    ),
    titleLarge = Typography().titleLarge.copy(
        fontSize = 20.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 28.sp
    ),
    titleMedium = Typography().titleMedium.copy(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 26.sp
    ),
    titleSmall = Typography().titleSmall.copy(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 22.sp
    ),
    bodyLarge = Typography().bodyLarge.copy(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 26.sp
    ),
    bodyMedium = Typography().bodyMedium.copy(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 22.sp
    ),
    bodySmall = Typography().bodySmall.copy(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp
    ),
    labelLarge = Typography().labelLarge.copy(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 24.sp
    ),
    labelMedium = Typography().labelMedium.copy(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 22.sp
    ),
    labelSmall = Typography().labelSmall.copy(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp
    )
)

val DateNumberStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 16.sp,
    fontWeight = FontWeight.Normal,
    lineHeight = 24.sp
)

val BigNumeralStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 48.sp,
    fontWeight = FontWeight.Light,
    lineHeight = 56.sp
)

/**
 * 200% 字体下把最终可视数字限制在 72–88sp，避免二次放大后溢出。
 * 普通字号仍严格使用 48sp；数字与「第 / 天」的换行由页面布局负责。
 */
@Composable
fun responsiveBigNumeralStyle(): TextStyle {
    val fontScale = LocalDensity.current.fontScale
    if (fontScale < 2f) return BigNumeralStyle
    val renderedSize = (48f * fontScale).coerceIn(72f, 88f)
    val compensatedSize = renderedSize / fontScale
    return BigNumeralStyle.copy(
        fontSize = compensatedSize.sp,
        lineHeight = (compensatedSize * 56f / 48f).sp
    )
}

val EmptyTitleStyle = TextStyle(
    fontSize = 20.sp,
    fontWeight = FontWeight.Medium,
    lineHeight = 28.sp
)
