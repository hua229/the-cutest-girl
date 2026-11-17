package com.thecutestgirl.app.ui.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thecutestgirl.app.data.SettingsStore
import com.thecutestgirl.app.sync.SyncPhase
import com.thecutestgirl.app.sync.SyncStatusStore
import com.thecutestgirl.app.ui.components.PaperIconButton
import com.thecutestgirl.app.ui.components.glassPanel
import com.thecutestgirl.app.ui.components.pressTint
import com.thecutestgirl.app.ui.theme.LocalAppPalette
import com.thecutestgirl.app.ui.theme.ScrimBlack
import com.thecutestgirl.app.ui.theme.ThemeMode
import com.thecutestgirl.app.ui.theme.ThemeStore

/**
 * 全局侧边设置菜单（v4.2.2）。
 *
 * 菜单只有一个：五个设置模块的入口，不混入「穿搭 / 饮食 / 点滴」模块导航。
 * 每个模块跳到自己的独立页面（[SettingsModule.route]），不再共用一张设置页。
 * 宽度固定 280dp；屏宽 ≤320dp 时取屏宽 84%，给遮罩保留至少 48dp 热区。
 * 遮罩点击、右滑、系统返回键关闭都由 Material3 的 ModalNavigationDrawer 负责。
 *
 * [expanded] 用抽屉的目标状态驱动内容淡入 + 轻微右移，
 * 与抽屉自身的滑入动画同向叠加，避免内容「先出现再被推着走」的断层感。
 */
@Composable
fun SettingsMenuDrawer(
    expanded: Boolean,
    onClose: () -> Unit,
    onOpenModule: (SettingsModule) -> Unit,
    modifier: Modifier = Modifier,
    refreshKey: Int = 0
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val syncStatus by SyncStatusStore.status.collectAsStateWithLifecycle()
    val themeMode by ThemeStore.mode.collectAsStateWithLifecycle()

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

    // 240ms 与 Material3 抽屉的滑入时长接近；只动 alpha 和位移，不改布局，滑动全程不卡顿。
    val reveal by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "settingsMenuReveal"
    )

    BoxWithConstraints(modifier = modifier.fillMaxHeight()) {
        val drawerWidth = if (maxWidth <= 320.dp) maxWidth * 0.84f else 280.dp
        Box(
            modifier = Modifier
                .width(drawerWidth)
                .fillMaxHeight()
                // 抽屉必须完全不透明：半透明会让底层列表文字透上来（FIX-01 的老问题）
                .glassPanel(
                    shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
                    elevated = true
                )
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .graphicsLayer {
                        alpha = reveal
                        translationX = (1f - reveal) * 18.dp.toPx()
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "设置",
                        style = MaterialTheme.typography.titleLarge,
                        color = scheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    PaperIconButton(onClick = onClose) {
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
                SettingsMenuRow(
                    module = SettingsModule.Profile,
                    trailing = myName.ifBlank { "未设置" },
                    trailingActive = myName.isNotBlank(),
                    onClick = { onOpenModule(SettingsModule.Profile) }
                )
                SettingsMenuRow(
                    module = SettingsModule.Sync,
                    trailing = syncLabel,
                    trailingActive = syncConfigured || syncStatus.phase == SyncPhase.Running,
                    onClick = { onOpenModule(SettingsModule.Sync) }
                )

                MenuDivider()

                // 数据与稳定：一次性操作 / 系统权限
                SettingsMenuRow(
                    module = SettingsModule.Backup,
                    onClick = { onOpenModule(SettingsModule.Backup) }
                )
                SettingsMenuRow(
                    module = SettingsModule.Battery,
                    onClick = { onOpenModule(SettingsModule.Battery) }
                )

                MenuDivider()

                // 外观：本设备偏好，行尾直接显示当前档位；
                // 手动固定（非「跟随系统」）时才染成主色，让「改过」看得出来
                SettingsMenuRow(
                    module = SettingsModule.Appearance,
                    trailing = themeMode.label,
                    trailingActive = themeMode != ThemeMode.System,
                    onClick = { onOpenModule(SettingsModule.Appearance) }
                )
            }
        }
    }
}

/** 分组之间的手账虚线：左右各缩进 8dp，与行项目对齐。 */
@Composable
private fun MenuDivider() {
    val dashedStroke = LocalAppPalette.current.dashedStroke
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .height(1.dp)
            .drawBehind {
                drawLine(
                    color = dashedStroke,
                    start = Offset(0f, 0.5f),
                    end = Offset(size.width, 0.5f),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f
                    )
                )
            }
    )
}

/**
 * 菜单行：36dp 图标底 + 标题 + 可选当前值 + 右侧箭头。
 * 最小高度 56dp（超过 48dp 热区要求），整行可点、无系统 ripple。
 */
@Composable
private fun SettingsMenuRow(
    module: SettingsModule,
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
            .pressTint(interactionSource, cornerRadius = 12.dp)
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
                .background(scheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = module.icon,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            module.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
            tint = LocalAppPalette.current.mutedText,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** 遮罩颜色：与新增记录抽屉统一 40% 黑。 */
internal val SettingsScrim = ScrimBlack
