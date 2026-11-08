package com.thecutestgirl.app.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.thecutestgirl.app.MainActivity
import com.thecutestgirl.app.R
import com.thecutestgirl.app.data.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * 同步任务调度。
 *
 * 三层节奏，越靠前越快：
 * 1. **前台轮询**（[SyncForegroundPoller]，20 秒一次）——App 开着的时候，对方一传这边几乎立刻就有
 * 2. **即时任务**（[enqueueNow]，保存/打开 App 时）——WorkManager 单次任务，通常几秒内跑
 * 3. **周期任务**（[ensurePeriodic]，15 分钟）——系统允许的最小间隔，是「App 没开」时的兜底
 *
 * ⚠️ 15 分钟是 Android 的硬下限，且 Doze / 厂商省电会把实际间隔拉长到几十分钟甚至更久。
 * 这就是「不打开 App 基本收不到通知」的根因，不是代码写错了。要真正稳住后台，
 * 只能让用户把 App 加进电池优化白名单（设置页有入口，见 SettingsScreen 的「后台不被冻结」）。
 */
object SyncManager {

    private const val PERIODIC_NAME = "tcg_sync_periodic"
    private const val ONCE_NAME = "tcg_sync_now"

    /** 3 秒内重复调用只入队一次（旋转屏幕、保存连点等） */
    @Volatile
    private var lastEnqueueAt = 0L

    /**
     * 注册/保持 15 分钟一次的后台同步（没配置账号时什么都不做）。
     * 默认 KEEP：避免每次启动都 UPDATE 把下一次执行时间往后推。
     * 用户改配置 / 换云盘后传 [force] = true 才 UPDATE。
     */
    fun ensurePeriodic(context: Context, force: Boolean = false) {
        if (!SettingsStore.isSyncConfigured(context)) return
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                if (force) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                request
            )
    }

    /** 立即同步一次（保存记录、打开 APP、点「立即同步」时调用） */
    fun enqueueNow(context: Context) {
        if (!SettingsStore.isSyncConfigured(context)) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastEnqueueAt < 3_000L) return
        lastEnqueueAt = now
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .addTag(ONCE_NAME)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(context)
            // 不能 enqueueUniqueWork(KEEP)：一次失败任务进入退避后，KEEP 会挡住后续「立即同步」，
            // 用户点同步要等退避结束才生效（回归 immediate sync bypasses retry delay 覆盖这一点）。
            // 去重由上面的 3 秒节流 + SyncEngine 的进程级 Mutex 保证。
            .enqueue(request)
    }

    /** 账号刚配好：先把周期任务挂上，再立刻来一次 */
    fun startAfterConfigured(context: Context) {
        SyncStatusStore.prime(context)
        ensurePeriodic(context)
        enqueueNow(context)
    }
}

/**
 * 同步的真正执行体：跑一轮同步，对方有新内容就发通知。
 *
 * 抽出来是因为它有两个调用方——WorkManager 的 [SyncWorker]，和前台的 [SyncForegroundPoller]。
 * 两处必须走同一条路径，否则「前台看到有新内容但不通知」这类不一致会很难查。
 */
object SyncRunner {

    suspend fun runOnce(context: Context): SyncEngine.Result {
        // P2-2：结构化状态供 UI 收集；字符串版本仍写入 SettingsStore 兼容旧版本
        SyncStatusStore.markRunning()
        val result = SyncEngine(context.applicationContext).syncOnce()
        if (result.ok) {
            SyncStatusStore.markSuccess()
        } else {
            SyncStatusStore.markFailure(
                result.error ?: "同步失败",
                retryable = result.retryable
            )
        }
        if (result.partnerNew.isNotEmpty()) {
            PartnerNotifier.notify(context.applicationContext, result.partnerNew)
        }
        return result
    }
}

/** 真正干活的同步任务：跑同步 → 对方有新内容就发系统通知 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = SyncRunner.runOnce(applicationContext)
        return when {
            result.ok -> Result.success()
            // 认证/地址/配额/数据问题重试也没用，交给状态条显示原因
            result.retryable -> Result.retry()
            else -> Result.failure()
        }
    }
}

/**
 * 开机后把周期任务重新挂一遍。
 *
 * WorkManager 自己也会在开机后恢复任务，但它恢复的是「没被系统清过数据」的那份。
 * 用户手动「清除数据」、或某些厂商 ROM 会清掉 WorkManager 的库，任务就永久消失了。
 * 这个接收器是兜底：开机、以及 App 被更新替换后，都重新注册一次（重复注册是幂等的）。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (SettingsStore.isSyncConfigured(context)) {
                    SyncManager.ensurePeriodic(context)
                }
            }
        }
    }
}

/**
 * 前台轮询：App 正在前台时每 20 秒同步一次。
 *
 * 为什么需要它：WorkManager 的周期任务最短 15 分钟，Doze 下还会更长。
 * 两个人都开着 App 的时候，谁也不想等 15 分钟才看到对方刚发的照片。
 * 20 秒是权衡——配合 v2.9.0 的增量同步，一轮在没变化时只有 2 次 PROPFIND，几乎不耗流量。
 *
 * 只在 RESUMED 状态跑（[com.thecutestgirl.app.ui.AppRoot] 里用 repeatOnLifecycle 控制），
 * 一旦退到后台就停，不占后台资源。
 */
