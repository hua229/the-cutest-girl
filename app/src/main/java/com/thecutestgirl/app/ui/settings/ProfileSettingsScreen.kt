package com.thecutestgirl.app.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.thecutestgirl.app.data.SettingsStore
import com.thecutestgirl.app.sync.SyncManager
import kotlinx.coroutines.delay

/**
 * 我的信息：只负责昵称。
 *
 * 昵称立即写本地；输入停顿 700ms 或离开页面时补一次同步，
 * 这样对方看到的历史记录和新内容通知不用等下一轮定时同步。
 */
@Composable
fun ProfileSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var myName by remember { mutableStateOf(SettingsStore.myName(context)) }

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

    SettingsPage(
        title = "我的信息",
        icon = Icons.Outlined.PersonOutline,
        onBack = onBack
    ) {
        SettingsCard {
            SettingsField(
                label = "我的昵称",
                value = myName,
                onValueChange = {
                    myName = it
                    SettingsStore.saveMyName(context, it)
                }
            )
        }
    }
}
