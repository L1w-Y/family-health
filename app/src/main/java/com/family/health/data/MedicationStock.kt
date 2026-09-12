// 契约：docs/02-数据库与同步.md §3.15；docs/05-页面结构与交互.md §3 今日用药
package com.family.health.data

import com.family.health.data.model.DailyMedItem
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.floor
import kotlin.math.max

val MEDICATION_SLOT_CUTOFFS: Map<String, LocalTime> = linkedMapOf(
    "morning" to LocalTime.of(11, 59),
    "noon" to LocalTime.of(17, 30),
    "evening" to LocalTime.of(20, 30),
    "bedtime" to LocalTime.of(23, 59),
)

/** 从实际盘点量扣除盘点后已经越过截止时刻的理论服药次数。 */
fun projectedMedicationStock(
    item: DailyMedItem,
    doseQty: Double,
    nowMs: Long = System.currentTimeMillis(),
): Map<String, Double> = item.stockBySlot.mapValues { (slot, quantity) ->
    projectedSlotStock(quantity, doseQty, slot, item.stockCountedAtMs, nowMs, item.tzOffsetMin)
}

fun projectedSlotStock(
    countedQuantity: Double,
    doseQty: Double,
    slot: String,
    countedAtMs: Long,
    nowMs: Long,
    tzOffsetMin: Int,
): Double {
    if (doseQty <= 0 || nowMs <= countedAtMs) return max(0.0, countedQuantity)
    val cutoff = MEDICATION_SLOT_CUTOFFS[slot] ?: return max(0.0, countedQuantity)
    val offset = ZoneOffset.ofTotalSeconds(tzOffsetMin * 60)
    val countedAt = Instant.ofEpochMilli(countedAtMs).atOffset(offset).toLocalDateTime()
    val now = Instant.ofEpochMilli(nowMs).atOffset(offset).toLocalDateTime()
    var date = countedAt.toLocalDate()
    var occurrences = 0
    while (!date.isAfter(now.toLocalDate())) {
        val occurrence = LocalDateTime.of(date, cutoff)
        if (occurrence.isAfter(countedAt) && occurrence.isBefore(now)) occurrences++
        date = date.plusDays(1)
    }
    return max(0.0, countedQuantity - occurrences * doseQty)
}

/** 当前余量能覆盖若干次该时段后，第一个无法覆盖的截止时刻。 */
fun nextUnavailableMedicationAt(
    slot: String,
    remainingQuantity: Double,
    doseQty: Double,
    nowMs: Long,
    tzOffsetMin: Int,
): Long? {
    val cutoff = MEDICATION_SLOT_CUTOFFS[slot] ?: return null
    if (doseQty <= 0) return null
    val coveredOccurrences = floor((remainingQuantity.coerceAtLeast(0.0) + 1e-9) / doseQty).toInt()
    val offset = ZoneOffset.ofTotalSeconds(tzOffsetMin * 60)
    val now = Instant.ofEpochMilli(nowMs).atOffset(offset).toLocalDateTime()
    var date: LocalDate = now.toLocalDate()
    var occurrence = LocalDateTime.of(date, cutoff)
    if (occurrence.isBefore(now)) occurrence = LocalDateTime.of(date.plusDays(1), cutoff)
    repeat(coveredOccurrences) { occurrence = occurrence.plusDays(1) }
    return occurrence.toInstant(offset).toEpochMilli()
}
