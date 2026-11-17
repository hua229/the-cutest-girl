package com.thecutestgirl.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thecutestgirl.app.ui.components.SegmentedChoices
import com.thecutestgirl.app.ui.theme.ThemeMode
import com.thecutestgirl.app.ui.theme.ThemeStore

/**
 * 外观：主题模式三选一（v4.3.0）。
 *
 * 默认「跟随系统」，另外两档是把这台手机钉死在浅色 / 深色。点一下即写即生效，
 * 没有保存按钮，也不需要重启页面——[ThemeStore] 一变，`MainActivity` 就换色板。
 *
 * 分段控件复用 [SegmentedChoices]：全 app 唯一的分段选择控件（饮食 / 点滴 / 外观页共用），
 * 这里不再另画一份。
 */
@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val mode by ThemeStore.mode.collectAsStateWithLifecycle()

    SettingsPage(
        title = "外观",
        icon = Icons.Outlined.DarkMode,
        onBack = onBack
    ) {
        SettingsCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                // 选项直接从枚举生成，顺序和 selectedIndex 不可能对不上
                SegmentedChoices(
                    options = ThemeMode.entries.map { it.label },
                    selectedIndex = ThemeMode.entries.indexOf(mode),
                    onSelect = { index -> ThemeStore.set(context, ThemeMode.entries[index]) }
                )
            }
        }
    }
}
