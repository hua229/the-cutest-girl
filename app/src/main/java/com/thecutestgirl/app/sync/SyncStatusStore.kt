package com.thecutestgirl.app.sync

import android.content.Context
import com.thecutestgirl.app.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SyncPhase { Idle, Running, Success, Failure }

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
    val retryable: Boolean = true
)

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
        val at = SettingsStore.lastSyncAt(context)
        if (at == 0L) return
        val result = SettingsStore.lastSyncResult(context)
        if (result.startsWith("同步失败")) {
            _status.value = SyncStatus(
                phase = SyncPhase.Failure,
                at = at,
                message = result.removePrefix("同步失败：").ifBlank { "同步失败" },
                retryable = true
            )
        } else {
            _status.value = SyncStatus(phase = SyncPhase.Success, at = at, message = result)
        }
    }
}