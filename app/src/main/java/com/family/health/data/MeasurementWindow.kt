// 契约：docs/05-页面结构与交互.md §4.2 测量段（粒度 + 周期导航）
package com.family.health.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 测量段粒度：日 / 周 / 月。
 * 单一时间导航体系，取代旧的"近 N 天"快捷 + "月份 tab" + "日期区间"三类并存的控件。
 */
enum class MeasurementGranularity { Day, Week, Month }

/**
 * 测量段当前周期：以锚点日期 + 粒度计算一个起止区间。
 * - Day  → [anchor, anchor]
 * - Week → [anchor 所在周的周一, 周日]（以周一为周首，贴近国内日历习惯）
 * - Month → [anchor 所在月 1 日, 月末]
 */
data class MeasurementWindow(val granularity: MeasurementGranularity, val anchor: LocalDate) {
    val start: LocalDate
        get() = when (granularity) {
            MeasurementGranularity.Day -> anchor
            MeasurementGranularity.Week -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            MeasurementGranularity.Month -> anchor.withDayOfMonth(1)
        }
    val end: LocalDate
        get() = when (granularity) {
            MeasurementGranularity.Day -> anchor
            MeasurementGranularity.Week -> start.plusDays(6)
            MeasurementGranularity.Month -> anchor.with(TemporalAdjusters.lastDayOfMonth())
        }

    /** 周期是否包含今天（含锚点边界）。用于"回到今天"显隐与右箭头置灰。 */
    fun contains(today: LocalDate): Boolean = !today.isBefore(start) && !today.isAfter(end)
}

/** 翻页：delta=+1 向后一周期，-1 向前一周期；锚点被钳到 ≤ today。 */
fun shiftWindow(w: MeasurementWindow, delta: Int, today: LocalDate): MeasurementWindow {
    val a = w.anchor
    val moved = when (w.granularity) {
        MeasurementGranularity.Day -> a.plusDays(delta.toLong())
        MeasurementGranularity.Week -> a.plusWeeks(delta.toLong())
        MeasurementGranularity.Month -> a.plusMonths(delta.toLong())
    }.coerceAtMost(today)
    return MeasurementWindow(w.granularity, moved)
}

/** 周期标题（与截图口径一致：日=9月11日 周五、周=9月7日—9月13日、月=2026年9月） */
fun formatWindowTitle(w: MeasurementWindow, today: LocalDate): String = when (w.granularity) {
    MeasurementGranularity.Day -> "${w.anchor.monthValue}月${w.anchor.dayOfMonth}日 ${weekdayCn(w.anchor)}"
    MeasurementGranularity.Week -> {
        val s = w.start
        val e = w.end
        if (s.monthValue == e.monthValue) "${s.monthValue}月${s.dayOfMonth}日—${e.dayOfMonth}日"
        else "${s.monthValue}月${s.dayOfMonth}日—${e.monthValue}月${e.dayOfMonth}日"
    }
    MeasurementGranularity.Month -> "${w.anchor.year}年${w.anchor.monthValue}月"
}

/** 简化的中文星期（避免引入新 util） */
private fun weekdayCn(d: LocalDate): String = when (d.dayOfWeek) {
    DayOfWeek.MONDAY -> "周一"; DayOfWeek.TUESDAY -> "周二"; DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"; DayOfWeek.FRIDAY -> "周五"; DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
}
