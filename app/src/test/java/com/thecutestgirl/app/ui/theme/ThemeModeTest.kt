package com.thecutestgirl.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test
    fun missingOrInvalidPreferenceFollowsSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored(""))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("unknown"))
    }

    @Test
    fun existingStoredChoicesArePreserved() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("system"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStored("light"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStored("dark"))
    }

    @Test
    fun systemModeTracksTheCurrentSystemTheme() {
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
    }

    @Test
    fun manualLightIgnoresSystemDark() {
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
    }

    @Test
    fun returningFromManualDarkToSystemUsesTheSystemValue() {
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
        assertTrue(ThemeMode.DARK.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
    }
}
