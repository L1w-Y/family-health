// 同步引擎：下行增量应用（事务）、上行 outbox 重放（同 idemKey 整批）、错误外露
// 契约：docs/02 §4.1（since/next 增量）、§4.2（幂等重放）
package com.family.health.data.sync

import androidx.room.withTransaction
import com.family.health.data.db.AppDatabase
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
import com.family.health.data.repo.toEntity
import com.family.health.data.session.SessionStore
import com.family.health.syncclient.ChangeRow
import com.family.health.syncclient.HttpSyncClient
import com.family.health.syncclient.WriteOp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class SyncEngine(
    private val db: AppDatabase,
    private val session: SessionStore,
    private val client: HttpSyncClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex() // 推拉串行，避免乱序
    private val json = Json { ignoreUnknownKeys = true }

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    val pendingCount = db.outboxDao().observeCount()

    /** 非阻塞触发：推 outbox → 拉增量（写操作后调用） */
    fun kickPush() = scope.launch { cycle() }

    /** 非阻塞触发：仅下拉（启动/手动刷新） */
    fun kickPull() = scope.launch { cycle() }

    /** 挂起式全量同步（首配/导入后调用）；异常上抛 */
    suspend fun pullAll() = lock.withLock { pullAllInternal() }

    private suspend fun cycle() = lock.withLock {
        if (!session.isConfigured) return@withLock
        _syncing.value = true
        try {
            pushPendingInternal()
            pullAllInternal()
            _lastError.value = null
        } catch (e: Exception) {
            _lastError.value = e.message ?: "同步失败"
        } finally {
            _syncing.value = false
        }
    }

    private suspend fun pullAllInternal() {
        var since = session.lastSeq
        while (true) {
            val r = client.pull(session.server, session.token, since, 500)
            if (r.changes.isNotEmpty()) applyRows(r.changes)
            session.lastSeq = r.next
            if (r.changes.size < 500) break
            since = r.next
        }
    }

    private suspend fun applyRows(changes: List<ChangeRow>) {
        db.withTransaction {
            changes.groupBy { it.table }.forEach { (table, rows) ->
                val entities = rows.map { it.toEntity() }
                when (table) {
                    "devices" -> db.deviceDao().upsertAll(entities.filterIsInstance<DeviceEntity>())
                    "profiles" -> db.profileDao().upsertAll(entities.filterIsInstance<ProfileEntity>())
                    "measurements" -> db.measurementDao().upsertAll(entities.filterIsInstance<MeasurementEntity>())
                    "checkup_events" -> db.checkupEventDao().upsertAll(entities.filterIsInstance<CheckupEventEntity>())
                    "reports" -> db.reportDao().upsertAll(entities.filterIsInstance<ReportEntity>())
                    "indicator_items" -> db.indicatorItemDao().upsertAll(entities.filterIsInstance<IndicatorItemEntity>())
                    "watch_items" -> db.watchItemDao().upsertAll(entities.filterIsInstance<WatchItemEntity>())
                    "medication_items" -> db.medicationItemDao().upsertAll(entities.filterIsInstance<MedicationItemEntity>())
                    "med_changes" -> db.medChangeDao().upsertAll(entities.filterIsInstance<MedChangeEntity>())
                    "reminders" -> db.reminderDao().upsertAll(entities.filterIsInstance<ReminderEntity>())
                    "attachments" -> db.attachmentDao().upsertAll(entities.filterIsInstance<AttachmentEntity>())
                    "notes" -> db.noteDao().upsertAll(entities.filterIsInstance<NoteEntity>())
                    "daily_med_items" -> db.dailyMedItemDao().upsertAll(entities.filterIsInstance<DailyMedItemEntity>())
                    // families/import_records/idempotency_keys 不下行
                }
            }
        }
    }

    /** 按 idemKey 分组整批推送（02 §4.2：同批共键，服务端整批幂等）；失败保留待下次 */
    private suspend fun pushPendingInternal() {
        val items = db.outboxDao().pending()
        if (items.isEmpty()) return
        items.groupBy { it.idemKey }.forEach { (key, group) ->
            val ops = group.map { WriteOp(it.tableName, it.op, json.parseToJsonElement(it.rowJson).jsonObject) }
            client.push(session.server, session.token, key, ops)
            db.outboxDao().deleteByIds(group.map { it.id })
        }
    }
}
