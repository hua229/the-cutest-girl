package com.thecutestgirl.app.ui.settings

import android.app.Application
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thecutestgirl.app.data.SettingsStore
import com.thecutestgirl.app.sync.SyncEngine
import com.thecutestgirl.app.sync.SyncManager
import com.thecutestgirl.app.sync.SyncPhase
import com.thecutestgirl.app.sync.SyncStatusStore
import com.thecutestgirl.app.ui.components.PrimaryButton
import com.thecutestgirl.app.ui.components.SyncStatusBar
import com.thecutestgirl.app.ui.components.SyncStatusKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 配置草稿留在页面 ViewModel 中，主题/旋转不会丢失，也不写入 saved state。 */
internal class SyncSettingsFormViewModel(app: Application) : AndroidViewModel(app) {
    val url = mutableStateOf(SettingsStore.webdavUrl(app))
    val user = mutableStateOf(SettingsStore.webdavUser(app))
    val pass = mutableStateOf(SettingsStore.webdavPass(app))
    val showPass = mutableStateOf(false)
}

/**
 * 云同步：服务器、账号、应用密码、测试连接与立即同步。
 *
 * 仍然是「先验证、后提交」：测试通过才保存配置，失败不动旧配置和同步账本。
 */
@Composable
fun SyncSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val form: SyncSettingsFormViewModel = viewModel()
    var url by form.url
    var user by form.user
    var pass by form.pass
    var showPass by form.showPass
    // 同步状态：结构化 StateFlow（P2-2），后台同步完成后本页自动刷新
    val syncStatus by SyncStatusStore.status.collectAsStateWithLifecycle()
    val statusKind = when (syncStatus.phase) {
        SyncPhase.Idle -> SyncStatusKind.Idle
        SyncPhase.Running -> SyncStatusKind.Syncing
        SyncPhase.Success -> SyncStatusKind.Success
        SyncPhase.Partial -> SyncStatusKind.Partial
        SyncPhase.Failure -> SyncStatusKind.Failure
    }
    var busy by remember { mutableStateOf(false) }
    var showEndpointConfirm by remember { mutableStateOf(false) }
    var showSyncHelp by remember { mutableStateOf(false) }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun requestNotificationIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun startTest() {
        busy = true
        SyncStatusStore.markRunning()
        scope.launch {
            try {
                when (val result = SyncEngine.verifyAndApplyDavConfig(context, url, user, pass)) {
                    is SyncEngine.DavTestResult.Ok -> {
                        SyncStatusStore.markSuccess("同步已开启")
                        SyncManager.ensurePeriodic(context, force = true)
                        SyncManager.enqueueNow(context)
                        requestNotificationIfNeeded()
                    }
                    is SyncEngine.DavTestResult.Failure -> {
                        SyncStatusStore.markFailure(result.message, retryable = false)
                    }
                }
            } catch (e: CancellationException) {
                SyncStatusStore.markFailure("连接已中断，请重试", retryable = true)
                throw e
            } catch (e: Exception) {
                SyncStatusStore.markFailure(
                    e.message?.takeIf { it.isNotBlank() } ?: "同步失败，请稍后重试",
                    retryable = true
                )
            } finally {
                busy = false
            }
        }
    }

    SettingsPage(
        title = "云同步",
        icon = Icons.Outlined.CloudSync,
        onBack = onBack
    ) {
        SettingsCard {
            SettingsField(
                label = "服务器地址",
                value = url,
                onValueChange = { url = it },
                keyboardType = KeyboardType.Uri
            )
            SettingsDivider()
            SettingsField(
                label = "账号",
                value = user,
                onValueChange = { user = it },
                keyboardType = KeyboardType.Email
            )
            SettingsDivider()
            SettingsField(
                label = "应用密码",
                value = pass,
                onValueChange = { pass = it },
                visualTransformation = if (showPass) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailing = {
                    IconButton(
                        onClick = { showPass = !showPass },
                        modifier = Modifier.size(48.dp)
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

        // 测试连接 = 唯一主操作（48dp 全宽主按钮）；失败时同一入口可重试。
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

        val syncConfigured = SettingsStore.isSyncConfigured(context)
        SettingsActionRow(
            text = "立即同步",
            icon = true,
            enabled = !busy && statusKind != SyncStatusKind.Syncing &&
                syncConfigured,
            onClick = {
                SyncManager.enqueueNow(context)
            }
        )

        val syncDetail = when (syncStatus.phase) {
            SyncPhase.Partial -> buildString {
                if (syncStatus.pending > 0) append("还有 ${syncStatus.pending} 条待重传")
                if (syncStatus.skipped > 0) {
                    if (isNotEmpty()) append("，")
                    append("跳过 ${syncStatus.skipped} 条异常数据")
                }
            }
            SyncPhase.Failure -> syncStatus.message
            else -> null
        }
        val retrySync: (() -> Unit)? = when {
            busy -> null
            syncStatus.phase == SyncPhase.Partial && syncStatus.pending > 0 && syncConfigured ->
                ({ SyncManager.enqueueNow(context) })
            syncStatus.phase == SyncPhase.Failure -> ({ startTest() })
            else -> null
        }
        SyncStatusBar(
            kind = statusKind,
            subText = relativeSyncTime(syncStatus.at),
            reason = syncDetail,
            onRetry = retrySync
        )

        TextButton(onClick = { showSyncHelp = true }) { Text("同步帮助") }
    }
    if (showSyncHelp) {
        AlertDialog(
            onDismissRequest = { showSyncHelp = false },
            title = { Text("同步帮助") },
            text = { Text("两台手机使用同一个坚果云账号。\n\n应用密码：坚果云网页版 → 账户信息 → 安全选项 → 添加应用密码。\n\n服务器地址可使用默认值。") },
            confirmButton = { TextButton(onClick = { showSyncHelp = false }) { Text("知道了") } }
        )
    }
}
