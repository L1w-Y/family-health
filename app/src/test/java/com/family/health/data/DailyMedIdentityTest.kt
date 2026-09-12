package com.family.health.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyMedIdentityTest {
    @Test
    fun `new execution id differs from medication item id`() {
        val medId = "med-1"
        val dailyId = newDailyMedExecutionId()
        assertNotEquals(medId, dailyId)
        assertTrue(dailyId.isNotBlank())
    }

    @Test
    fun `resolve reuses active execution row`() {
        assertEquals(
            "daily-existing",
            resolveDailyMedRowId("daily-existing", "proposed-new", "med-1"),
        )
    }

    @Test
    fun `resolve rejects proposed id that reuses medication id`() {
        val resolved = resolveDailyMedRowId(null, "med-1", "med-1")
        assertNotEquals("med-1", resolved)
        assertFalse(isCorruptDailyMedIdentity(resolved, "med-1"))
    }

    @Test
    fun `resolve accepts independent proposed id`() {
        assertEquals(
            "daily-new",
            resolveDailyMedRowId(null, "daily-new", "med-1"),
        )
    }
}
