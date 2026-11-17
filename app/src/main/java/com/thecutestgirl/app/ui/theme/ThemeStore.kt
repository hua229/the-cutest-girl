package com.thecutestgirl.app.ui.theme

import android.content.Context
import com.thecutestgirl.app.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 外观主题模式（v4.3.0）。
 *
 * [resolve] 是「设置值 + 系统当前深浅色 → 用不用深色色板」的唯一判断处，
 * 所以单独做成纯函数并配 JVM 回归（ThemeModeTest），不跟 Compose 绑在一起。
 *
 * [key] 是落到 settings.xml 的字符串，改名字等于把用户已有的设置重置，
 * 只能新增不能改旧值。
 */
enum class ThemeMode(val key: String, val label: String) {
    System("system", "跟随系统"),
    Light("light", "浅色"),
    Dark("dark", "深色");

    /** 返回 `true` 表示用深色色板。手动两档忽略系统设置。 */
    fun resolve(systemIsDark: Boolean): Boolean = when (this) {
        System -> systemIsDark
        Light -> false
        Dark -> true
    }

    companion object {
        /** 读不到、值不认识（旧版本 / 手工改过 xml）一律回到「跟随系统」。 */
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: System
    }
}

/**
 * 主题模式的进程内单一来源。
 *
 * 设置页写入 → [StateFlow] 变 → `MainActivity` 的 `setContent` 重组换色板，
 * 即时生效且不重建 Activity（重建会打断正在填的表单和滚动位置）。
 * 持久化本身仍然在 SharedPreferences：进程死了重启，靠 [init] 再读回来。
 */
object ThemeStore {

    private val _mode = MutableStateFlow(ThemeMode.System)
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    /** App 启动时（`super.onCreate` 之后立刻）灌一次已存值，冷启动即恢复用户选择。 */
    fun init(context: Context) {
        _mode.value = ThemeMode.fromKey(SettingsStore.themeModeKey(context))
    }

    fun set(context: Context, mode: ThemeMode) {
        SettingsStore.saveThemeModeKey(context, mode.key)
        _mode.value = mode
    }
}
