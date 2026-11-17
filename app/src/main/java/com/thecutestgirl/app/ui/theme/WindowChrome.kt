package com.thecutestgirl.app.ui.theme

import android.content.res.Configuration
import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * 主题解析结果往「Activity 侧」传递要用到的两个小工具（v4.3.0）。
 *
 * Compose 认 [androidx.compose.ui.graphics.Color] 和 `isSystemInDarkTheme()`，
 * 窗口底色与系统栏认 `@ColorInt Int` 和 `Configuration.uiMode`。
 * 手动指定深色时这两套判断**不一样**——资源里的 `values-night` 只跟随机身设置，
 * 不认我们的 `settings.xml`。所以这里把判断收在一处，避免 Activity 里散落
 * `(uiMode and mask)` 和「到底是哪套底色」。
 */

/** 机身当前是否深色。与 Compose 的 `isSystemInDarkTheme()` 同一个判断，给首帧（Compose 之前）用。 */
fun Configuration.isSystemNight(): Boolean =
    uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

/** 窗口底色：Compose 第一帧画满之前由它兜着，避免手动深色时先闪一下浅底。 */
@ColorInt
fun windowBackgroundArgb(darkTheme: Boolean): Int =
    (if (darkTheme) CanvasDark else CanvasLight).toArgb()
