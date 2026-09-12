// 契约：docs/05 §3 今日测量只按 measured_at 时间归格
package com.family.health.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeasurementTimeBucketTest {
    @Test fun boundariesCoverWholeDay() {
        assertEquals(MeasurementTimeBucket.Evening, MeasurementTimeBucket.fromTime("04:59"))
        assertEquals(MeasurementTimeBucket.EarlyMorning, MeasurementTimeBucket.fromTime("05:00"))
        assertEquals(MeasurementTimeBucket.EarlyMorning, MeasurementTimeBucket.fromTime("08:59"))
        assertEquals(MeasurementTimeBucket.Morning, MeasurementTimeBucket.fromTime("09:00"))
        assertEquals(MeasurementTimeBucket.Morning, MeasurementTimeBucket.fromTime("11:59"))
        assertEquals(MeasurementTimeBucket.Afternoon, MeasurementTimeBucket.fromTime("12:00"))
        assertEquals(MeasurementTimeBucket.Afternoon, MeasurementTimeBucket.fromTime("17:59"))
        assertEquals(MeasurementTimeBucket.Evening, MeasurementTimeBucket.fromTime("18:00"))
        assertEquals(MeasurementTimeBucket.Evening, MeasurementTimeBucket.fromTime("23:59"))
    }

    @Test fun orderAndLabelsUseOnlyTimeConcepts() {
        assertEquals(
            listOf("早晨", "上午", "下午", "晚上"),
            MeasurementTimeBucket.ALL.map { it.label },
        )
    }

    @Test fun malformedTimeDoesNotInventABucket() {
        assertNull(MeasurementTimeBucket.fromTime(""))
        assertNull(MeasurementTimeBucket.fromTime("25:00"))
    }
}
