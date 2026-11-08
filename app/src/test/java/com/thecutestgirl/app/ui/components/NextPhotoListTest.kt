package com.thecutestgirl.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0-1 回归：单图「换一张」必须真正替换，多图超额必须把多余文件标记为孤儿。
 * 纯函数测试，不依赖 Android 环境。
 */
class NextPhotoListTest {

    @Test
    fun `single photo replaces old and marks old as orphan`() {
        val (result, orphans) = nextPhotoList(
            current = listOf("old.jpg"),
            added = listOf("new.jpg"),
            maxPhotos = 1
        )
        assertEquals(listOf("new.jpg"), result)
        assertEquals(listOf("old.jpg"), orphans)
    }

    @Test
    fun `single photo does not touch current when nothing added`() {
        val (result, orphans) = nextPhotoList(
            current = listOf("old.jpg"),
            added = emptyList(),
            maxPhotos = 1
        )
        assertEquals(listOf("old.jpg"), result)
        assertTrue(orphans.isEmpty())
    }

    @Test
    fun `multiple photos keep limit and orphan the overflow`() {
        val (result, orphans) = nextPhotoList(
            current = listOf("a.jpg", "b.jpg"),
            added = listOf("c.jpg", "d.jpg"),
            maxPhotos = 3
        )
        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), result)
        assertEquals(listOf("d.jpg"), orphans)
    }

    @Test
    fun `multiple photos within limit produce no orphans`() {
        val (result, orphans) = nextPhotoList(
            current = listOf("a.jpg"),
            added = listOf("b.jpg"),
            maxPhotos = 9
        )
        assertEquals(listOf("a.jpg", "b.jpg"), result)
        assertTrue(orphans.isEmpty())
    }
}