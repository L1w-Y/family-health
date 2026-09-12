// 契约：docs/02 §1 测量时刻用 epoch ms + 原始时区偏移还原
package com.family.health.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class DatesTest {
    @Test fun savedOffsetRestoresOriginalLocalDateAndTime() {
        val instant = Instant.parse("2026-09-10T16:30:00Z").toEpochMilli()
        assertEquals("2026-09-11 00:30", msToDateTime(instant, 480))
        assertEquals("2026-09-10 11:30", msToDateTime(instant, -300))
    }
}
