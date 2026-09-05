// 契约：docs/05-页面结构与交互.md §4.1（指标匹配）/§5（历史用药分段）；prototype/app.js 同款逻辑
package com.family.health.data

import com.family.health.data.model.CheckupEvent
import com.family.health.data.model.MedicationItem
import com.family.health.data.model.Profile
import com.family.health.data.model.WatchItem

/** 指标历史数据点 */
data class IndicatorPoint(
    val date: String,
    val department: String,
    val valueNumeric: Double?,
    val valueText: String?,
    val unit: String,
) {
    val isNumeric: Boolean get() = valueNumeric != null
    val displayValue: String
        get() = valueNumeric?.let { v ->
            if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else v.toString()
        } ?: (valueText ?: "")
}

/**
 * 指标匹配：默认按报告原文名精确匹配；加入重点清单的指标按别名归并。
 * 契约：docs/02 §3.8 下钻规则、docs/05 §4.1
 */
fun indicatorPoints(member: Profile, name: String): Pair<List<IndicatorPoint>, WatchItem?> {
    val wl = member.watchlist.firstOrNull { it.canonicalName == name || name in it.aliases }
    val names = wl?.aliases ?: listOf(name)
    val pts = mutableListOf<IndicatorPoint>()
    member.events.forEach { e ->
        e.reports.forEach { r ->
            r.indicators.forEach { it ->
                if (it.itemName in names) {
                    pts.add(IndicatorPoint(e.checkupDate, e.department, it.valueNumeric, it.valueText, it.unit))
                }
            }
        }
    }
    pts.sortBy { it.date }
    return pts to wl
}

/** 历史用药分段 */
data class MedHistorySeg(
    val from: String,
    val to: String, // "至今" 或日期
    val active: List<MedicationItem>,
)

/** 契约：docs/05 §5——按变化节点分段，每段列出该阶段在用的药 */
fun medHistory(meds: List<MedicationItem>): List<MedHistorySeg> {
    val pts = meds.flatMap { listOfNotNull(it.startDate, it.endDate) }
        .distinct().sortedDescending()
    return pts.mapIndexed { i, from ->
        val segEnd = if (i == 0) "9999" else pts[i - 1]
        val active = meds.filter { m -> m.startDate < segEnd && (m.endDate == null || m.endDate!! > from) }
        MedHistorySeg(from, if (i == 0) "至今" else pts[i - 1], active)
    }.filter { it.active.isNotEmpty() }
}

/** 概览复查卡：最近的未来"下次复查日期"（契约：docs/05 §3） */
fun nextCheckup(member: Profile, today: String): CheckupEvent? =
    member.events
        .filter { it.nextCheckupDate != null && it.nextCheckupDate!! >= today }
        .minByOrNull { it.nextCheckupDate!! }