object SyncForegroundPoller {

    /** 前台轮询正常间隔：30 秒（增量同步无变化时只有 2 次 PROPFIND） */
    const val INTERVAL_MS = 30_000L

    /** 连续失败时的退避上限 */
    private const val MAX_INTERVAL_MS = 300_000L

    suspend fun runForever(context: Context) {
        var interval = INTERVAL_MS
        while (true) {
            if (SettingsStore.isSyncConfigured(context)) {
                val result = try {
                    SyncRunner.runOnce(context)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    SyncEngine.Result(ok = false, error = e.message, retryable = true)
                }
                if (result.ok) {
                    interval = INTERVAL_MS
                } else if (!result.retryable) {
                    // 认证/地址/配额问题：停止轮询，避免每 30 秒打服务器。
                    // 用户改完配置或重新回到前台时会重新启动。
                    return
                } else {
                    interval = (interval * 2).coerceAtMost(MAX_INTERVAL_MS)
                }
            }
            delay(interval)
        }
    }
}

/**
 * 「对方发来了新内容」的系统通知。
 *
 * v2.9.0 三处改动，都是冲着「不打开 App 就收不到」去的：
 * 1. 渠道从 `IMPORTANCE_DEFAULT` 升到 **`IMPORTANCE_HIGH`** —— 只有 HIGH 才会弹横幅/响铃。
 *    DEFAULT 只是安静地躺在通知栏里，用户不主动下拉就永远看不见。
 * 2. 点通知 **直达对应页面 + 日期**（原来只打开首页，用户还得自己翻）。
 * 3. 按记录 id **去重**：前台 20 秒轮询会反复扫到同一条记录，
 *    没有去重就会 20 秒弹一次同样的通知。
 */
object PartnerNotifier {

    /**
     * 渠道 id 必须换新。
     *
     * Android 的规矩是：渠道一旦建好，**重要级别就钉死了**，再调
     * `createNotificationChannel` 传更高的 IMPORTANCE 也不会生效 —— 系统直接忽略。
     * 老用户手机上 `partner_updates` 早就以 DEFAULT 建好了，改常量值而不换 id，
     * 等于什么都没改：新装用户能看到横幅，升级上来的永远只有静音通知。
     * 所以这里换成 `partner_updates_v2`，并把旧渠道删掉，免得设置页里出现两条同名项。
     */
    private const val CHANNEL_ID = "partner_updates_v2"
    private const val LEGACY_CHANNEL_ID = "partner_updates"
    private const val NOTICE_ID = 2001

    const val EXTRA_ROUTE = "extra_route"
    const val EXTRA_DATE = "extra_date"
    const val EXTRA_RECORD_ID = "extra_record_id"

    fun notify(context: Context, items: List<SyncEngine.PartnerItem>) {
        val manager = NotificationManagerCompat.from(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!manager.areNotificationsEnabled()) return

        // 去重：轮询会反复看到同一条记录，只对「这轮第一次见到的」发通知。
        // 键是 id@版本，所以同一条记录被对方改了第二次仍会提醒。
        val alreadyNotified = SyncStateStore.notifiedIds(context)
        val fresh = items.filter { it.notifyKey.isBlank() || it.notifyKey !in alreadyNotified }
        if (fresh.isEmpty()) return

        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "对方的新内容",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "ta 添加或修改了穿搭、三餐、点滴时提醒"
                enableVibration(true)
                // 横幅要能穿透「请勿打扰」之外的静音设置，否则用户还是看不到
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
            manager.createNotificationChannel(channel)
            // 旧渠道删掉，别在系统设置里留一条「对方的新内容」静音项让用户困惑
            runCatching { manager.deleteNotificationChannel(LEGACY_CHANNEL_ID) }
        }

        val counts = fresh.groupingBy { it.typeLabel }.eachCount()
        val summary = counts.entries.joinToString(" · ") { "${it.key} ×${it.value}" }
        val who = fresh.lastOrNull()?.fromName?.takeIf { it.isNotBlank() } ?: "对方"

        // 点通知直达「最新那条」所在的页面和日期
        val target = fresh.lastOrNull()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ROUTE, target?.route.orEmpty())
            putExtra(EXTRA_DATE, target?.date.orEmpty())
            putExtra(EXTRA_RECORD_ID, target?.recordId.orEmpty())
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle("$who 发来了新内容")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // 先确认通知真的发出去，再记入去重集合；否则发送失败会永久漏提醒
        val posted = runCatching { manager.notify(NOTICE_ID, notification) }.isSuccess
        if (posted) {
            SyncStateStore.rememberNotified(context, fresh.map { it.notifyKey }.filter { it.isNotBlank() })
        }
    }
}
