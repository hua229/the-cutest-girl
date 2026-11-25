package com.thecutestgirl.app.sync

import android.content.Context
import com.thecutestgirl.app.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SyncPhase { Idle, Running, Success, Partial, Failure }

/**
 * 结构化同步状态（P2-2）。
 *
 * UI 不再解析 `lastSyncResult` 字符串：设置页/三个列表页都收集这个 StateFlow，
 * 后台同步完成后自动刷新。`SettingsStore` 里的字符串仍照旧写入，兼容旧版本。
 */
data class SyncStatus(
    val phase: SyncPhase = SyncPhase.Idle,
    val at: Long = 0L,
    val message: String = "",
    val retryable: Boolean = true,
    val pending: Int = 0,
    val skipped: Int = 0
)

private fun completedSyncStatus(
    at: Long,
    pending: Int,
    skipped: Int,
    message: String = "同步成功"
): SyncStatus = SyncStatus(
    phase = if (pending > 0 || skipped > 0) SyncPhase.Partial else SyncPhase.Success,
    at = at,
    message = message,
    pending = pending,
    skipped = skipped
)

internal fun SyncEngine.Result.toSyncStatus(at: Long): SyncStatus =
    if (ok) {
        completedSyncStatus(at, pending, skipped)
    } else {
        SyncStatus(
            phase = SyncPhase.Failure,
            at = at,
            message = error ?: "同步失败",
            retryable = retryable
        )
    }

private val pendingSummary = Regex("，([0-9]+) 条待重传")
private val skippedSummary = Regex("，跳过 ([0-9]+) 条异常数据")

internal fun restoreSyncStatus(at: Long, summary: String): SyncStatus {
    if (at == 0L) return SyncStatus()
    if (summary.startsWith("同步失败")) {
        return SyncStatus(
            phase = SyncPhase.Failure,
            at = at,
            message = summary.removePrefix("同步失败：").ifBlank { "同步失败" }
        )
    }
    return completedSyncStatus(
        at = at,
        pending = pendingSummary.find(summary)?.groupValues?.get(1)?.toIntOrNull() ?: 0,
        skipped = skippedSummary.find(summary)?.groupValues?.get(1)?.toIntOrNull() ?: 0,
        message = summary
    )
}

object SyncStatusStore {

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    fun markRunning() {
        _status.value = _status.value.copy(phase = SyncPhase.Running, message = "")
    }

    fun markSuccess(message: String = "同步成功") {
        _status.value = SyncStatus(
            phase = SyncPhase.Success,
            at = System.currentTimeMillis(),
            message = message
        )
    }

    fun markResult(result: SyncEngine.Result) {
        _status.value = result.toSyncStatus(System.currentTimeMillis())
    }

    fun markFailure(message: String, retryable: Boolean) {
        _status.value = SyncStatus(
            phase = SyncPhase.Failure,
            at = System.currentTimeMillis(),
            message = message,
            retryable = retryable
        )
    }

    /** App 启动时用上次落盘的结果初始化，避免设置页先显示「空闲」再跳变 */
    fun prime(context: Context) {
        if (_status.value.phase != SyncPhase.Idle) return
        _status.value = restoreSyncStatus(
            SettingsStore.lastSyncAt(context),
            SettingsStore.lastSyncResult(context)
        )
    }
}
