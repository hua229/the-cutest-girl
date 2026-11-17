package com.thecutestgirl.app.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.thecutestgirl.app.ui.theme.TheCutestGirlTheme

/**
 * 深浅成对的组件 Preview：纸卡、主按钮、次按钮、分段控件和禁用态。
 * 悬停/按压依赖真实交互源，真机与鼠标设备走查；这里保证静态状态和双主题可同时核对。
 */
@Preview(name = "纸卡与按钮状态 · 浅色", showBackground = true)
@Preview(name = "纸卡与按钮状态 · 深色", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PaperComponentsPreview() {
    TheCutestGirlTheme {
        Column(
            modifier = Modifier
                .paperBackground()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "卡片标题",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp)
                )
            }
            PrimaryButton(text = "保存", onClick = {})
            PrimaryButton(text = "不可保存", onClick = {}, enabled = false)
            SoftCapsuleButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Text("次要操作", style = MaterialTheme.typography.labelLarge)
            }
            SoftCapsuleButton(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                enabled = false
            ) {
                Text("不可操作", style = MaterialTheme.typography.labelLarge)
            }
            SegmentedChoices(
                options = listOf("跟随系统", "浅色", "深色"),
                selectedIndex = 0,
                onSelect = {}
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}
