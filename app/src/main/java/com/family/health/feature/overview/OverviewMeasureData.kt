// 契约：docs/05-页面结构与交互.md §3 今日测量按时间归格、同格显示最新值与次数
package com.family.health.feature.overview

import com.family.health.data.MeasurementTimeBucket
import com.family.health.data.model.Measurement

internal data class TodayMeasurementCell(
    val latest: Measurement,
    val count: Int,
)

internal fun todayMeasurementCells(
    records: List<Measurement>,
    type: String,
): Map<MeasurementTimeBucket, TodayMeasurementCell> = records
    .asSequence()
    .filter { it.type == type && !it.deleted }
    .mapNotNull { record ->
        MeasurementTimeBucket.fromTime(record.time)?.let { bucket -> bucket to record }
    }
    .groupBy({ it.first }, { it.second })
    .mapValues { (_, bucketRecords) ->
        TodayMeasurementCell(
            latest = bucketRecords.maxBy { it.measuredAt },
            count = bucketRecords.size,
        )
    }

internal fun compactGlucoseScene(context: String?): String = when (context) {
    "fasting" -> "空腹"
    "before_meal" -> "餐前"
    "after_meal_2h" -> "餐后"
    "bedtime" -> "睡前"
    "random" -> "随机"
    else -> ""
}
