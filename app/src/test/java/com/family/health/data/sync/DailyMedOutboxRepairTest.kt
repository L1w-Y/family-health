package com.family.health.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyMedOutboxRepairTest {
    @Test
    fun `drops corrupt insert update and optimistic local rows`() {
        val plan = planCorruptDailyMedRepair(
            localRows = listOf(
                DailyMedLocalRow(id = "med-1", medicationItemId = "med-1", seq = 0),
                DailyMedLocalRow(id = "daily-ok", medicationItemId = "med-2", seq = 0),
                DailyMedLocalRow(id = "med-synced", medicationItemId = "med-synced", seq = 99),
            ),
            outbox = listOf(
                OutboxScanRow(
                    id = 1,
                    tableName = "daily_med_items",
                    op = "insert",
                    rowJson = """{"id":"med-1","medication_item_id":"med-1","stock_by_slot":{}}""",
                ),
                OutboxScanRow(
                    id = 2,
                    tableName = "daily_med_items",
                    op = "delete",
                    rowJson = """{"id":"med-1"}""",
                ),
                OutboxScanRow(
                    id = 3,
                    tableName = "measurements",
                    op = "insert",
                    rowJson = """{"id":"m1"}""",
                ),
                OutboxScanRow(
                    id = 4,
                    tableName = "daily_med_items",
                    op = "update",
                    rowJson = """{"id":"daily-ok","medication_item_id":"med-2"}""",
                ),
            ),
        )
        assertEquals(listOf("med-1"), plan.localIdsToDelete)
        assertTrue(plan.outboxIdsToDelete.containsAll(listOf(1L, 2L)))
        assertFalseContains(plan.outboxIdsToDelete, 3L, 4L)
    }

    private fun assertFalseContains(ids: List<Long>, vararg forbidden: Long) {
        forbidden.forEach { assertTrue("$it should remain", it !in ids) }
    }
}
