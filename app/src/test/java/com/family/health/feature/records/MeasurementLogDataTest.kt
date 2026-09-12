// 契约：docs/05-页面结构与交互.md §4.2 测量段（粒度 日/周/月 + 按天分组 + 仅展示有数据日）
package com.family.health.feature.records

import com.family.health.data.MeasurementGranularity
import com.family.health.data.MeasurementWindow
import com.family.health.data.model.Measurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MeasurementLogDataTest {
    private val today = LocalDate.of(2026, 9, 11)
    private fun rec(id: String, at: String, type: String = "bp", scene: String? = null, deleted: Boolean = false) =
        Measurement(id = id, measuredAt = at, type = type, glucoseContext = scene, createdBy = "家人", deleted = deleted)
    private fun dayOf(w: MeasurementWindow) = w.anchor

    @Test fun dayViewReturnsOnlyAnchorDayAndCanBeEmpty() {
        val w = MeasurementWindow(MeasurementGranularity.Day, today)
        // 空周期：日视图返回该日（records=空），上层据此显示"暂无记录"
        val empty = measurementGroupsInWindow(emptyList(), "bp", w, today = today)
        assertEquals(1, empty.size)
        assertEquals(today, empty.single().date)
        assertTrue(empty.single().records.isEmpty())
        // 命中锚点
        val hit = measurementGroupsInWindow(listOf(rec("a", "2026-09-11 07:30")), "bp", w, today = today)
        assertEquals(1, hit.size); assertEquals(1, hit.single().records.size)
    }

    @Test fun weekViewSkipsEmptyDaysAndIsolatesScene() {
        val w = MeasurementWindow(MeasurementGranularity.Week, today) // 含 9/7—9/13
        val records = listOf(
            rec("m", "2026-09-09 07:00", "glucose", "fasting"),
            rec("n", "2026-09-09 19:00", "glucose", "bedtime"),
            rec("p", "2026-09-11 10:00", "bp"),
            rec("q", "2026-09-13 22:00", "glucose", "after_meal_2h"),  // 周日
        )
        val all = measurementGroupsInWindow(records, "glucose", w, today = today)
        // 9/9 含 2 条 glucose、9/13 含 1 条；9/11 只有 bp 被类型过滤掉；空日跳过
        assertEquals(listOf(LocalDate.of(2026,9,13), LocalDate.of(2026,9,9)), all.map { it.date })
        // 场景筛选：仅 9/9 的 fasting（bedtime/after_meal_2h 被排）
        val fasting = measurementGroupsInWindow(records, "glucose", w, "fasting", today)
        assertEquals(1, fasting.size)
        assertEquals(listOf("m"), fasting.single().records.map { it.id })
        // 同一天多条按时间升序
        assertEquals(listOf("m", "n"), all[1].records.map { it.id })
    }

    @Test fun monthViewSkipsEmptyDaysAndIsolatesType() {
        val w = MeasurementWindow(MeasurementGranularity.Month, today) // 2026-09
        val records = listOf(
            rec("a", "2026-08-15 09:00"), rec("b", "2026-09-01 08:00"),
            rec("c", "2026-09-11 07:30"), rec("d", "2026-09-11 21:00"),
            rec("e", "2026-09-30 23:59", deleted = true),
        )
        val r = measurementGroupsInWindow(records, "bp", w, today = today)
        // 8 月被月份边界排除，9/30 软删被排除，剩 9/1 与 9/11（9/11 含 2 条）
        assertEquals(listOf(LocalDate.of(2026,9,11), LocalDate.of(2026,9,1)), r.map { it.date })
        assertEquals(listOf("c", "d"), r[0].records.map { it.id })
        assertEquals(listOf("b"), r[1].records.map { it.id })
    }

    @Test fun emptyPeriodProducesEmpty() {
        val w = MeasurementWindow(MeasurementGranularity.Month, today)
        assertTrue(measurementGroupsInWindow(emptyList(), "bp", w, today = today).isEmpty())
    }
}
