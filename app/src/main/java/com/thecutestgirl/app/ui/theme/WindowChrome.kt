package com.thecutestgirl.app.ui.theme

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb

/** 系统 uiMode 保持原值；手动档位只用于 Compose，不覆盖资源配置。 */
fun Configuration.isSystemNight(): Boolean =
    uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

fun windowBackgroundArgb(darkTheme: Boolean): Int =
    (if (darkTheme) CanvasDark else CanvasLight).toArgb()

fun ComponentActivity.applyWindowChrome(darkTheme: Boolean) {
    val style = if (darkTheme) {
        SystemBarStyle.dark(Color.TRANSPARENT)
    } else {
        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    }
    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    window.setBackgroundDrawable(ColorDrawable(windowBackgroundArgb(darkTheme)))
}
