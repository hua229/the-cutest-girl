package com.thecutestgirl.app.ui.settings

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thecutestgirl.app.data.BackupExporter
import com.thecutestgirl.app.data.BackupImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class BackupSettingsViewModel(app: Application) : AndroidViewModel(app) {
    var progress by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var preview by mutableStateOf<BackupImporter.Preview?>(null)
        private set

    fun export(uri: Uri) = runOperation("正在导出…") {
        message = BackupExporter.export(getApplication(), uri).fold(
            onSuccess = { "备份已导出" },
            onFailure = { "导出失败：${it.message ?: "未知错误"}" }
        )
    }

    fun prepare(uri: Uri) = runOperation("正在检查备份…") {
        BackupImporter.prepare(getApplication(), uri).fold(
            onSuccess = { preview = it },
            onFailure = { message = "无法读取备份：${it.message ?: "未知错误"}" }
        )
    }

    fun restore() {
        val backup = preview ?: return
        runOperation("正在恢复…") {
            preview = null
            try {
                message = BackupImporter.restore(getApplication(), backup).fold(
                    onSuccess = { "恢复完成。需要云同步时，请重新设置同步账号和应用密码。" },
                    onFailure = { "恢复失败：${it.message ?: "未知错误"}" }
                )
            } finally {
                withContext(Dispatchers.IO + NonCancellable) { backup.discard() }
            }
        }
    }

    fun discardPreview() {
        val backup = preview ?: return
        preview = null
        viewModelScope.launch(Dispatchers.IO + NonCancellable) { backup.discard() }
    }

    override fun onCleared() {
        discardPreview()
        super.onCleared()
    }

    private fun runOperation(label: String, operation: suspend () -> Unit) {
        if (progress != null) return
        progress = label
        message = null
        viewModelScope.launch {
            try {
                operation()
            } finally {
                progress = null
            }
        }
    }
}

@Composable
fun BackupSettingsScreen(onBack: () -> Unit) {
    val vm: BackupSettingsViewModel = viewModel()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let(vm::export) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::prepare) }
    val enabled = vm.progress == null && vm.preview == null

    SettingsPage(
        title = "数据备份",
        icon = Icons.Outlined.Save,
        onBack = onBack
    ) {
        SettingsCard {
            SettingsActionRow(
                text = "导出备份",
                icon = true,
                iconVector = Icons.Outlined.Save,
                enabled = enabled,
                container = false,
                onClick = {
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA)
                        .format(Date())
                    exportLauncher.launch("thecutestgirl-backup-$stamp.zip")
                }
            )
            SettingsDivider()
            SettingsActionRow(
                text = "从备份恢复",
                icon = true,
                iconVector = Icons.Outlined.FileOpen,
                enabled = enabled,
                container = false,
                onClick = { importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed")) }
            )
        }
        (vm.progress ?: vm.message)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }

    vm.preview?.let { backup ->
        AlertDialog(
            onDismissRequest = vm::discardPreview,
            title = { Text("恢复这份备份？") },
            text = {
                Text(buildString {
                    append("穿搭 ${backup.outfits} 条 · 饮食 ${backup.meals} 条 · 点滴 ${backup.events} 条\n")
                    append("照片 ${backup.photos} 张\n\n")
                    append("恢复后会以备份中的身份继续使用。")
                    if (backup.missingPhotos > 0) {
                        append("\n备份缺少 ${backup.missingPhotos} 张关联照片，配置同步后可尝试补回。")
                    }
                })
            },
            confirmButton = { TextButton(onClick = vm::restore) { Text("确认恢复") } },
            dismissButton = { TextButton(onClick = vm::discardPreview) { Text("取消") } }
        )
    }
}
