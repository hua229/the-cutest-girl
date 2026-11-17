package com.thecutestgirl.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.thecutestgirl.app.ui.components.AppTopBar
import com.thecutestgirl.app.ui.components.focusRing
import com.thecutestgirl.app.ui.components.pressTint
import com.thecutestgirl.app.ui.components.solidCard
import com.thecutestgirl.app.ui.theme.LocalAppPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 四个独立设置页共用的页面骨架。
 *
 * 顶栏标题就是模块名，页面内只放这一个模块的内容；
 * 内容随键盘避让、到底部时给导航栏留白。
 */
@Composable
internal fun SettingsPage(
    title: String,
    icon: ImageVector,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(title = title, icon = icon, onBack = onBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 设置页里的一张实体卡片；组内用发丝线切行。 */
@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .solidCard(cornerRadius = 12.dp)
            .padding(vertical = 4.dp),
        content = content
    )
}

/** 组内发丝线：左缩进与输入文字对齐，右不缩进（裁到卡片圆角内）。 */
@Composable
internal fun SettingsDivider() {
    val dashedStroke = LocalAppPalette.current.dashedStroke
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
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
 * 无框输入行：字段标签 + 正文值。
 *
 * 用 [BasicTextField] 而不是 TextField：要的就是「没有框」——
 * 光标用主色 1dp，占位符用 40% 的辅助色，焦点态靠标签变色提示，不引入任何描边。
 */
@Composable
internal fun SettingsField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val palette = LocalAppPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .focusRing(focused, 10.dp)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder != null) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        // 占位符也是文字，4.5:1 的线一样要过：alpha 压过的 onSurfaceVariant 只有 ≈2.6:1
                        color = palette.mutedText
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    visualTransformation = visualTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    interactionSource = interactionSource,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (trailing != null) trailing()
        }
    }
}

/** 次要操作行：左图标 + 文案，整行可点，禁用时降到 38% 透明度。 */
@Composable
internal fun SettingsActionRow(
    text: String,
    icon: Boolean,
    enabled: Boolean,
    container: Boolean = true,
    iconVector: ImageVector = Icons.Outlined.CloudSync,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val palette = LocalAppPalette.current
    val tint = if (enabled) scheme.onSurfaceVariant else scheme.onSurfaceVariant
    // 全站统一不用系统 ripple；禁用态用实色底 + 实色文字，不再压透明度
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (container) Modifier.solidCard(cornerRadius = 12.dp) else Modifier)
            .then(
                if (!enabled && container) {
                    Modifier.background(palette.disabledContainer)
                } else {
                    Modifier
                }
            )
            .pressTint(interactionSource, cornerRadius = if (container) 12.dp else 0.dp, enabled = enabled)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}

/** 同步时间的相对文案：刚刚 / N 分钟前 / N 小时前 / 具体日期。 */
internal fun relativeSyncTime(at: Long): String {
    if (at <= 0L) return "还没同步过"
    val diff = System.currentTimeMillis() - at
    return when {
        diff < 60_000L -> "刚刚同步"
        diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
        diff < 86_400_000L -> "${diff / 3_600_000L} 小时前"
        else -> SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(at))
    }
}

/** 是否已在电池优化白名单（PowerManager 的 API 从 23 起才有，minSdk 26 所以直接用）。 */
internal fun Context.isIgnoringBatteryOptimizations(): Boolean =
    runCatching {
        (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(packageName)
    }.getOrDefault(false)

/**
 * 打开「忽略电池优化」设置页。
 *
 * 两个必须绕开的坑（v2.9.0 的真实 bug，v2.9.1 修）：
 *
 * 1. **Manifest 必须声明 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`**。AOSP 的
 *    `RequestIgnoreBatteryOptimizations.onCreate()` 会检查调用方有没有这个权限，
 *    没有就 `finish()`。此时 `startActivity` 正常返回、`runCatching` 判为成功，
 *    但用户看到的是「点了没反应」——所以不能只靠 `isSuccess` 判断。
 * 2. **国产 ROM 常常不提供这个 action 的 Activity**。用 `resolveActivity` 先查，
 *    查不到就走下一级；而且「有 resolver、对方却立刻 finish」的情况也要兜住，
 *    所以按「白名单列表 → 应用详情」逐级回退，每级都真的发出去再让用户看到结果。
 *
 * 顺序：
 * - 已在白名单里：什么都不做（按钮本来就是禁用态，这里只是防御）。
 * - `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS + package:` → 直接弹确认框，最省事。
 * - `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` → 系统白名单列表，用户自己找本 App。
 * - `ACTION_APPLICATION_DETAILS_SETTINGS` → 应用详情，最后兜底。
 * - 全部打不开时给个 Toast，不再静默失败。
 */
internal fun Context.openBatteryOptimizationSettings() {
    if (isIgnoringBatteryOptimizations()) return

    val packageUri = Uri.parse("package:$packageName")

    /** 只有真的存在能处理它的 Activity 才启动，否则返回 false 让调用方走下一级。 */
    fun launch(intent: Intent): Boolean {
        val resolved = runCatching {
            intent.resolveActivity(packageManager) != null
        }.getOrDefault(false)
        if (!resolved) return false
        return runCatching {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    // 第一级：直接弹「允许忽略电池优化？」确认框。
    // 部分 ROM 有 resolver 但会立刻 finish（权限没声明、或厂商改了实现），
    // 这时没法同步感知，所以不把它当唯一路径——下面两级始终可用，用户点第二次即可。
    val requested = launch(
        @Suppress("BatteryLife")
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
    )

    if (!requested) {
        // 第二级：系统的「忽略电池优化」白名单列表，用户自己找本 App。
        val openedList = launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        if (!openedList) {
            // 第三级：应用详情页，兜底一定能打开。
            val openedDetails = launch(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
            )
            if (!openedDetails) {
                Toast.makeText(this, "这台手机没有电池优化设置入口", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
