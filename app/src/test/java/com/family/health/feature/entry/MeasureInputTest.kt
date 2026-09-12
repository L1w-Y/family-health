// 契约：docs/03 §4 测量值限制；docs/05 §8 血糖小数输入
package com.family.health.feature.entry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasureInputTest {
    @Test fun decimalInputKeepsOneDotAndTwoDecimalPlaces() {
        assertEquals("6.1", sanitizeDecimalInput("6.1"))
        assertEquals("7.25", sanitizeDecimalInput("7.256"))
        assertEquals("6.12", sanitizeDecimalInput("6..12"))
        assertEquals("123.45", sanitizeDecimalInput("1234.456"))
    }

    @Test fun structuralRangesMatchImportContract() {
        assertTrue(validGlucose(0.5))
        assertTrue(validGlucose(40.0))
        assertFalse(validGlucose(0.49))
        assertFalse(validGlucose(40.01))
        assertTrue(validBloodPressure(40))
        assertTrue(validBloodPressure(300))
        assertFalse(validBloodPressure(301))
        assertTrue(validHeartRate(20))
        assertFalse(validHeartRate(251))
    }
}
