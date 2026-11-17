package com.thecutestgirl.app.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * 后台不被冻结：引导用户把 App 加进电池优化白名单。
 *
 * 用户去系统页开完再返回时本页不会重建，所以用 ON_RESUME 重新读一次状态，
 * 否则按钮会一直显示「去设置」。
 */
@Composable
fun BatterySettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(context.isIgnoringBatteryOptimizations())
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoringBatteryOptimizations = context.isIgnoringBatteryOptimizations()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsPage(
        title = "后台不被冻结",
        icon = Icons.Outlined.BatterySaver,
        onBack = onBack
    ) {
        SettingsCard {
            SettingsActionRow(
                text = if (isIgnoringBatteryOptimizations) "已允许后台运行" else "允许后台运行",
                icon = true,
                iconVector = Icons.Outlined.BatterySaver,
                enabled = !isIgnoringBatteryOptimizations,
                container = false,
                onClick = { context.openBatteryOptimizationSettings() }
            )
        }
    }
}
