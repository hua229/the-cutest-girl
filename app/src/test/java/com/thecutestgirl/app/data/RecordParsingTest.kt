package com.thecutestgirl.app.data

import com.thecutestgirl.app.sync.SyncStateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** P2-11：纯函数回归（不需要 Android 设备） */
class RecordParsingTest {

    @Test
    fun `valid iso date parses`() {
        assertEquals(LocalDate.of(2026, 10, 24), parseRecordDate("2026-10-24"))
    }

    @Test
    fun `invalid dates are rejected`() {
        assertNull(parseRecordDate(""))
        assertNull(parseRecordDate("2026-13-40"))
        assertNull(parseRecordDate("not-a-date"))
    }

    @Test
    fun `signature combines server time and size`() {
        assertEquals("123:456", SyncStateStore.signatureOf(123L, 456L))
    }
}