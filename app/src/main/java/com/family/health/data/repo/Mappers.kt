// 映射：服务器行 JsonObject（snake_case 全字段） ↔ Room 实体。契约：docs/02 §2 通用列、§3 业务列
package com.family.health.data.repo

import com.family.health.data.db.AttachmentEntity
import com.family.health.data.db.CheckupEventEntity
import com.family.health.data.db.DailyMedItemEntity
import com.family.health.data.db.DeviceEntity
import com.family.health.data.db.IndicatorItemEntity
import com.family.health.data.db.MedChangeEntity
import com.family.health.data.db.MeasurementEntity
import com.family.health.data.db.MedicationItemEntity
import com.family.health.data.db.NoteEntity
import com.family.health.data.db.ProfileEntity
import com.family.health.data.db.ReminderEntity
import com.family.health.data.db.ReportEntity
import com.family.health.data.db.WatchItemEntity
import com.family.health.syncclient.ChangeRow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

// ---------- 取值辅助 ----------
private fun JsonObject.str(key: String): String = this[key]?.jsonPrimitive?.contentOrNull ?: ""
private fun JsonObject.strOrNull(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
private fun JsonObject.longOr(key: String, def: Long = 0L): Long = this[key]?.jsonPrimitive?.longOrNull ?: def
private fun JsonObject.longOrNull(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull
private fun JsonObject.intOrNull(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
private fun JsonObject.dblOrNull(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull
private fun JsonObject.boolOr(key: String, def: Boolean = false): Boolean =
    this[key]?.jsonPrimitive?.booleanOrNull ?: def

private fun JsonObject.rawOrNull(key: String): String? = this[key]?.toString()

/** 下行行 → 对应表实体（deleted/seq 随业务列一同覆盖） */
fun ChangeRow.toEntity(): Any = when (table) {
    "devices" -> DeviceEntity(
        id = row.str("id"), displayName = row.str("display_name"),
        type = row.str("type"), deleted = row.boolOr("deleted"), seq = row.longOr("seq"),
    )
    "profiles" -> ProfileEntity(
        id = row.str("id"), name = row.str("name"), relation = row.str("relation"),
        gender = row.str("gender"), birthDate = row.strOrNull("birth_date"),
        notes = row.str("notes"), deleted = row.boolOr("deleted"), seq = row.longOr("seq"),
    )
    "measurements" -> MeasurementEntity(
        id = row.str("id"), profileId = row.str("profile_id"), type = row.str("type"),
        measuredAt = row.longOr("measured_at"), tzOffsetMin = row.intOrNull("tz_offset_min") ?: 0,
        systolic = row.intOrNull("systolic"), diastolic = row.intOrNull("diastolic"),
        heartRateBpm = row.intOrNull("heart_rate_bpm"),
        glucoseMmol = row.dblOrNull("glucose_mmol"), glucoseContext = row.strOrNull("glucose_context"),
        note = row.strOrNull("note"), payloadJson = row.rawOrNull("payload"),
        createdBy = row.str("created_by"),
        deleted = row.boolOr("deleted"), seq = row.longOr("seq"),
    )
    "checkup_events" -> CheckupEventEntity(
        id = row.str("id"), profileId = row.str("profile_id"), checkupDate = row.str("checkup_date"),
        hospital = row.strOrNull("hospital"), department = row.strOrNull("department"),
        note = row.strOrNull("note"), nextCheckupDate = row.strOrNull("next_checkup_date"),
        medicationChangesNote = row.strOrNull("medication_changes_note"),
        createdBy = row.str("created_by"), deleted = row.boolOr("deleted"), seq = row.longOr("seq"),
    )
    "reports" -> ReportEntity(
        id = row.str("id"), eventId = row.str("event_id"), title = row.str("title"),
        reportDate = row.str("report_date"), conclusionText = row.strOrNull("conclusion_text"),
        deleted = row.boolOr("deleted"), seq = row.longOr("seq"),
    )
    "indicator_items" -> IndicatorItemEntity(
        id = row.str("id"), reportId = row.str("report_id"), itemName = row.str("item_name"),
        valueNumeric = row.dblOrNull("value_numeric"), valueText = row.strOrNull("value_text"),
        unit = row.strOrNull("unit"), referenceRange = row.strOrNull("reference_range"),
        sortOrder = row.intOrNull("sort_order") ?: 0, canonicalName = row.strOrNull("canonical_name"),
        deleted = row.boolOr("deleted"), seq = row.longOr("seq"),
    )
    "watch_items" -> WatchItemEntity(
        id = row.str("id"), profileId = row.str("profile_id"), canonicalName = row.str("canonical_name"),
        aliasesJson = row.rawOrNull("aliases") ?: "[]", canonicalUnit = row.strOrNull("canonical_unit"),
        sortOrder = row.intOrNull("sort_order") ?: 0, deleted = row.boolOr("deleted"), seq = row.longOr("seq"),
    )
    "medication_items" -> MedicationItemEntity(
        id = row.str("id"), profileId = row.str("profile_id"), category = row.str("category"),
        medKind = row.str("med_kind"), name = row.str("name"), dosageText = row.str("dosage_text"),
        doseQty = row.dblOrNull("dose_qty"), doseUnit = row.strOrNull("dose_unit"),
        doseTimesPerDay = row.intOrNull("dose_times_per_day"),
        doseSlotsJson = row.rawOrNull("dose_slots"), startDate = row.str("start_date"),
        endDate = row.strOrNull("end_date"), supersedesId = row.strOrNull("supersedes_id"),
        changeId = row.strOrNull("change_id"), deleted = row.boolOr("deleted"), seq = row.longOr("seq"),
    )
    "med_changes" -> MedChangeEntity(
        id = row.str("id"), profileId = row.str("profile_id"), effectiveDate = row.str("effective_date"),
        note = row.strOrNull("note"), linkedEventId = row.strOrNull("linked_event_id"),
        deleted = row.boolOr("deleted"), seq = row.longOr("seq"),
    )
    "reminders" -> ReminderEntity(
        id = row.str("id"), profileId = row.str("profile_id"), type = row.str("type"),
        timesJson = row.rawOrNull("times"), measureType = row.strOrNull("measure_type"),
        advanceDaysJson = row.rawOrNull("advance_days"), enabled = row.boolOr("enabled", true),
        deleted = row.boolOr("deleted"), seq = row.longOr("seq"),
    )
    "attachments" -> AttachmentEntity(
        id = row.str("id"), reportId = row.strOrNull("report_id"), fileKey = row.str("file_key"),
        mime = row.strOrNull("mime"), sizeBytes = row.longOrNull("size_bytes"),
        uploadState = row.str("upload_state"), deleted = row.boolOr("deleted"), seq = row.longOr("seq"),
    )
    "notes" -> NoteEntity(
        id = row.str("id"), profileId = row.str("profile_id"), text = row.str("text"),
        done = row.boolOr("done"), remindAt = row.longOrNull("remind_at"),
        tzOffsetMin = row.intOrNull("tz_offset_min"), remindTargetsJson = row.rawOrNull("remind_targets"),
        createdBy = row.str("created_by"), createdAtMs = row.longOr("created_at"),
        deleted = row.boolOr("deleted"), seq = row.longOr("seq"),
    )
    "daily_med_items" -> DailyMedItemEntity(
        id = row.str("id"), profileId = row.str("profile_id"),
        medicationItemId = row.str("medication_item_id"),
        stockBySlotJson = row.rawOrNull("stock_by_slot") ?: "{}",
        stockCountedAtMs = row.longOr("stock_counted_at"),
        tzOffsetMin = row.intOrNull("tz_offset_min") ?: 0,
        deleted = row.boolOr("deleted"), seq = row.longOr("seq"),
    )
    else -> throw IllegalArgumentException("未知同步表：$table")
}

// ---------- 实体 → 上行业务行（仅 id + 业务列；通用列由服务端赋值） ----------

private fun putIfNotNull(builder: kotlinx.serialization.json.JsonObjectBuilder, key: String, v: Any?) {
    if (v == null) return
    when (v) {
        is String -> builder.put(key, JsonPrimitive(v))
        is Int -> builder.put(key, JsonPrimitive(v))
        is Long -> builder.put(key, JsonPrimitive(v))
        is Double -> builder.put(key, JsonPrimitive(v))
        is Boolean -> builder.put(key, JsonPrimitive(v))
    }
}

private fun putRaw(builder: kotlinx.serialization.json.JsonObjectBuilder, key: String, rawJson: String?) {
    if (rawJson == null) return
    runCatching {
        builder.put(key, kotlinx.serialization.json.Json.parseToJsonElement(rawJson))
    }
}

fun profileToRow(e: ProfileEntity): JsonObject = kotlinx.serialization.json.buildJsonObject {
    put("id", e.id); put("name", e.name); put("relation", e.relation)
    put("gender", e.gender); putIfNotNull(this, "birth_date", e.birthDate); put("notes", e.notes)
}

fun measurementToRow(e: MeasurementEntity): JsonObject = kotlinx.serialization.json.buildJsonObject {
    put("id", e.id); put("profile_id", e.profileId); put("type", e.type)
    put("measured_at", e.measuredAt); put("tz_offset_min", e.tzOffsetMin)
    putIfNotNull(this, "systolic", e.systolic); putIfNotNull(this, "diastolic", e.diastolic)
    putIfNotNull(this, "heart_rate_bpm", e.heartRateBpm)
    putIfNotNull(this, "glucose_mmol", e.glucoseMmol); putIfNotNull(this, "glucose_context", e.glucoseContext)
    putIfNotNull(this, "note", e.note)
    putRaw(this, "payload", e.payloadJson)
}

fun medicationItemToRow(e: MedicationItemEntity): JsonObject = kotlinx.serialization.json.buildJsonObject {
    put("id", e.id); put("profile_id", e.profileId); put("category", e.category)
    put("med_kind", e.medKind); put("name", e.name); put("dosage_text", e.dosageText)
    putIfNotNull(this, "dose_qty", e.doseQty); putIfNotNull(this, "dose_unit", e.doseUnit)
    putIfNotNull(this, "dose_times_per_day", e.doseTimesPerDay)
    putRaw(this, "dose_slots", e.doseSlotsJson)
    put("start_date", e.startDate)
    putIfNotNull(this, "end_date", e.endDate); putIfNotNull(this, "supersedes_id", e.supersedesId)
    putIfNotNull(this, "change_id", e.changeId)
}

fun checkupEventToRow(e: CheckupEventEntity): JsonObject = kotlinx.serialization.json.buildJsonObject {
    put("id", e.id); put("profile_id", e.profileId); put("checkup_date", e.checkupDate)
    putIfNotNull(this, "hospital", e.hospital); putIfNotNull(this, "department", e.department)
    putIfNotNull(this, "note", e.note); putIfNotNull(this, "next_checkup_date", e.nextCheckupDate)
    putIfNotNull(this, "medication_changes_note", e.medicationChangesNote)
}

fun medChangeToRow(e: MedChangeEntity): JsonObject = kotlinx.serialization.json.buildJsonObject {
    put("id", e.id); put("profile_id", e.profileId); put("effective_date", e.effectiveDate)
    putIfNotNull(this, "note", e.note); putIfNotNull(this, "linked_event_id", e.linkedEventId)
}

fun reminderToRow(e: ReminderEntity): JsonObject = kotlinx.serialization.json.buildJsonObject {
    put("id", e.id); put("profile_id", e.profileId); put("type", e.type)
    putRaw(this, "times", e.timesJson); putIfNotNull(this, "measure_type", e.measureType)
    putRaw(this, "advance_days", e.advanceDaysJson); put("enabled", e.enabled)
}

fun watchItemToRow(e: WatchItemEntity): JsonObject = kotlinx.serialization.json.buildJsonObject {
    put("id", e.id); put("profile_id", e.profileId); put("canonical_name", e.canonicalName)
    putRaw(this, "aliases", e.aliasesJson)
    putIfNotNull(this, "canonical_unit", e.canonicalUnit); put("sort_order", e.sortOrder)
}

fun noteToRow(e: NoteEntity): JsonObject = kotlinx.serialization.json.buildJsonObject {
    put("id", e.id); put("profile_id", e.profileId); put("text", e.text); put("done", e.done)
    putIfNotNull(this, "remind_at", e.remindAt); putIfNotNull(this, "tz_offset_min", e.tzOffsetMin)
    putRaw(this, "remind_targets", e.remindTargetsJson)
}

fun dailyMedItemToRow(e: DailyMedItemEntity): JsonObject = kotlinx.serialization.json.buildJsonObject {
    put("id", e.id); put("profile_id", e.profileId); put("medication_item_id", e.medicationItemId)
    putRaw(this, "stock_by_slot", e.stockBySlotJson)
    put("stock_counted_at", e.stockCountedAtMs); put("tz_offset_min", e.tzOffsetMin)
}

/** 墓碑行（软删仅需 id） */
fun tombstoneRow(id: String): JsonObject = kotlinx.serialization.json.buildJsonObject { put("id", id) }

// ---------- jsonb 原文 → List 辅助（UI 聚合用） ----------

fun jsonArrayToStrings(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        kotlinx.serialization.json.Json.parseToJsonElement(raw)
            .let { it as? kotlinx.serialization.json.JsonArray }
            ?.map { el -> el.jsonPrimitive.content } ?: emptyList()
    }.getOrDefault(emptyList())
}

fun jsonObjectToDoubles(raw: String?): Map<String, Double> = runCatching {
    if (raw.isNullOrBlank()) emptyMap() else Json.parseToJsonElement(raw).jsonObject.mapNotNull { (key, value) ->
        value.jsonPrimitive.doubleOrNull?.let { key to it }
    }.toMap()
}.getOrDefault(emptyMap())

fun doublesToJsonObject(values: Map<String, Double>): String = buildJsonObject {
    values.forEach { (key, value) -> put(key, value) }
}.toString()

fun jsonArrayToInts(raw: String?): List<Int> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        kotlinx.serialization.json.Json.parseToJsonElement(raw)
            .let { it as? kotlinx.serialization.json.JsonArray }
            ?.mapNotNull { el -> el.jsonPrimitive.intOrNull } ?: emptyList()
    }.getOrDefault(emptyList())
}

fun stringsToJsonArray(list: List<String>): String =
    kotlinx.serialization.json.JsonArray(list.map { JsonPrimitive(it) }).toString()

fun intsToJsonArray(list: List<Int>): String =
    kotlinx.serialization.json.JsonArray(list.map { JsonPrimitive(it) }).toString()
