// 契约：docs/02-数据库与同步.md §3（表结构）；术语见 AGENTS.md §5
package com.family.health.data.model

/** 设备（设备即身份）。契约 §3.2 */
data class Device(
    val id: String,
    val displayName: String,
    val type: String = "member", // member | api
    val self: Boolean = false,
)

/** 指标项目（原文名存档，不归一）。契约 §3.7 */
data class IndicatorItem(
    val itemName: String,
    val valueNumeric: Double? = null,
    val valueText: String? = null,
    val unit: String = "",
    val referenceRange: String = "",
) {
    val isNumeric: Boolean get() = valueNumeric != null
    val displayValue: String
        get() = valueNumeric?.let { v ->
            if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else v.toString()
        } ?: (valueText ?: "")
}

/** 检查报告。契约 §3.6 */
data class Report(
    val title: String,
    val reportDate: String,
    val attachments: Int = 0,
    val conclusionText: String = "",
    val indicators: List<IndicatorItem> = emptyList(),
)

/** 复查事件。契约 §3.5；medChangeSummary 为本次用药变化的展示摘要（原型同款） */
data class CheckupEvent(
    val id: String,
    val checkupDate: String,
    val hospital: String = "",
    val department: String = "",
    val note: String = "",
    val nextCheckupDate: String? = null,
    val medChangeSummary: String = "",
    val reports: List<Report> = emptyList(),
    val createdBy: String = "爸爸",
)

/** 用药条目（档案层方案）。契约 §3.9 */
data class MedicationItem(
    val id: String,
    val name: String,
    val dosageText: String,
    val doseSlots: List<String> = emptyList(), // morning/noon/evening/bedtime
    val medKind: String = "western", // western | tcm
    val category: String = "long_term", // long_term | temporary
    val startDate: String,
    val endDate: String? = null, // null = 进行中
    val supersedesId: String? = null,
    val changeId: String? = null,
)

/** 用药变化（调整批次，事由只在这里）。契约 §3.10 */
data class MedChange(
    val id: String,
    val effectiveDate: String,
    val note: String = "",
    val linkedEventId: String? = null,
)

/** 今日用药条目（执行层，与方案无外键关联）。契约 §3.15 */
data class DailyMedItem(
    val id: String,
    val name: String,
    val isTcm: Boolean = false,
    val doseText: String = "",
    val doseSlots: List<String> = emptyList(),
    val stockQty: Double? = null,
    val stockUnit: String = "片",
    val dailyQty: Double? = null,
    val tcmPacks: Double? = null,
    val tcmDaysPerPack: Int = 1,
    val tcmUsedDays: Int = 0,
) {
    /** 余量 ÷ 每日用量，纯算术展示（契约 §3.15） */
    val daysLeft: Int?
        get() = if (stockQty != null && dailyQty != null && dailyQty!! > 0) {
            kotlin.math.floor(stockQty!! / dailyQty!!).toInt()
        } else null
}

/** 测量记录（追加型；心率是血压附属项，不单列类型）。契约 §3.4 */
data class Measurement(
    val id: String,
    val type: String, // bp | glucose
    val measuredAt: String, // "yyyy-MM-dd HH:mm"
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val heartRateBpm: Int? = null,
    val glucoseMmol: Double? = null,
    val glucoseContext: String? = null, // fasting/before_meal/after_meal_2h/bedtime/random
    val note: String = "",
    val createdBy: String,
    val deleted: Boolean = false, // 软删除（契约 §1 决策）
) {
    val date: String get() = measuredAt.substring(0, 10)
    val time: String get() = measuredAt.substring(11)
}

/** 便签（多条并存，remindAt 自驱动提醒）。契约 §3.14 */
data class Note(
    val id: String,
    val text: String,
    val done: Boolean = false,
    val remindAt: String? = null, // 展示格式 "MM-dd HH:mm"（原型同款）
    val remindTargetName: String? = null, // null = 全家设备
    val createdBy: String,
    val createdAtLabel: String,
)

/** 重点指标清单（别名归并）。契约 §3.8 */
data class WatchItem(
    val canonicalName: String,
    val aliases: List<String>,
    val canonicalUnit: String = "",
)

/** 提醒设置（档案级时刻表）。契约 §3.11 */
data class ReminderSetting(
    val medTimes: List<String> = emptyList(),
    val measureTimes: List<String> = emptyList(),
    val advanceDays: List<Int> = listOf(1),
)

/** 成员档案。契约 §3.3 */
data class Profile(
    val id: String,
    val name: String,
    val relation: String = "",
    val gender: String = "unknown", // male | female | unknown
    val birthDate: String = "",
    val profileNote: String = "",
    val meds: List<MedicationItem> = emptyList(),
    val changes: List<MedChange> = emptyList(),
    val daily: List<DailyMedItem> = emptyList(),
    val events: List<CheckupEvent> = emptyList(),
    val measurements: List<Measurement> = emptyList(),
    val notes: List<Note> = emptyList(),
    val watchlist: List<WatchItem> = emptyList(),
)

val Profile.genderLabel: String
    get() = when (gender) {
        "male" -> "男"
        "female" -> "女"
        else -> "未知"
    }

/** 时段与血糖场景的展示名（契约：docs/05 §4/§8） */
object Labels {
    val SLOTS: List<Pair<String, String>> = listOf(
        "morning" to "早", "noon" to "中", "evening" to "晚", "bedtime" to "睡前",
    )
    val GLUCOSE_SCENES: List<Pair<String, String>> = listOf(
        "fasting" to "空腹", "before_meal" to "餐前", "after_meal_2h" to "餐后",
        "bedtime" to "睡前", "random" to "随机",
    )
    val GLUCOSE_CTX_FULL: Map<String, String> = mapOf(
        "fasting" to "空腹", "before_meal" to "餐前", "after_meal_2h" to "餐后2h",
        "bedtime" to "睡前", "random" to "随机",
    )

    fun slotName(key: String): String = SLOTS.firstOrNull { it.first == key }?.second ?: key
    fun sceneName(key: String?): String = GLUCOSE_CTX_FULL[key] ?: ""
    fun measureTypeName(type: String): String = when (type) {
        "bp" -> "血压"; "glucose" -> "血糖"; else -> "心率"
    }
}
