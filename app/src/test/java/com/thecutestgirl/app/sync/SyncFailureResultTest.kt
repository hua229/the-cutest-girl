package com.thecutestgirl.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncFailureResultTest {
    @Test
    fun unexpectedExceptionBecomesRetryableFailure() {
        val result = unexpectedSyncFailure(IllegalStateException("连接测试异常"))

        assertFalse(result.ok)
        assertEquals("连接测试异常", result.error)
        assertTrue(result.retryable)
    }

    @Test
    fun exceptionWithoutMessageUsesStableUserMessage() {
        val result = unexpectedSyncFailure(IllegalStateException())

        assertEquals("同步失败，请稍后重试", result.error)
    }
}
