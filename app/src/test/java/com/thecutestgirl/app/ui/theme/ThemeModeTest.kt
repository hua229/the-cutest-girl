package com.thecutestgirl.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 深色模式生效链路里唯一的判断逻辑：设置值 + 系统当前深浅色 → 到底用哪套色板。
 * 纯函数，不依赖 Android 环境。
 */
class ThemeModeTest {

    @Test
    fun `key round trips`() {
        for (mode in ThemeMode.entries) {
            assertEquals(mode, ThemeMode.fromKey(mode.key))
        }
    }

    @Test
    fun `stored keys are stable`() {
        // 这三个字符串已经写进用户手机的 settings.xml，改名等于把设置重置
        assertEquals("system", ThemeMode.System.key)
        assertEquals("light", ThemeMode.Light.key)
        assertEquals("dark", ThemeMode.Dark.key)
    }

    @Test
    fun `unknown missing and blank keys fall back to system`() {
        assertEquals(ThemeMode.System, ThemeMode.fromKey("nope"))
        assertEquals(ThemeMode.System, ThemeMode.fromKey(""))
        assertEquals(ThemeMode.System, ThemeMode.fromKey(null))
    }

    @Test
    fun `system mode follows the device`() {
        assertTrue(ThemeMode.System.resolve(systemIsDark = true))
        assertFalse(ThemeMode.System.resolve(systemIsDark = false))
    }

    @Test
    fun `manual modes ignore the device`() {
        assertFalse(ThemeMode.Light.resolve(systemIsDark = true))
        assertTrue(ThemeMode.Dark.resolve(systemIsDark = false))
    }
}
