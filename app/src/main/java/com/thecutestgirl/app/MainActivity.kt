package com.thecutestgirl.app

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.mutableStateOf
import com.thecutestgirl.app.data.PhotoStore
import com.thecutestgirl.app.sync.PartnerNotifier
import com.thecutestgirl.app.sync.SyncManager
import com.thecutestgirl.app.ui.AppRoot
import com.thecutestgirl.app.ui.NotificationTarget
import com.thecutestgirl.app.ui.theme.TheCutestGirlTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /**
     * 点通知进来时要落到哪一页。
     *
     * 用 `mutableStateOf` 而不是直接当构造参数传：App 已经在前台时点通知走的是
     * [onNewIntent]（不会重建 Activity），这时必须能通知 Compose 换页，
     * 光改字段是没用的。
     */
    private var notificationTarget by mutableStateOf<NotificationTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        // 只有真正首次创建才触发同步排单；旋转屏幕等重建不重复入队
        if (savedInstanceState == null) {
            SyncManager.startAfterConfigured(this)
        }
        // P2-7：清理进程被杀留下的过期草稿照片；FIX-05：同时清理孤儿照片（不要求开启同步）
        lifecycleScope.launch(Dispatchers.IO) {
            PhotoStore.cleanupOldDrafts(this@MainActivity)
            PhotoStore.cleanupOrphans(this@MainActivity)
        }
        notificationTarget = intent.toNotificationTarget()
        setContent {
            TheCutestGirlTheme {
                AppRoot(
                    notificationTarget = notificationTarget,
                    onNotificationTargetConsumed = { notificationTarget = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // App 已在前台时点通知会走这里：不重建 Activity，只换页
        intent.toNotificationTarget()?.let { notificationTarget = it }
    }

    private fun Intent.toNotificationTarget(): NotificationTarget? {
        val route = getStringExtra(PartnerNotifier.EXTRA_ROUTE).orEmpty()
        if (route.isBlank()) return null
        return NotificationTarget(
            route = route,
            date = getStringExtra(PartnerNotifier.EXTRA_DATE).orEmpty(),
            recordId = getStringExtra(PartnerNotifier.EXTRA_RECORD_ID).orEmpty()
        )
    }
}
