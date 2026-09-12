// 契约：docs/05 §3 今日测量同格取最新值、保留次数与血糖场景
package com.family.health.feature.overview

import com.family.health.data.MeasurementTimeBucket
import com.family.health.data.model.Measurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OverviewMeasureDataTest {
    private fun record(
        id: String,
        type: String,
        at: String,
        glucoseContext: String? = null,
        deleted: Boolean = false,
    ) = Measurement(
        id = id,
        type = type,
        measuredAt = at,
        glucoseContext = glucoseContext,
        glucoseMmol = if (type == "glucose") 6.1 else null,
        systolic = if (type == "bp") 130 else null,
        diastolic = if (type == "bp") 80 else null,
        createdBy = "家人",
        deleted = deleted,
    )

    @Test fun sameBucketUsesLatestRecordAndReportsCount() {
        val cells = todayMeasurementCells(
            listOf(
                record("early", "bp", "2026-09-10 06:30"),
                record("latest", "bp", "2026-09-10 08:15"),
                record("other", "glucose", "2026-09-10 07:00", "fasting"),
                record("deleted", "bp", "2026-09-10 07:30", deleted = true),
            ),
            "bp",
        )

        assertEquals("latest", cells[MeasurementTimeBucket.EarlyMorning]?.latest?.id)
        assertEquals(2, cells[MeasurementTimeBucket.EarlyMorning]?.count)
        assertFalse(cells.containsKey(MeasurementTimeBucket.Morning))
    }

    @Test fun glucoseSceneIsIndependentFromTimeBucket() {
        val cells = todayMeasurementCells(
            listOf(record("fasting-at-ten", "glucose", "2026-09-10 10:00", "fasting")),
            "glucose",
        )

        assertEquals("fasting-at-ten", cells[MeasurementTimeBucket.Morning]?.latest?.id)
        assertEquals("空腹", compactGlucoseScene(cells[MeasurementTimeBucket.Morning]?.latest?.glucoseContext))
    }
}
