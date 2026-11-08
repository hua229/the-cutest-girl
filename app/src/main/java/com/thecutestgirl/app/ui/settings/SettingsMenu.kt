package com.thecutestgirl.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thecutestgirl.app.data.SettingsStore
import com.thecutestgirl.app.sync.SyncPhase
import com.thecutestgirl.app.sync.SyncStatusStore
import com.thecutestgirl.app.ui.components.glassPanel
import com.thecutestgirl.app.ui.theme.BorderHairline
import com.thecutestgirl.app.ui.theme.ScrimBlack

/**
 * 设置页的一级分组。侧边菜单和设置页共用这份枚举，
 * 保证「菜单里有什么」和「页面上能滚到哪」永远一致。
 *
 * [key] 写进导航参数，改名前先考虑旧路由兼容；[title] 是用户看到的业务语言。
 */
enum class SettingsSection(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    Profile(
        key = "profile",
        title = "我的信息",
        subtitle = "昵称会同步给对方",
        icon = Icons.Outlined.PersonOutline
    ),
    Sync(
        key = "sync",
        title = "云同步",
        subtitle = "服务器、账号与立即同步",
        icon = Icons.Outlined.CloudSync
    ),
    Backup(
        key = "backup",
        title = "数据备份",
        subtitle = "导出数据库和全部照片",
        icon = Icons.Outlined.Save
    ),
    Battery(
        key = "battery",
        title = "后台不被冻结",
        subtitle = "加入电池优化白名单",
        icon = Icons.Outlined.BatterySaver
    );

    companion object {
        /** 导航参数 → 分组；空值或旧参数返回 null（停在设置页顶部）。 */
        fun fromKey(key: String?): SettingsSection? =
            values().firstOrNull { it.key == key }
    }
}

/**
 * 全局侧边设置菜单（v4.3）。
 *
 * 为什么是「设置分组」而不是「穿搭 / 饮食 / 点滴」：
 * 底部导航已经承担了模块切换，侧边栏只做设置，职责单一、不会和底栏抢入口。
 * 宽度取屏宽 78%（上限 340dp），满足「不超过屏宽 80%」的约束；
 * 遮罩点击、右滑、系统返回键关闭都由 Material3 的 ModalNavigationDrawer 负责。
 */
@Composable
fun SettingsMenuDrawer(
    onClose: () -> Unit,
    onOpenSection: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
    refreshKey: Int = 0
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val syncStatus by SyncStatusStore.status.collectAsStateWithLifecycle()

    // 昵称 / 是否已配置在打开抽屉时重读（refreshKey 每次打开都会变），
    // 否则在设置页改完昵称回来，菜单里还是旧值。
    val myName = remember(refreshKey) { SettingsStore.myName(context) }
    val syncConfigured = remember(refreshKey) { SettingsStore.isSyncConfigured(context) }
    val syncLabel = when {
        syncStatus.phase == SyncPhase.Running -> "同步中"
        syncStatus.phase == SyncPhase.Failure -> "同步失败"
        syncConfigured -> "已开启"
        else -> "未开启"
    }

    BoxWithConstraints(modifier = modifier.fillMaxHeight()) {
        val drawerWidth = minOf(maxWidth * 0.78f, 340.dp)
        Column(
            modifier = Modifier
                .width(drawerWidth)
                .fillMaxHeight()
                // 抽屉必须完全不透明：半透明会让底层列表文字透上来（FIX-01 的老问题）
                .glassPanel(
                    shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                    surfaceAlpha = 1f
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "设置",
                        style = MaterialTheme.typography.titleLarge,
                        color = scheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "同步、备份与后台都在这里",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "关闭设置菜单",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 账户：所有「我是谁 / 怎么连」的设置
            MenuGroupLabel("账户")
            SettingsMenuRow(
                section = SettingsSection.Profile,
                trailing = myName.ifBlank { "未设置" },
                trailingActive = myName.isNotBlank(),
                onClick = { onOpenSection(SettingsSection.Profile) }
            )
            SettingsMenuRow(
                section = SettingsSection.Sync,
                trailing = syncLabel,
                trailingActive = syncConfigured || syncStatus.phase == SyncPhase.Running,
                onClick = { onOpenSection(SettingsSection.Sync) }
            )

            MenuDivider()

            // 数据与稳定：一次性操作 / 系统权限
            MenuGroupLabel("数据与后台")
            SettingsMenuRow(
                section = SettingsSection.Backup,
                onClick = { onOpenSection(SettingsSection.Backup) }
            )
            SettingsMenuRow(
                section = SettingsSection.Battery,
                onClick = { onOpenSection(SettingsSection.Battery) }
            )

            Spacer(Modifier.weight(1f))

            Text(
                "设置都在这里，随时可以回来调整。",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 菜单分组标题：12sp 辅助色，只负责分区，不抢行项目的注意力。 */
@Composable
private fun MenuGroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
    )
}

/** 分组之间的发丝线：左右各缩进 8dp，和行项目对齐。 */
@Composable
private fun MenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .height(1.dp)
            .background(BorderHairline)
    )
}

/**
 * 菜单行：36dp 图标底 + 主标题 + 说明 + 可选当前值 + 右侧箭头。
 * 最小高度 56dp（超过 48dp 热区要求），整行可点、无系统 ripple。
 */
@Composable
private fun SettingsMenuRow(
    section: SettingsSection,
    onClick: () -> Unit,
    trailing: String? = null,
    trailingActive: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(scheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = section.icon,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                section.title,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                section.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!trailing.isNullOrBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                trailing,
                style = MaterialTheme.typography.labelSmall,
                color = if (trailingActive) {
                    scheme.primary
                } else {
                    scheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = scheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
    }
}

/** 遮罩颜色：与新增记录抽屉统一 40% 黑。 */
internal val SettingsScrim = ScrimBlack