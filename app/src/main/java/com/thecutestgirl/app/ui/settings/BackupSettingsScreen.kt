package com.thecutestgirl.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.thecutestgirl.app.data.BackupExporter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 数据备份：导出一个包含数据库和全部照片的 zip。
 *
 * 导出过程不离开本页；系统文件选择器取消时什么都不做。
 */
@Composable
fun BackupSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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

    SettingsPage(
        title = "数据备份",
        icon = Icons.Outlined.Save,
        onBack = onBack
    ) {
        Text(
            "导出数据库和全部照片（zip），不包含同步密码。导入功能后续版本提供。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SettingsCard {
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
}