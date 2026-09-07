// 仓储：Room 单一事实源 → UI 模型聚合；写操作 = 本地事务（业务行 + outbox）→ 触发上行
// 契约：docs/02 §4（同步策略）、§4.3（追加型只增+软删 / 可改表整行 LWW）；01 §5.2（改量链/批次归属）
package com.family.health.data.repo

import com.family.health.data.db.AppDatabase
import com.family.health.data.db.CheckupEventEntity
import com.family.health.data.db.DailyMedItemEntity
import com.family.health.data.db.DeviceEntity
import com.family.health.data.db.IndicatorItemEntity
import com.family.health.data.db.MedChangeEntity
import com.family.health.data.db.MeasurementEntity
import com.family.health.data.db.MedicationItemEntity
import com.family.health.data.db.NoteEntity
import com.family.health.data.db.OutboxEntity
import com.family.health.data.db.ProfileEntity
import com.family.health.data.db.ReminderEntity
import com.family.health.data.db.WatchItemEntity
import com.family.health.data.model.CheckupEvent
import com.family.health.data.model.DailyMedItem
import com.family.health.data.model.Device
import com.family.health.data.model.IndicatorItem
import com.family.health.data.model.Measurement
import com.family.health.data.model.MedChange
import com.family.health.data.model.MedicationItem
import com.family.health.data.model.Note
import com.family.health.data.model.Profile
import com.family.health.data.model.ReminderSetting
import com.family.health.data.model.Report
import com.family.health.data.model.WatchItem
import com.family.health.data.session.SessionStore
import com.family.health.data.sync.SyncEngine
import com.family.health.syncclient.HttpSyncClient
import com.family.health.syncclient.ImportResult
import com.family.health.util.dateTimeToMs
import com.family.health.util.deviceTzOffsetMin
import com.family.health.util.mmDdHmToMs
import com.family.health.util.msToDate
import com.family.health.util.msToDateTime
import com.family.health.util.msToMmDdHm
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

