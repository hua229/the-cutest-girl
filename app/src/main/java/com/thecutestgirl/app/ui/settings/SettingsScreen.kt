package com.thecutestgirl.app.ui.settings

import android.Manifest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.thecutestgirl.app.data.SettingsStore
import com.thecutestgirl.app.data.BackupExporter
import com.thecutestgirl.app.sync.SyncEngine
import com.thecutestgirl.app.sync.SyncManager
import com.thecutestgirl.app.sync.SyncPhase
import com.thecutestgirl.app.sync.SyncStatusStore
import com.thecutestgirl.app.ui.components.AppTopBar
import com.thecutestgirl.app.ui.components.PrimaryButton
import com.thecutestgirl.app.ui.components.solidCard
import com.thecutestgirl.app.ui.components.SyncStatusBar
import com.thecutestgirl.app.ui.components.SyncStatusKind
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 同步时间的相对文案：刚刚 / N 分钟前 / N 小时前 / 具体日期 */
private fun relativeSyncTime(at: Long): String {
    if (at <= 0L) return "还没同步过"
    val diff = System.currentTimeMillis() - at
    return when {
        diff < 60_000L -> "刚刚同步"
        diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
        diff < 86_400_000L -> "${diff / 3_600_000L} 小时前"
        else -> SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(at))
    }
}

