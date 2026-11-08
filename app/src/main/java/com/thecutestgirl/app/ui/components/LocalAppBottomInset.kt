package com.thecutestgirl.app.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 底部玻璃导航栏占用的总高度（含系统导航条留白）。
 * 玻璃栏是悬浮在内容之上的，外层 Scaffold 不再消费底部 padding，
 * 各页面通过它给滚动内容预留出「能滚到玻璃栏底下但不被永久遮住」的底部空间。
 */
val LocalAppBottomInset = staticCompositionLocalOf { 0.dp }