class Repository(
    private val db: AppDatabase,
    private val session: SessionStore,
    private val engine: SyncEngine,
    private val client: HttpSyncClient,
) {
    private fun uuid() = UUID.randomUUID().toString()

    // ================= 读取：聚合流 =================

    val devicesFlow: Flow<List<Device>> = db.deviceDao().observeAll().map { list ->
        list.map { Device(it.id, it.displayName, it.type, self = it.id == session.deviceId) }
    }

    val remindersFlow: Flow<Map<String, ReminderSetting>> = db.reminderDao().observeAll().map { list ->
        list.groupBy { it.profileId }.mapValues { (_, rs) ->
            ReminderSetting(
                medTimes = rs.firstOrNull { it.type == "medication" }
                    ?.let { jsonArrayToStrings(it.timesJson) } ?: emptyList(),
                measureTimes = rs.firstOrNull { it.type == "measure" }
                    ?.let { jsonArrayToStrings(it.timesJson) } ?: emptyList(),
                advanceDays = rs.firstOrNull { it.type == "checkup" }
                    ?.let { jsonArrayToInts(it.advanceDaysJson) } ?: listOf(1),
            )
        }
    }

    /** 成员聚合流：13 张表按 profileId 组装为 UI 模型 */
    @Suppress("UNCHECKED_CAST")
    val membersFlow: Flow<List<Profile>> = combine(
        listOf(
            db.profileDao().observeAll().map { it as Any },
            db.measurementDao().observeAll().map { it as Any },
            db.checkupEventDao().observeAll().map { it as Any },
            db.reportDao().observeAll().map { it as Any },
            db.indicatorItemDao().observeAll().map { it as Any },
            db.watchItemDao().observeAll().map { it as Any },
            db.medicationItemDao().observeAll().map { it as Any },
            db.medChangeDao().observeAll().map { it as Any },
            db.attachmentDao().observeAll().map { it as Any },
            db.noteDao().observeAll().map { it as Any },
            db.dailyMedItemDao().observeAll().map { it as Any },
            db.deviceDao().observeAll().map { it as Any },
        )
    ) { arr -> assembleMembers(arr) }

    private fun assembleMembers(arr: Array<Any>): List<Profile> {
        val profiles = arr[0] as List<ProfileEntity>
        val measurements = arr[1] as List<MeasurementEntity>
        val events = arr[2] as List<CheckupEventEntity>
        val reports = arr[3] as List<com.family.health.data.db.ReportEntity>
        val indicators = arr[4] as List<IndicatorItemEntity>
        val watches = arr[5] as List<WatchItemEntity>
        val meds = arr[6] as List<MedicationItemEntity>
        val changes = arr[7] as List<MedChangeEntity>
        val attachments = arr[8] as List<com.family.health.data.db.AttachmentEntity>
        val notes = arr[9] as List<NoteEntity>
        val dailies = arr[10] as List<DailyMedItemEntity>
        val devices = arr[11] as List<DeviceEntity>
        val devName = devices.associate { it.id to it.displayName }
        fun signer(deviceId: String) = devName[deviceId] ?: "家人"

        return profiles.map { p ->
            Profile(
                id = p.id, name = p.name, relation = p.relation, gender = p.gender,
                birthDate = p.birthDate ?: "", profileNote = p.notes,
                meds = meds.filter { it.profileId == p.id }.map { m ->
                    MedicationItem(
                        id = m.id, name = m.name, dosageText = m.dosageText,
                        doseSlots = jsonArrayToStrings(m.doseSlotsJson),
                        medKind = m.medKind, category = m.category,
                        startDate = m.startDate, endDate = m.endDate,
                        supersedesId = m.supersedesId, changeId = m.changeId,
                    )
                },
                changes = changes.filter { it.profileId == p.id }.map { c ->
                    MedChange(c.id, c.effectiveDate, c.note ?: "", c.linkedEventId)
                },
                daily = dailies.filter { it.profileId == p.id }.map { d ->
                    DailyMedItem(
                        id = d.id, name = d.name, isTcm = d.isTcm, doseText = d.doseText ?: "",
                        doseSlots = jsonArrayToStrings(d.doseSlotsJson),
                        stockQty = d.stockQty, stockUnit = d.stockUnit ?: "片", dailyQty = d.dailyQty,
                        tcmPacks = d.tcmPacks, tcmDaysPerPack = d.tcmDaysPerPack ?: 1,
                        tcmUsedDays = d.tcmUsedDays ?: 0,
                    )
                },
                events = events.filter { it.profileId == p.id }
                    .sortedByDescending { it.checkupDate }
                    .map { e ->
                        CheckupEvent(
                            id = e.id, checkupDate = e.checkupDate,
                            hospital = e.hospital ?: "", department = e.department ?: "",
                            note = e.note ?: "", nextCheckupDate = e.nextCheckupDate,
                            medChangeSummary = e.medicationChangesNote ?: "",
                            reports = reports.filter { it.eventId == e.id }
                                .sortedBy { it.seq }
                                .map { r ->
                                    Report(
                                        title = r.title, reportDate = r.reportDate,
                                        attachments = attachments.count { it.reportId == r.id },
                                        conclusionText = r.conclusionText ?: "",
                                        indicators = indicators.filter { it.reportId == r.id }
                                            .sortedBy { it.sortOrder }
                                            .map { i ->
                                                IndicatorItem(
                                                    itemName = i.itemName,
                                                    valueNumeric = i.valueNumeric,
                                                    valueText = i.valueText,
                                                    unit = i.unit ?: "",
                                                    referenceRange = i.referenceRange ?: "",
                                                )
                                            },
                                    )
                                },
                            createdBy = signer(e.createdBy),
                        )
                    },
                measurements = measurements.filter { it.profileId == p.id }.map { m ->
                    Measurement(
                        id = m.id, type = m.type, measuredAt = msToDateTime(m.measuredAt),
                        systolic = m.systolic, diastolic = m.diastolic, heartRateBpm = m.heartRateBpm,
                        glucoseMmol = m.glucoseMmol, glucoseContext = m.glucoseContext,
                        note = m.note ?: "", createdBy = signer(m.createdBy),
                    )
                }.sortedByDescending { it.measuredAt },
                notes = notes.filter { it.profileId == p.id }
                    .sortedByDescending { it.createdAtMs }
                    .map { n ->
                        Note(
                            id = n.id, text = n.text, done = n.done,
                            remindAt = n.remindAt?.let { msToMmDdHm(it) },
                            remindTargetName = jsonArrayToStrings(n.remindTargetsJson)
                                .firstOrNull()?.let { signer(it) },
                            createdBy = signer(n.createdBy),
                            createdAtLabel = msToDate(n.createdAtMs).substring(5),
                        )
                    },
                watchlist = watches.filter { it.profileId == p.id }
                    .sortedBy { it.sortOrder }
                    .map { w ->
                        WatchItem(w.canonicalName, jsonArrayToStrings(w.aliasesJson), w.canonicalUnit ?: "")
                    },
            )
        }
    }

    // ================= 写入（本地事务 + outbox → 上行） =================

    private fun outboxOps(key: String, ops: List<Triple<String, String, JsonObject>>): List<OutboxEntity> =
        ops.map { (table, op, row) ->
            OutboxEntity(tableName = table, op = op, rowJson = row.toString(), idemKey = key,
                createdAtMs = System.currentTimeMillis())
        }

    /** 追加型表"编辑" = 软删旧行 + 新 id 插入（02 §4.3：追加型只增） */
    suspend fun addMeasurement(profileId: String, m: Measurement) {
        val e = MeasurementEntity(
            id = uuid(), profileId = profileId, type = m.type,
            measuredAt = dateTimeToMs(m.measuredAt), tzOffsetMin = deviceTzOffsetMin(),
            systolic = m.systolic, diastolic = m.diastolic, heartRateBpm = m.heartRateBpm,
            glucoseMmol = m.glucoseMmol, glucoseContext = m.glucoseContext,
            note = m.note.ifEmpty { null }, createdBy = session.deviceId, deleted = false, seq = 0,
        )
        val key = uuid()
        db.withTransaction {
            db.measurementDao().upsertAll(listOf(e))
            db.outboxDao().insertAll(outboxOps(key, listOf(Triple("measurements", "insert", measurementToRow(e)))))
        }
        engine.kickPush()
    }

    suspend fun updateMeasurement(m: Measurement) {
        val old = db.measurementDao().byId(m.id) ?: return
        val e2 = old.copy(
            id = uuid(), systolic = m.systolic, diastolic = m.diastolic,
            heartRateBpm = m.heartRateBpm, glucoseMmol = m.glucoseMmol,
            glucoseContext = m.glucoseContext, note = m.note.ifEmpty { null }, deleted = false,
        )
        val key = uuid()
        db.withTransaction {
            db.measurementDao().upsertAll(listOf(old.copy(deleted = true), e2))
            db.outboxDao().insertAll(outboxOps(key, listOf(
                Triple("measurements", "delete", tombstoneRow(m.id)),
                Triple("measurements", "insert", measurementToRow(e2)),
            )))
        }
        engine.kickPush()
    }

    suspend fun deleteMeasurement(id: String) {
        val old = db.measurementDao().byId(id) ?: return
        val key = uuid()
        db.withTransaction {
            db.measurementDao().upsertAll(listOf(old.copy(deleted = true)))
            db.outboxDao().insertAll(outboxOps(key, listOf(Triple("measurements", "delete", tombstoneRow(id)))))
        }
        engine.kickPush()
    }

    /** 按 id 存在与否决定 update/insert（id 由调用方预生成，保证页面立即可选中） */
    suspend fun saveProfile(
        id: String, name: String, relation: String, gender: String, birth: String, note: String,
    ): String {
        val key = uuid()
        val old = db.profileDao().byId(id)
        val e = if (old != null) {
            old.copy(name = name, relation = relation, gender = gender,
                birthDate = birth.ifEmpty { null }, notes = note)
        } else {
            ProfileEntity(id, name, relation, gender, birth.ifEmpty { null }, note, false, 0)
        }
        db.withTransaction {
            db.profileDao().upsertAll(listOf(e))
            db.outboxDao().insertAll(outboxOps(key, listOf(
                Triple("profiles", if (old != null) "update" else "insert", profileToRow(e))
            )))
        }
        engine.kickPush()
        return id
    }

    suspend fun saveMed(item: MedicationItem) {
        val old = db.medicationItemDao().byId(item.id) ?: return
        val e = old.copy(
            name = item.name, dosageText = item.dosageText,
            doseSlotsJson = stringsToJsonArray(item.doseSlots),
            medKind = item.medKind, category = item.category,
            startDate = item.startDate, endDate = item.endDate,
        )
        val key = uuid()
        db.withTransaction {
            db.medicationItemDao().upsertAll(listOf(e))
            db.outboxDao().insertAll(outboxOps(key, listOf(Triple("medication_items", "update", medicationItemToRow(e)))))
        }
        engine.kickPush()
    }

    /** 记用药变化：med_changes 先行（规则 3），被替代条目先写（规则 2），同事务同事务批次一键幂等 */
    suspend fun saveMedChange(
        profileId: String, date: String, note: String, linkedEventId: String?,
        stops: Set<String>, adjustments: Map<String, Pair<String, Set<String>>>,
        additions: List<MedicationItem>,
    ): Triple<Int, Int, Int> {
        val changeId = uuid()
        val key = uuid()
        val change = MedChangeEntity(changeId, profileId, date, note.ifEmpty { null }, linkedEventId, false, 0)
        val ops = mutableListOf(Triple("med_changes", "insert", medChangeToRow(change)))
        val medsToUpsert = mutableListOf<MedicationItemEntity>()

        stops.forEach { id ->
            db.medicationItemDao().byId(id)?.let { old ->
                val e = old.copy(endDate = date)
                medsToUpsert += e
                ops += Triple("medication_items", "update", medicationItemToRow(e))
            }
        }
        adjustments.forEach { (id, adj) ->
            db.medicationItemDao().byId(id)?.let { old ->
                val ended = old.copy(endDate = date)
                medsToUpsert += ended
                ops += Triple("medication_items", "update", medicationItemToRow(ended))
                val next = old.copy(
                    id = uuid(), dosageText = adj.first.ifBlank { old.dosageText },
                    doseSlotsJson = if (adj.second.isEmpty()) old.doseSlotsJson else stringsToJsonArray(adj.second.toList()),
                    startDate = date, endDate = null, supersedesId = id, changeId = changeId,
                )
                medsToUpsert += next
                ops += Triple("medication_items", "insert", medicationItemToRow(next))
            }
        }
        additions.filter { it.name.isNotBlank() }.forEach { n ->
            val e = MedicationItemEntity(
                id = uuid(), profileId = profileId, category = n.category, medKind = n.medKind,
                name = n.name, dosageText = n.dosageText, doseSlotsJson = stringsToJsonArray(n.doseSlots),
                startDate = date, endDate = null, supersedesId = null, changeId = changeId,
                deleted = false, seq = 0,
            )
            medsToUpsert += e
            ops += Triple("medication_items", "insert", medicationItemToRow(e))
        }

        db.withTransaction {
            db.medChangeDao().upsertAll(listOf(change))
            db.medicationItemDao().upsertAll(medsToUpsert)
            db.outboxDao().insertAll(outboxOps(key, ops))
        }
        engine.kickPush()
        return Triple(stops.size, adjustments.size, additions.count { it.name.isNotBlank() })
    }

    suspend fun upsertDaily(profileId: String, item: DailyMedItem) {
        val exists = db.dailyMedItemDao().byId(item.id) != null
        val e = DailyMedItemEntity(
            id = item.id, profileId = profileId, isTcm = item.isTcm, name = item.name,
            doseText = item.doseText, doseSlotsJson = stringsToJsonArray(item.doseSlots),
            stockQty = item.stockQty, stockUnit = item.stockUnit, dailyQty = item.dailyQty,
            tcmPacks = item.tcmPacks, tcmDaysPerPack = item.tcmDaysPerPack,
            tcmUsedDays = item.tcmUsedDays, deleted = false, seq = 0,
        )
        val key = uuid()
        db.withTransaction {
            db.dailyMedItemDao().upsertAll(listOf(e))
            db.outboxDao().insertAll(outboxOps(key, listOf(
                Triple("daily_med_items", if (exists) "update" else "insert", dailyMedItemToRow(e))
            )))
        }
        engine.kickPush()
    }

    suspend fun deleteDaily(id: String) {
        val old = db.dailyMedItemDao().byId(id) ?: return
        val key = uuid()
        db.withTransaction {
            db.dailyMedItemDao().upsertAll(listOf(old.copy(deleted = true)))
            db.outboxDao().insertAll(outboxOps(key, listOf(Triple("daily_med_items", "delete", tombstoneRow(id)))))
        }
        engine.kickPush()
    }

    suspend fun toggleNote(noteId: String) {
        val old = db.noteDao().byId(noteId) ?: return
        val e = old.copy(done = !old.done)
        val key = uuid()
        db.withTransaction {
            db.noteDao().upsertAll(listOf(e))
            db.outboxDao().insertAll(outboxOps(key, listOf(Triple("notes", "update", noteToRow(e)))))
        }
        engine.kickPush()
    }

    suspend fun addNote(profileId: String, text: String, remindAtMmDdHm: String?, targetName: String?) {
        val remindMs = remindAtMmDdHm?.let { mmDdHmToMs(it) }
        val targetId = targetName?.let { name ->
            db.deviceDao().all().firstOrNull { it.displayName == name }?.id
        }
        val e = NoteEntity(
            id = uuid(), profileId = profileId, text = text, done = false,
            remindAt = remindMs, tzOffsetMin = remindMs?.let { deviceTzOffsetMin() },
            remindTargetsJson = targetId?.let { stringsToJsonArray(listOf(it)) } ?: "[]",
            createdBy = session.deviceId, createdAtMs = System.currentTimeMillis(),
            deleted = false, seq = 0,
        )
        val key = uuid()
        db.withTransaction {
            db.noteDao().upsertAll(listOf(e))
            db.outboxDao().insertAll(outboxOps(key, listOf(Triple("notes", "insert", noteToRow(e)))))
        }
        engine.kickPush()
    }

    suspend fun addWatch(profileId: String, name: String) {
        val e = WatchItemEntity(uuid(), profileId, name, stringsToJsonArray(listOf(name)), null, 0, false, 0)
        val key = uuid()
        db.withTransaction {
            db.watchItemDao().upsertAll(listOf(e))
            db.outboxDao().insertAll(outboxOps(key, listOf(Triple("watch_items", "insert", watchItemToRow(e)))))
        }
        engine.kickPush()
    }

    suspend fun addReminderTime(memberId: String, kind: String) {
        val type = if (kind == "med") "medication" else "measure"
        val rows = db.reminderDao().byProfile(memberId)
        val row = rows.firstOrNull { it.type == type }
        val key = uuid()
        db.withTransaction {
            if (row != null) {
                val e = row.copy(timesJson = stringsToJsonArray(jsonArrayToStrings(row.timesJson) + "12:00"))
                db.reminderDao().upsertAll(listOf(e))
                db.outboxDao().insertAll(outboxOps(key, listOf(Triple("reminders", "update", reminderToRow(e)))))
            } else {
                val e = ReminderEntity(
                    id = uuid(), profileId = memberId, type = type,
                    timesJson = stringsToJsonArray(listOf("12:00")),
                    measureType = if (type == "measure") "bp" else null,
                    advanceDaysJson = null, enabled = true, deleted = false, seq = 0,
                )
                db.reminderDao().upsertAll(listOf(e))
                db.outboxDao().insertAll(outboxOps(key, listOf(Triple("reminders", "insert", reminderToRow(e)))))
            }
        }
        engine.kickPush()
    }

    suspend fun removeReminderTime(memberId: String, kind: String, index: Int) {
        val type = if (kind == "med") "medication" else "measure"
        val row = db.reminderDao().byProfile(memberId).firstOrNull { it.type == type } ?: return
        val times = jsonArrayToStrings(row.timesJson).filterIndexed { i, _ -> i != index }
        val e = row.copy(timesJson = stringsToJsonArray(times))
        val key = uuid()
        db.withTransaction {
            db.reminderDao().upsertAll(listOf(e))
            db.outboxDao().insertAll(outboxOps(key, listOf(Triple("reminders", "update", reminderToRow(e)))))
        }
        engine.kickPush()
    }

    // ================= 复查导入（03；dry_run 预检 → 正式导入 → 增量下拉） =================

    private fun idemKeyOf(text: String): String = runCatching {
        kotlinx.serialization.json.Json.parseToJsonElement(text).jsonObject["import_id"]!!
            .jsonPrimitive.content
    }.getOrDefault(uuid())

    suspend fun importDryRun(text: String): ImportResult =
        client.importCall(session.server, session.token, idemKeyOf(text), text, dryRun = true)

    suspend fun importSubmit(text: String): ImportResult {
        val r = client.importCall(session.server, session.token, idemKeyOf(text), text, dryRun = false)
        engine.pullAll()
        return r
    }

    val pendingSyncCount: Flow<Int> = db.outboxDao().observeCount()
}
