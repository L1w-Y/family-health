// Room 实体：服务器行的本地镜像（契约 docs/02 §3 表结构；deleted/seq 为同步列，业务列为 snake_case 映射）
package com.family.health.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val type: String,
    val deleted: Boolean,
    val seq: Long,
)

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val relation: String,
    val gender: String,
    val birthDate: String?,
    val notes: String,
    val deleted: Boolean,
    val seq: Long,
)

@Entity(tableName = "measurements")
data class MeasurementEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val type: String,
    val measuredAt: Long, // epoch ms
    val tzOffsetMin: Int,
    val systolic: Int?,
    val diastolic: Int?,
    val heartRateBpm: Int?,
    val glucoseMmol: Double?,
    val glucoseContext: String?,
    val note: String?,
    val payloadJson: String?, // 服务端 payload（jsonb 原文，预留给二期测量类型）
    val createdBy: String, // 设备 id，展示时映射署名
    val deleted: Boolean,
    val seq: Long,
)

@Entity(tableName = "checkup_events")
data class CheckupEventEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val checkupDate: String,
    val hospital: String?,
    val department: String?,
    val note: String?,
    val nextCheckupDate: String?,
    val medicationChangesNote: String?,
    val createdBy: String,
    val deleted: Boolean,
    val seq: Long,
)

@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val title: String,
    val reportDate: String,
    val conclusionText: String?,
    val deleted: Boolean,
    val seq: Long,
)

@Entity(tableName = "indicator_items")
data class IndicatorItemEntity(
    @PrimaryKey val id: String,
    val reportId: String,
    val itemName: String,
    val valueNumeric: Double?,
    val valueText: String?,
    val unit: String?,
    val referenceRange: String?,
    val sortOrder: Int,
    val canonicalName: String?,
    val deleted: Boolean,
    val seq: Long,
)

@Entity(tableName = "watch_items")
data class WatchItemEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val canonicalName: String,
    val aliasesJson: String, // jsonb 原文（数组）
    val canonicalUnit: String?,
    val sortOrder: Int,
    val deleted: Boolean,
    val seq: Long,
)

@Entity(tableName = "medication_items")
data class MedicationItemEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val category: String,
    val medKind: String,
    val name: String,
    val dosageText: String,
    val doseQty: Double?,
    val doseUnit: String?,
    val doseTimesPerDay: Int?,
    val doseSlotsJson: String?, // jsonb 原文（数组）
    val startDate: String,
    val endDate: String?,
    val supersedesId: String?,
    val changeId: String?,
    val deleted: Boolean,
    val seq: Long,
)

@Entity(tableName = "med_changes")
data class MedChangeEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val effectiveDate: String,
    val note: String?,
    val linkedEventId: String?,
    val deleted: Boolean,
    val seq: Long,
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val type: String, // medication | measure | checkup
    val timesJson: String?,
    val measureType: String?,
    val advanceDaysJson: String?,
    val enabled: Boolean,
    val deleted: Boolean,
    val seq: Long,
)

@Entity(tableName = "attachments")
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val reportId: String?,
    val fileKey: String,
    val mime: String?,
    val sizeBytes: Long?,
    val uploadState: String,
    val deleted: Boolean,
    val seq: Long,
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val text: String,
    val done: Boolean,
    val remindAt: Long?, // epoch ms
    val tzOffsetMin: Int?,
    val remindTargetsJson: String?,
    val createdBy: String,
    val createdAtMs: Long, // 服务器 created_at
    val deleted: Boolean,
    val seq: Long,
)

@Entity(tableName = "daily_med_items", indices = [Index(value = ["medicationItemId"])])
data class DailyMedItemEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val medicationItemId: String,
    val stockBySlotJson: String,
    val stockCountedAtMs: Long,
    val tzOffsetMin: Int,
    val deleted: Boolean,
    val seq: Long,
)

/** 待上行队列（02 §4.2：失败重放；idemKey 预生成，重试不重复） */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableName: String,
    val op: String, // insert | update | delete
    val rowJson: String,
    val idemKey: String,
    val createdAtMs: Long,
)

/** 元数据（备用键值；lastSeq 存 SessionStore） */
@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
