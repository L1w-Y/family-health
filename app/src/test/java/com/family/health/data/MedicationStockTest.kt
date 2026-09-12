package com.family.health.data

import com.family.health.data.model.DailyMedItem
import com.family.health.data.model.doseTimesMatchSlots
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class MedicationStockTest {
    private val offset = ZoneOffset.ofHours(8)
    private fun ms(text: String) = LocalDateTime.parse(text).toInstant(offset).toEpochMilli()

    @Test
    fun `daily times must match selected slots`() {
        assertTrue(doseTimesMatchSlots(3, setOf("morning", "noon", "evening")))
        assertFalse(doseTimesMatchSlots(3, setOf("morning", "evening")))
    }

    @Test
    fun `three boxes are consumed independently at their cutoff times`() {
        val item = DailyMedItem(
            id = "daily", medicationItemId = "med",
            stockBySlot = mapOf("morning" to 12.0, "noon" to 12.0, "evening" to 12.0),
            stockCountedAtMs = ms("2026-09-09T08:00:00"), tzOffsetMin = 480,
        )

        val stock = projectedMedicationStock(item, doseQty = 4.0, nowMs = ms("2026-09-11T15:00:00"))

        assertEquals(0.0, stock.getValue("morning"), 0.0)
        assertEquals(4.0, stock.getValue("noon"), 0.0)
        assertEquals(4.0, stock.getValue("evening"), 0.0)
    }

    @Test
    fun `stock is not deducted until the slot cutoff has passed`() {
        val counted = ms("2026-09-11T08:00:00")
        assertEquals(4.0, projectedSlotStock(4.0, 4.0, "morning", counted, ms("2026-09-11T11:59:00"), 480), 0.0)
        assertEquals(0.0, projectedSlotStock(4.0, 4.0, "morning", counted, ms("2026-09-11T12:00:00"), 480), 0.0)
    }

    @Test
    fun `next unavailable occurrence follows all covered doses`() {
        val result = nextUnavailableMedicationAt("morning", 8.0, 4.0, ms("2026-09-11T15:00:00"), 480)
        assertEquals(ms("2026-09-14T11:59:00"), result)
    }
}