/** 设置页：昵称 + 坚果云同步配置 + 立即同步 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    initialSection: SettingsSection? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // 从侧边菜单点进来时，直接滚到对应分组。
    // 每个分组恰好是 LazyColumn 的一个 item，顺序与 SettingsSection 枚举一致。
    LaunchedEffect(initialSection) {
        val target = initialSection ?: return@LaunchedEffect
        listState.scrollToItem(SettingsSection.values().indexOf(target).coerceAtLeast(0))
    }

    var myName by remember { mutableStateOf(SettingsStore.myName(context)) }
    var url by remember { mutableStateOf(SettingsStore.webdavUrl(context)) }
    var user by remember { mutableStateOf(SettingsStore.webdavUser(context)) }
    var pass by remember { mutableStateOf(SettingsStore.webdavPass(context)) }
    var showPass by remember { mutableStateOf(false) }
    // 同步状态：结构化 StateFlow（P2-2），后台同步完成后本页自动刷新
    val syncStatus by SyncStatusStore.status.collectAsStateWithLifecycle()
    val statusKind = when (syncStatus.phase) {
        SyncPhase.Idle -> SyncStatusKind.Idle
        SyncPhase.Running -> SyncStatusKind.Syncing
        SyncPhase.Success -> SyncStatusKind.Success
        SyncPhase.Failure -> SyncStatusKind.Failure
    }
    var busy by remember { mutableStateOf(false) }
    var showEndpointConfirm by remember { mutableStateOf(false) }

    // 输入停顿后发送最终昵称；离开设置页时也补发，避免必须重启或手动同步。
    LaunchedEffect(myName) {
        delay(700)
        if (SettingsStore.myName(context) != SettingsStore.propagatedName(context)) {
            SyncManager.enqueueNow(context)
        }
    }
    DisposableEffect(context) {
        onDispose {
            if (SettingsStore.myName(context) != SettingsStore.propagatedName(context)) {
                SyncManager.enqueueNow(context)
            }
        }
    }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // P2-10：导出备份（zip）
    var exporting by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        exporting = true
        scope.launch {
            val result = BackupExporter.export(context, uri)
            exporting = false
            Toast.makeText(
                context,
                if (result.isSuccess) {
                    "备份已导出"
                } else {
                    "导出失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
                },
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // 是否已在电池优化白名单里。
    // 用户点「去设置」跳去系统页、开完再返回时，本页不会重建，
    // 所以用 ON_RESUME 重新读一次，否则按钮会一直显示「去设置」。
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

    fun requestNotificationIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                title = "设置",
                icon = Icons.Outlined.Settings,
                onBack = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = 8.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---------- 我的信息 ----------
            // v2.5.2：丢掉 OutlinedTextField 的默认方框（与全站玻璃胶囊语言冲突），
            // 改成「实体卡片 + 发丝线分隔 + 无框输入」，标签 12sp 弱化、值 15sp 突出。
            item(key = "profile") {
                SettingsGroup(title = "我的信息") {
                    SettingsField(
                        label = "我的昵称",
                        value = myName,
                        onValueChange = {
                            myName = it
                            SettingsStore.saveMyName(context, it)
                        },
                        supporting = "昵称会自动同步到对方看到的历史记录和新内容通知"
                    )
                }
            }

            // ---------- 云同步 ----------
            item(key = "sync") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingsGroup(
                        title = "云同步",
                        description = "两台手机都填同一个坚果云账号并开启同步，" +
                            "你添加的穿搭 / 三餐 / 点滴会自动出现在对方手机上，对方会收到提醒。"
                    ) {
                        SettingsField(
                            label = "服务器地址",
                            value = url,
                            onValueChange = { url = it },
                            supporting = "坚果云用户不用改",
                            keyboardType = KeyboardType.Uri
                        )
                        SettingsDivider()
                        SettingsField(
                            label = "账号",
                            value = user,
                            onValueChange = { user = it },
                            placeholder = "注册邮箱或手机号",
                            keyboardType = KeyboardType.Email
                        )
                        SettingsDivider()
                        SettingsField(
                            label = "应用密码",
                            value = pass,
                            onValueChange = { pass = it },
                            placeholder = "在坚果云生成的 16 位密码",
                            supporting = "获取方法：坚果云网页版 → 账户信息 → 安全选项 → 添加应用密码",
                            visualTransformation = if (showPass) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailing = {
                                IconButton(
                                    onClick = { showPass = !showPass },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = if (showPass) {
                                            Icons.Outlined.VisibilityOff
                                        } else {
                                            Icons.Outlined.Visibility
                                        },
                                        contentDescription = if (showPass) "隐藏密码" else "显示密码",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        )
                    }

                    // ---------- 操作区 ----------
                    // 测试连接 = 唯一主操作（48dp 全宽主按钮）；失败时同一入口可重试。
                    // 先验证、后提交：验证通过才保存配置，失败不动旧配置/账本。
                    fun startTest() {
                        busy = true
                        SyncStatusStore.markRunning()
                        scope.launch {
                            when (val result = SyncEngine.verifyAndApplyDavConfig(context, url, user, pass)) {
                                is SyncEngine.DavTestResult.Ok -> {
                                    busy = false
                                    SyncStatusStore.markSuccess("同步已开启")
                                    SyncManager.ensurePeriodic(context, force = true)
                                    SyncManager.enqueueNow(context)
                                    requestNotificationIfNeeded()
                                }
                                is SyncEngine.DavTestResult.Failure -> {
                                    busy = false
                                    SyncStatusStore.markFailure(result.message, retryable = false)
                                }
                            }
                        }
                    }

                    PrimaryButton(
                        text = if (busy) "正在测试连接…" else "测试并开启同步",
                        enabled = !busy && statusKind != SyncStatusKind.Syncing &&
                            user.isNotBlank() && pass.isNotBlank(),
                        onClick = {
                            if (SettingsStore.davConfigChanged(context, url, user, pass)) {
                                // 换端点会重置增量账本：先让用户确认，再测试+保存
                                showEndpointConfirm = true
                            } else {
                                startTest()
                            }
                        }
                    )

                    if (showEndpointConfirm) {
                        AlertDialog(
                            onDismissRequest = { showEndpointConfirm = false },
                            title = { Text("换服务器？") },
                            text = {
                                Text("换服务器会让本机重新全量拉取一次，但不会删除本地数据。继续？")
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    showEndpointConfirm = false
                                    startTest()
                                }) { Text("继续") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showEndpointConfirm = false }) { Text("取消") }
                            }
                        )
                    }

                    SettingsActionRow(
                        text = "立即同步",
                        icon = true,
                        enabled = !busy && statusKind != SyncStatusKind.Syncing &&
                            SettingsStore.isSyncConfigured(context),
                        onClick = {
                            SyncManager.enqueueNow(context)
                            SyncStatusStore.markRunning()
                        }
                    )

                    // 同步状态条：结构化状态 + 相对时间 + 失败原因 / 重试
                    SyncStatusBar(
                        kind = statusKind,
                        subText = relativeSyncTime(syncStatus.at),
                        reason = if (syncStatus.phase == SyncPhase.Failure) syncStatus.message else null,
                        onRetry = { startTest() }
                    )

                    Text(
                        "说明：APP 开着的时候每 30 秒同步一次，对方刚发的内容基本马上就能看到；" +
                            "退到后台后系统只允许每 15 分钟同步一次（这是 Android 的硬限制，改不了）。" +
                            "所以想及时收到对方的消息，把下面这一项开了会更稳。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---------- 数据备份 ----------
            item(key = "backup") {
                SettingsGroup(
                    title = "数据备份",
                    description = "导出数据库和全部照片（zip），不包含同步密码。导入功能后续版本提供。"
                ) {
                    SettingsActionRow(
                        text = if (exporting) "正在导出…" else "导出备份",
                        icon = true,
                        iconVector = Icons.Outlined.Save,
                        enabled = !exporting,
                        container = false,
                        onClick = {
                            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA)
                                .format(Date())
                            exportLauncher.launch("thecutestgirl-backup-$stamp.zip")
                        }
                    )
                }
            }

            // ---------- 后台保活 ----------
            // 15 分钟是 Android 给普通应用的下限，Doze / 厂商省电还会把它拉长到几十分钟。
            // 唯一的正规解法是让用户把 App 加进电池优化白名单，系统就不再冻结它的后台任务。
            item(key = "battery") {
                SettingsGroup(
                    title = "后台不被冻结",
                    description = "把本 App 加进电池优化白名单。不加的话，手机放着一会儿后台同步就会被系统" +
                        "掐掉，对方的通知可能延迟很久才到。"
                ) {
                    SettingsActionRow(
                        text = if (isIgnoringBatteryOptimizations) "已加入白名单" else "去设置",
                        icon = true,
                        enabled = !isIgnoringBatteryOptimizations,
                        container = false,
                        onClick = { context.openBatteryOptimizationSettings() }
                    )
                }
            }
        }
    }
}

/**
 * 设置分组：小标题 + 可选说明 + 一张实体卡片。
 *
 * v2.5.2 新增。之前设置页是 4 个 OutlinedTextField 平铺在页面上，
 * 每个都带一圈方框 + 浮动 label，和全站「玻璃胶囊 + 发丝线」的语言完全对不上。
 * 现在按「分组 = 卡片」组织，组内用发丝线切行，视觉重量和列表页卡片一致。
 */
