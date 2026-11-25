package com.thecutestgirl.app.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncStatusTest {
    @Test
    fun failedUploadsMakeAnOtherwiseSuccessfulRunPartial() {
        val status = SyncEngine.Result(ok = true, pending = 2).toSyncStatus(at = 42L)

        assertEquals(SyncPhase.Partial, status.phase)
        assertEquals(2, status.pending)
        assertEquals(42L, status.at)
    }

    @Test
    fun skippedDataMakesAnOtherwiseSuccessfulRunPartial() {
        val status = SyncEngine.Result(ok = true, skipped = 1).toSyncStatus(at = 42L)

        assertEquals(SyncPhase.Partial, status.phase)
        assertEquals(0, status.pending)
        assertEquals(1, status.skipped)
    }

    @Test
    fun nextSuccessfulRunClearsPendingAndSkippedCounts() {
        val status = SyncEngine.Result(ok = true).toSyncStatus(at = 43L)

        assertEquals(SyncPhase.Success, status.phase)
        assertEquals(0, status.pending)
        assertEquals(0, status.skipped)
    }

    @Test
    fun restoredSummaryPreservesPartialCounts() {
        val status = restoreSyncStatus(
            at = 42L,
            summary = "同步成功（瘦身 3 张历史大图），2 条待重传，跳过 1 条异常数据"
        )

        assertEquals(SyncPhase.Partial, status.phase)
        assertEquals(2, status.pending)
        assertEquals(1, status.skipped)
    }

    @Test
    fun restoredLegacyFailureKeepsItsReason() {
        val status = restoreSyncStatus(at = 42L, summary = "同步失败：账号错误")

        assertEquals(SyncPhase.Failure, status.phase)
        assertEquals("账号错误", status.message)
    }
}
