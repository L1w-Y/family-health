// 契约：docs/02 §1（纯日历日期用 ISO YYYY-MM-DD）
package com.family.health.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

fun todayStr(): String = LocalDate.now().format(DATE_FMT)

fun nowStr(): String = LocalDateTime.now().format(TIME_FMT)

/** 目标日期相对今天的天数差（未来为正） */
fun daysTo(date: String): Long =
    ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(date, DATE_FMT))

/** "2026-09-05" -> "09/05"（原型 mmdd） */
fun mmdd(date: String): String = date.substring(5).replace('-', '/')

/** "2026-09" -> "2026 年 9 月" */
fun monthLabel(month: String): String {
    val y = month.substring(0, 4)
    val m = month.substring(5, 7).toInt()
    return "$y 年 $m 月"
}

/** 月份偏移：shiftMonth("2026-09", -1) -> "2026-08" */
fun shiftMonth(month: String, delta: Int): String =
    LocalDate.parse("$month-01", DATE_FMT).plusMonths(delta.toLong()).format(DATE_FMT).substring(0, 7)

/** 星期展示："2026-09-05" -> "周六" */
fun weekdayCn(date: String): String {
    val dow = LocalDate.parse(date, DATE_FMT).dayOfWeek.value // 1=周一
    return "周" + "一二三四五六日"[dow - 1]
}

fun Double.trimmed(): String =
    if (this == Math.floor(this) && !this.isInfinite()) this.toLong().toString() else this.toString()

// ---------- epoch ms ↔ 展示串（设备本地时区；契约 02 §1：ms + tz_offset_min） ----------

private val DT_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** 设备当前时区偏移（分钟，如 +480） */
fun deviceTzOffsetMin(): Int =
    (java.time.ZoneId.systemDefault().rules.getOffset(java.time.Instant.now()).totalSeconds / 60)

fun msToDateTime(ms: Long): String =
    LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), java.time.ZoneId.systemDefault()).format(DT_FMT)

fun dateTimeToMs(text: String): Long =
    LocalDateTime.parse(text, DT_FMT).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

fun msToDate(ms: Long): String = msToDateTime(ms).substring(0, 10)

/** "MM-dd HH:mm"（便签展示，原型同款；按当前年解析） */
fun msToMmDdHm(ms: Long): String {
    val dt = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), java.time.ZoneId.systemDefault())
    return "%02d-%02d %02d:%02d".format(dt.monthValue, dt.dayOfMonth, dt.hour, dt.minute)
}

/** "MM-dd HH:mm"（补当前年）→ ms；解析失败返回 null */
fun mmDdHmToMs(text: String): Long? = runCatching {
    val year = LocalDate.now().year
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    LocalDateTime.parse("$year-$text", fmt).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrNull()
