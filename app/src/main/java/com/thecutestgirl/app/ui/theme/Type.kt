package com.thecutestgirl.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * v4.0 排版规范（系统无衬线，不引入字体文件）：
 * - 页面标题 headlineMedium 30sp / Medium（字重 500，不用 700+）
 * - 卡片标题 titleMedium 16sp / Medium
 * - 正文 bodyLarge 16sp / Regular，行高 24
 * - 辅助 labelMedium 14sp / Regular
 * - 角标 labelSmall 12sp / Regular
 * 数字：FontFamily.Monospace（等宽），核心大数字用 Light(300)。
 */
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
        lineHeight = 24.sp
    ),
    titleSmall = Typography().titleSmall.copy(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp
    ),
    bodyLarge = Typography().bodyLarge.copy(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 24.sp
    ),
    bodyMedium = Typography().bodyMedium.copy(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp
    ),
    bodySmall = Typography().bodySmall.copy(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 18.sp
    ),
    labelLarge = Typography().labelLarge.copy(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp
    ),
    labelMedium = Typography().labelMedium.copy(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp
    ),
    labelSmall = Typography().labelSmall.copy(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp
    )
)

/** 日期栏 / 日期条数字：等宽（Monospace），以免数字跳动。 */
val DateNumberStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 16.sp,
    fontWeight = FontWeight.Normal,
    lineHeight = 24.sp
)

/** 纪念日大数字：等宽 + Light(300)，比正文大很多但不用粗体压人。 */
val BigNumeralStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 44.sp,
    fontWeight = FontWeight.Light,
    lineHeight = 48.sp,
    letterSpacing = (-1).sp
)

/** 空态主文案：比正文重、比标题轻。 */
val EmptyTitleStyle = TextStyle(
    fontSize = 20.sp,
    fontWeight = FontWeight.Medium,
    lineHeight = 28.sp
)