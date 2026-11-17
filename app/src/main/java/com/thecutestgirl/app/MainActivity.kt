package com.thecutestgirl.app

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.thecutestgirl.app.data.PhotoStore
import com.thecutestgirl.app.sync.PartnerNotifier
import com.thecutestgirl.app.sync.SyncManager
import com.thecutestgirl.app.ui.AppRoot
import com.thecutestgirl.app.ui.NotificationTarget
import com.thecutestgirl.app.ui.theme.ThemeStore
import com.thecutestgirl.app.ui.theme.TheCutestGirlTheme
import com.thecutestgirl.app.ui.theme.isSystemNight
import com.thecutestgirl.app.ui.theme.windowBackgroundArgb
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
        // 先灌已存的主题档位，再画第一帧：冷启动就要落在用户上次选的浅/深上
        ThemeStore.init(this)
        super.onCreate(savedInstanceState)
        applyWindowChrome(
            ThemeStore.mode.value.resolve(resources.configuration.isSystemNight())
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
            val mode by ThemeStore.mode.collectAsStateWithLifecycle()
            val darkTheme = mode.resolve(isSystemInDarkTheme())
            // 换档位时重设系统栏图标与窗口底色。enableEdgeToEdge 可以重复调用，
            // 所以手动切深色不需要重建 Activity（重建会丢掉正在填的表单和滚动位置）。
            SideEffect { applyWindowChrome(darkTheme) }
            TheCutestGirlTheme(darkTheme = darkTheme) {
                AppRoot(
                    notificationTarget = notificationTarget,
                    onNotificationTargetConsumed = { notificationTarget = null }
                )
            }
        }
    }

    /**
     * 把解析后的深浅落到「Compose 之外」的两处：系统栏图标颜色、窗口底色。
     *
     * 这两处不跟 `MaterialTheme` 走：`SystemBarStyle.light` 是「深图标」（配浅底），
     * 写死的话深色模式下状态栏变成深字深底；窗口底色则由 [windowBackgroundArgb] 给出，
     * 顶在 Compose 第一帧之前。
     */
    private fun applyWindowChrome(darkTheme: Boolean) {
        val barStyle = if (darkTheme) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        window.setBackgroundDrawable(ColorDrawable(windowBackgroundArgb(darkTheme)))
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
