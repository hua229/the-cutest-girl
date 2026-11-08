package com.thecutestgirl.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * v4.0 形状规范：
 * - 小圆角 8（按钮 / 输入 / 小控件）
 * - 卡片 16
 * - 容器 / 抽屉 24
 * 不允许 12/18/20/26 之类的一次性圆角。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)