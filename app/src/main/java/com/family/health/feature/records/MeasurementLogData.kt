// 契约：docs/05-页面结构与交互.md §4.2 测量段（日/周/月 周期 + 按天分组 + 仅展示有数据日）
package com.family.health.feature.records

import com.family.health.data.MeasurementWindow
import com.family.health.data.model.Measurement
import java.time.LocalDate

internal data class MeasurementDay(val date: LocalDate, val records: List<Measurement>)

/**
 * 周期内的按天分组：日视图返回该日（可能为空，方便上层显示"暂无记录"），周/月视图
 * 只列出有记录的日期，倒序。月视图空周期返回空列表，配合上层空态文案。
 */
internal fun measurementGroupsInWindow(
    measurements: List<Measurement>,
    type: String,
    window: MeasurementWindow,
    scene: String? = null,
    today: LocalDate = LocalDate.now(),
): List<MeasurementDay> {
    val start = window.start.toString()
    val end = window.end.toString()
    val filtered = measurements.filter {
        it.type == type && !it.deleted && it.date in start..end &&
            (type == "bp" || scene == null || it.glucoseContext == scene)
    }.groupBy { it.date }

    val days = when (window.granularity) {
        com.family.health.data.MeasurementGranularity.Day -> listOf(window.anchor)
        else -> (0..java.time.temporal.ChronoUnit.DAYS.between(window.start, window.end))
            .map { offset -> window.start.plusDays(offset) }
    }

    return days.mapNotNull { d ->
        val key = d.toString()
        val recs = filtered[key].orEmpty().sortedBy { it.measuredAt }
        // 日视图允许空（上层显示"暂无记录"）；周/月视图跳过无数据日，避免空行铺满
        if (window.granularity == com.family.health.data.MeasurementGranularity.Day) {
            MeasurementDay(d, recs)
        } else if (recs.isEmpty()) null else MeasurementDay(d, recs)
    }.sortedByDescending { it.date }
}
