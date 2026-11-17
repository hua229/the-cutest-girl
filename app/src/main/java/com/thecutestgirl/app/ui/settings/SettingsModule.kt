package com.thecutestgirl.app.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Save
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 侧边菜单里的五个设置模块。
 *
 * 每个模块都有独立的导航路由和独立页面，不再共用一张设置页：
 * 菜单只负责「去哪」，页面只负责「这一个模块做什么」。
 *
 * [key] 拼进路由 `settings/<key>`；[title] 是菜单与页面顶栏共用的业务语言。
 */
enum class SettingsModule(
    val key: String,
    val title: String,
    val icon: ImageVector
) {
    Profile(
        key = "profile",
        title = "我的信息",
        icon = Icons.Outlined.PersonOutline
    ),
    Sync(
        key = "sync",
        title = "云同步",
        icon = Icons.Outlined.CloudSync
    ),
    Backup(
        key = "backup",
        title = "数据备份",
        icon = Icons.Outlined.Save
    ),
    Battery(
        key = "battery",
        title = "后台不被冻结",
        icon = Icons.Outlined.BatterySaver
    ),
    Appearance(
        key = "appearance",
        title = "外观",
        icon = Icons.Outlined.DarkMode
    );

    /** 独立页面路由：settings/profile、settings/sync…… */
    val route: String get() = "settings/$key"
}
