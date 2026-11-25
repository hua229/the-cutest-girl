package com.thecutestgirl.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thecutestgirl.app.data.SettingsStore
import com.thecutestgirl.app.sync.SyncPhase
import com.thecutestgirl.app.sync.SyncStatusStore
import com.thecutestgirl.app.ui.components.ArtworkIcon
import com.thecutestgirl.app.ui.theme.ThemeStore

/** 三个主页共用的设置首页，各模块仍有独立返回栈。 */
@Composable
fun SettingsHomeScreen(onBack: () -> Unit, onOpenModule: (SettingsModule) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshKey by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    ThemeStore.initialize(context)
    val mode by ThemeStore.mode.collectAsStateWithLifecycle()
    val syncStatus by SyncStatusStore.status.collectAsStateWithLifecycle()
    val myName = remember(refreshKey) { SettingsStore.myName(context) }
    val syncConfigured = remember(refreshKey, syncStatus) { SettingsStore.isSyncConfigured(context) }
    val backgroundAllowed = remember(refreshKey) { context.isIgnoringBatteryOptimizations() }
    val syncLabel = when (syncStatus.phase) {
        SyncPhase.Running -> "同步中"
        SyncPhase.Partial -> "部分完成"
        SyncPhase.Failure -> "同步失败"
        SyncPhase.Success -> if (syncConfigured) "已同步" else null
        SyncPhase.Idle -> null
    }

    SettingsPage(title = "设置", icon = Icons.Outlined.Settings, onBack = onBack) {
        SettingsCard {
            SettingsHomeRow(SettingsModule.Profile, myName) { onOpenModule(SettingsModule.Profile) }
            SettingsDivider()
            SettingsHomeRow(SettingsModule.Appearance, mode.label) { onOpenModule(SettingsModule.Appearance) }
        }
        SettingsCard {
            SettingsHomeRow(SettingsModule.Sync, syncLabel) { onOpenModule(SettingsModule.Sync) }
            SettingsDivider()
            SettingsHomeRow(SettingsModule.Backup) { onOpenModule(SettingsModule.Backup) }
            SettingsDivider()
            SettingsHomeRow(SettingsModule.Battery, if (backgroundAllowed) "已允许" else null) { onOpenModule(SettingsModule.Battery) }
        }
    }
}

/** 值在大字体下移到下一行，完整保留模块名称。 */
@Composable
private fun SettingsHomeRow(module: SettingsModule, value: String? = null, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val largeFont = LocalDensity.current.fontScale > 1.3f
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(if (pressed) scheme.onSurface.copy(alpha = 0.04f) else Color.Transparent)
            .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            ArtworkIcon(module.icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        if (largeFont) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(module.title, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
                if (!value.isNullOrBlank()) {
                    Text(value, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        } else {
            Text(module.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
            if (!value.isNullOrBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(value, modifier = Modifier.widthIn(max = 112.dp), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = scheme.onSurfaceVariant.copy(alpha = 0.55f), modifier = Modifier.size(18.dp))
    }
}
