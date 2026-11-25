package com.thecutestgirl.app.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val storageValue: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStored(value: String?): ThemeMode = entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

/** 本机外观偏好，不参与记录同步与 ZIP 身份备份。 */
object ThemeStore {
    private const val FILE = "settings"
    private const val KEY = "theme_mode"
    private var preferences: SharedPreferences? = null
    private val currentMode = MutableStateFlow(ThemeMode.SYSTEM)
    val mode: StateFlow<ThemeMode> = currentMode.asStateFlow()

    @Synchronized
    fun initialize(context: Context) {
        if (preferences != null) return
        val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        preferences = prefs
        currentMode.value = ThemeMode.fromStored(prefs.getString(KEY, null))
    }

    fun setMode(context: Context, mode: ThemeMode) {
        initialize(context)
        preferences!!.edit().putString(KEY, mode.storageValue).apply()
        currentMode.value = mode
    }
}
