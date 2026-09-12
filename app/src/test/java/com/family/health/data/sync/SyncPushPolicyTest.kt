package com.family.health.data.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPushPolicyTest {
    @Test
    fun `ordered batches keep first-seen idemKey order`() {
        assertEquals(
            listOf("a", "b", "c"),
            orderedOutboxBatches(listOf("a", "a", "b", "c", "b")),
        )
    }

    @Test
    fun `failed batch does not stop later batches`() = runBlocking {
        val deleted = mutableListOf<Int>()
        val result = runContinuingBatches(listOf(0, 1, 2)) { idx ->
            if (idx == 0) throw Exception("墓碑不可改")
            deleted += idx
        }
        assertEquals(listOf(1, 2), result.succeededIndices)
        assertEquals(listOf(1, 2), deleted)
        assertFalse(result.allSucceeded)
        assertEquals(1, result.errors.size)
    }

    @Test
    fun `push failure still allows pull phase error merge to show both or push only`() {
        assertEquals("上行：墓碑不可改", combineSyncPhaseErrors("墓碑不可改", null))
        assertEquals("下行：网络错误", combineSyncPhaseErrors(null, "网络错误"))
        assertEquals(
            "上行：墓碑不可改；下行：网络错误",
            combineSyncPhaseErrors("墓碑不可改", "网络错误"),
        )
        assertNull(combineSyncPhaseErrors(null, null))
        // 语义：有上行错误时下行仍应执行——由 SyncEngine.cycle 保证；此处验证错误合并不互相覆盖
        assertTrue(combineSyncPhaseErrors("坏 outbox", null)!!.startsWith("上行："))
    }
}
