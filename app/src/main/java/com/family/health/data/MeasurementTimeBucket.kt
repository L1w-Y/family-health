// 契约：docs/05-页面结构与交互.md §3 今日测量按 measured_at 时间分段
package com.family.health.data

/**
 * 概览使用的展示时间段。它是 measured_at 的派生值，不写入数据库，避免录入来源之间产生两套分类。
 */
enum class MeasurementTimeBucket(val label: String) {
    EarlyMorning("早晨"),
    Morning("上午"),
    Afternoon("下午"),
    Evening("晚上"),
    ;

    companion object {
        val ALL: List<MeasurementTimeBucket> = entries.toList()

        fun fromTime(time: String): MeasurementTimeBucket? {
            val hour = time.substringBefore(":").toIntOrNull()?.takeIf { it in 0..23 } ?: return null
            return when (hour) {
                in 5..8 -> EarlyMorning
                in 9..11 -> Morning
                in 12..17 -> Afternoon
                else -> Evening
            }
        }
    }
}