@Composable
private fun SettingsGroup(
    title: String,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
    if (!description.isNullOrBlank()) {
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .solidCard(cornerRadius = 18.dp)
            .padding(vertical = 4.dp),
        content = content
    )
}

/** 组内发丝线：左缩进与输入文字对齐，右不缩进（裁到卡片圆角内） */
@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    )
}

/**
 * 无框输入行：12sp 灰标签 + 15sp 正文值 + 可选说明。
 *
 * 用 [BasicTextField] 而不是 TextField：要的就是「没有框」——
 * 光标用主色 1dp，占位符用 40% 的辅助色，焦点态靠标签变色提示，不引入任何描边。
 */
@Composable
private fun SettingsField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supporting: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
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
                        color = scheme.onSurfaceVariant.copy(alpha = 0.6f)
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
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (trailing != null) trailing()
        }
        if (!supporting.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                supporting,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant.copy(alpha = 0.85f)
            )
        }
    }
}

/** 次要操作行：左图标 + 文案，整行可点，禁用时降到 38% 透明度 */
@Composable
private fun SettingsActionRow(
    text: String,
    icon: Boolean,
    enabled: Boolean,
    container: Boolean = true,
    iconVector: ImageVector = Icons.Outlined.CloudSync,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (enabled) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.38f)
    // 全站统一不用系统 ripple 方框，反馈由卡片本身的投影与按压色承担
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(if (container) Modifier.solidCard(cornerRadius = 16.dp) else Modifier)
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

/** 是否已在电池优化白名单（PowerManager 的 API 从 23 起才有，minSdk 26 所以直接用） */
private fun Context.isIgnoringBatteryOptimizations(): Boolean =
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
private fun Context.openBatteryOptimizationSettings() {
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
