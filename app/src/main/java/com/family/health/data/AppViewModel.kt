// 应用 ViewModel：UI 状态装配（Room 流）+ 写操作分发（仓储）。契约：docs/05（交互行为）、docs/02 §4
package com.family.health.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.family.health.FamilyHealthApp
import com.family.health.data.model.CheckupEvent
import com.family.health.data.model.DailyMedItem
import com.family.health.data.model.Device
import com.family.health.data.model.Measurement
import com.family.health.data.model.MedicationItem
import com.family.health.data.model.Note
import com.family.health.data.model.Profile
import com.family.health.data.model.ReminderSetting
import com.family.health.data.session.SessionStore
import com.family.health.data.sync.SyncEngine
import com.family.health.data.repo.Repository
import com.family.health.syncclient.ApiException
import com.family.health.syncclient.HttpSyncClient
import com.family.health.syncclient.ImportResult
import com.family.health.util.dateTimeToMs
import com.family.health.util.todayStr
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ToastMsg(val id: Long, val text: String)

/** 测量段周期：自然月 or 近 N 天（契约：docs/05 §4.2） */
sealed interface MeasurePeriod {
    data class Month(val month: String) : MeasurePeriod
    data class LastDays(val days: Int) : MeasurePeriod
}

data class AppUiState(
    val familyName: String,
    val devices: List<Device>,
    val members: List<Profile>,
    val currentMemberId: String,
    val reminders: Map<String, ReminderSetting>,
    val toast: ToastMsg? = null,
    // —— 页面 UI 偏好（对应 prototype state） ——
    val recordsSeg: String = "checkup", // checkup | measure
    val measureType: String = "bp", // bp | glucose
    val measurePeriod: MeasurePeriod = MeasurePeriod.Month(todayStr().substring(0, 7)),
    val trendsTableMode: Boolean = true,
    val trendDays: Int = 30,
    val lastGlucoseCtx: String = "fasting",
) {
    /** 空成员安全：未配置/空家庭时给占位档案，页面走空态分支 */
    val currentMember: Profile
        get() = members.firstOrNull { it.id == currentMemberId }
            ?: members.firstOrNull()
            ?: Profile(id = "", name = "（暂无成员）")
}

/** 首配状态（SetupScreen 观察） */
data class SetupUiState(
    val loading: Boolean = false,
    val error: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as FamilyHealthApp).container
    private val session: SessionStore = container.session
    private val repo: Repository = container.repo
    private val engine: SyncEngine = container.engine
    private val client: HttpSyncClient = container.client

    // ---------- 配置门禁 ----------
    private val _configured = MutableStateFlow(session.isConfigured)
    val configured: StateFlow<Boolean> = _configured.asStateFlow()

    private val _setup = MutableStateFlow(SetupUiState())
    val setup: StateFlow<SetupUiState> = _setup.asStateFlow()

    val syncing = engine.syncing
    val syncError = engine.lastError
    val pendingSyncCount = repo.pendingSyncCount

    // ---------- UI 状态 ----------
    private val _toast = MutableStateFlow<ToastMsg?>(null)
    private val _familyName = MutableStateFlow(session.familyName)
    private val _currentMemberId = MutableStateFlow(session.currentMemberId)
    private val _prefs = MutableStateFlow(Prefs())

    private data class Prefs(
        val recordsSeg: String = "checkup",
        val measureType: String = "bp",
        val measurePeriod: MeasurePeriod = MeasurePeriod.Month(todayStr().substring(0, 7)),
        val trendsTableMode: Boolean = true,
        val trendDays: Int = 30,
        val lastGlucoseCtx: String = "fasting",
    )

    @Suppress("UNCHECKED_CAST")
    val ui: StateFlow<AppUiState> = combine(
        listOf(
            repo.membersFlow.map { it as Any? },
            repo.devicesFlow.map { it as Any? },
            repo.remindersFlow.map { it as Any? },
            _currentMemberId.map { it as Any? },
            _toast.map { it as Any? },
            _prefs.map { it as Any? },
            _familyName.map { it as Any? },
        )
    ) { arr ->
        val p = arr[5] as Prefs
        AppUiState(
            familyName = arr[6] as String,
            devices = arr[1] as List<Device>,
            members = arr[0] as List<Profile>,
            currentMemberId = arr[3] as String,
            reminders = arr[2] as Map<String, ReminderSetting>,
            toast = arr[4] as ToastMsg?,
            recordsSeg = p.recordsSeg,
            measureType = p.measureType,
            measurePeriod = p.measurePeriod,
            trendsTableMode = p.trendsTableMode,
            trendDays = p.trendDays,
            lastGlucoseCtx = p.lastGlucoseCtx,
        )
    }.stateInUi(AppUiState("", emptyList(), emptyList(), "", emptyMap()))

    private fun <T> Flow<T>.stateInUi(initial: T) =
        this.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, initial)

    // ---------- toast ----------
    fun toast(text: String) = _toast.tryEmit(ToastMsg(System.nanoTime(), text)).let { }
    fun clearToast(id: Long) =
        _toast.update { if (it?.id == id) null else it }

    // ---------- 导航/偏好 ----------
    fun selectMember(id: String) {
        _currentMemberId.value = id
        session.currentMemberId = id
    }
    fun setRecordsSeg(seg: String) = _prefs.update { it.copy(recordsSeg = seg) }
    fun setMeasureType(type: String) = _prefs.update { it.copy(measureType = type) }
    fun setMeasurePeriod(period: MeasurePeriod) = _prefs.update { it.copy(measurePeriod = period) }
    fun setTrendsTableMode(table: Boolean) = _prefs.update { it.copy(trendsTableMode = table) }
    fun setTrendDays(days: Int) = _prefs.update { it.copy(trendDays = days) }

    // ---------- 测量 ----------
    fun addMeasurement(m: Measurement) = launch { repo.addMeasurement(currentId(), m) }
    fun updateMeasurement(m: Measurement) = launch { repo.updateMeasurement(m) }

    /** 软删除（契约：docs/02 §1 一律软删） */
    fun deleteMeasurement(id: String) = launch { repo.deleteMeasurement(id) }

    // ---------- 便签 ----------
    fun toggleNote(noteId: String) = launch { repo.toggleNote(noteId) }
    fun deleteNote(noteId: String) = launch {
        repo.deleteNote(noteId)
        container.reminderScheduler.cancel(noteId.hashCode())
    }

    /** 新建便签：可选日期+时刻（到点本机通知，支持单次/每天） */
    fun addNote(text: String, date: String?, time: String?, repeatDaily: Boolean, target: String?) {
        val noteId = UUID.randomUUID().toString()
        val ms = if (!date.isNullOrBlank() && !time.isNullOrBlank()) {
            runCatching { dateTimeToMs("$date $time") }.getOrNull()
        } else null
        launch { repo.addNote(noteId, currentId(), text, ms, target) }
        if (ms != null) {
            container.reminderScheduler.schedule(
                com.family.health.notif.LocalReminder(
                    id = noteId.hashCode(), title = "便签提醒",
                    text = text.take(60), triggerAtMs = ms, repeatDaily = repeatDaily, kind = "note",
                )
            )
        }
    }

    // ---------- 今日用药勾选（本机视觉，按日清零） ----------
    private val _checkTick = MutableStateFlow(0L)
    val checkTick: StateFlow<Long> = _checkTick.asStateFlow()
    fun dailyCheckedSet(): Set<String> = container.dailyChecks.checkedSet()
    fun toggleDailyChecked(itemId: String) {
        container.dailyChecks.toggle(itemId)
        _checkTick.value = System.nanoTime()
    }

    // ---------- 测量提醒（本机每天闹钟 + reminders 表） ----------
    private val SLOT_TIMES = mapOf(
        "morning" to "08:00", "noon" to "12:00", "evening" to "19:00", "bedtime" to "21:30",
    )

    /** 概览快捷添加每日测量提醒 */
    fun addMeasureReminderTime(time: String) {
        val memberId = ui.value.currentMember.id
        launch { repo.addReminderTime(memberId, "measure", time) }
        scheduleMeasureDaily(time)
    }

    private fun scheduleMeasureDaily(time: String) {
        val todayMs = runCatching { dateTimeToMs("${todayStr()} $time") }.getOrNull() ?: return
        container.reminderScheduler.schedule(
            com.family.health.notif.LocalReminder(
                id = ("measure" + time).hashCode(), title = "测量提醒",
                text = "该测血压/血糖了",
                triggerAtMs = com.family.health.notif.ReminderScheduler.nextDailyAt(
                    todayMs, System.currentTimeMillis()
                ),
                repeatDaily = true, kind = "measure", timeLabel = time,
            )
        )
    }

    /** 启动/数据变化后按 reminders 表对齐测量类本机闹钟 */
    fun rescheduleMeasureReminders() {
        val wanted = ui.value.reminders.values.flatMap { it.measureTimes }.toSet()
        container.reminderScheduler.store().all()
            .filter { it.kind == "measure" && it.timeLabel !in wanted }
            .forEach { container.reminderScheduler.cancel(it.id) }
        wanted.forEach { scheduleMeasureDaily(it) }
    }

    /** 余量只够最后一天时：下次服药前 1 小时本机提醒补药（每味药只排一次） */
    fun checkLowStockReminders() {
        val now = System.currentTimeMillis()
        val nowLabel = "%02d:%02d".format(java.time.LocalTime.now().hour, java.time.LocalTime.now().minute)
        val store = container.reminderScheduler.store()
        ui.value.currentMember.daily.forEach { x ->
            val stock = x.stockQty ?: return@forEach
            val daily = x.dailyQty ?: return@forEach
            if (daily <= 0 || stock > daily) return@forEach // 余量 > 1 天用量，无需提醒
            val id = ("lowstock" + x.id).hashCode()
            if (store.all().any { it.id == id }) return@forEach
            val slotsToday = x.doseSlots.mapNotNull { SLOT_TIMES[it] }.filter { it > nowLabel }.sorted()
            val triggerMs = if (slotsToday.isNotEmpty()) {
                dateTimeToMs("${todayStr()} ${slotsToday.first()}") - 3600_000
            } else {
                val first = x.doseSlots.mapNotNull { SLOT_TIMES[it] }.minOrNull() ?: "08:00"
                dateTimeToMs("${todayStr()} $first") + 86400_000 - 3600_000
            }
            if (triggerMs <= now) return@forEach
            container.reminderScheduler.schedule(
                com.family.health.notif.LocalReminder(
                    id = id, title = "补药提醒",
                    text = "「${x.name}」只剩最后一天用量，记得补充",
                    triggerAtMs = triggerMs, repeatDaily = false, kind = "lowstock",
                )
            )
        }
    }

    /** 今日单次快捷测量提醒 */
    fun quickRemindToday(time: String) {
        val ms = runCatching { dateTimeToMs("${todayStr()} $time") }.getOrNull() ?: return
        if (ms <= System.currentTimeMillis()) {
            toast("该时刻已过")
            return
        }
        container.reminderScheduler.schedule(
            com.family.health.notif.LocalReminder(
                id = ("measure_once" + time).hashCode(), title = "测量提醒",
                text = "该测血压/血糖了", triggerAtMs = ms, repeatDaily = false, kind = "measure_once",
            )
        )
        toast("已设今天 $time 提醒")
    }

    // ---------- 用药 ----------
    fun saveMed(item: MedicationItem) = launch { repo.saveMed(item) }

    /** 记用药变化：计数即时返回（页面 toast 用），写库与上行异步完成（01 §5.2 改量链） */
    fun saveMedChange(
        date: String,
        note: String,
        linkedEventId: String?,
        stops: Set<String>,
        adjustments: Map<String, Pair<String, Set<String>>>,
        additions: List<MedicationItem>,
    ): Triple<Int, Int, Int> {
        launch { repo.saveMedChange(currentId(), date, note, linkedEventId, stops, adjustments, additions) }
        return Triple(stops.size, adjustments.size, additions.count { it.name.isNotBlank() })
    }

    // ---------- 今日用药 ----------
    fun upsertDaily(item: DailyMedItem) = launch { repo.upsertDaily(currentId(), item) }
    fun deleteDaily(id: String) = launch { repo.deleteDaily(id) }

    fun newDailyId() = UUID.randomUUID().toString()
    fun newMedId() = UUID.randomUUID().toString()

    // ---------- 重点清单 ----------
    fun addWatch(name: String) = launch { repo.addWatch(currentId(), name) }

    // ---------- 我的：成员/设备/提醒 ----------
    fun saveProfile(id: String?, name: String, relation: String, gender: String, birth: String, note: String): String {
        val nid = id ?: UUID.randomUUID().toString()
        launch { repo.saveProfile(nid, name, relation, gender, birth, note) }
        return nid
    }

    fun revokeDevice(id: String) = toast("一期暂不支持撤销设备（待服务端通道）")

    fun addReminderTime(memberId: String, kind: String) = launch { repo.addReminderTime(memberId, kind) }
    fun removeReminderTime(memberId: String, kind: String, index: Int) {
        if (kind == "measure") {
            ui.value.reminders[memberId]?.measureTimes?.getOrNull(index)
                ?.let { container.reminderScheduler.cancel(("measure" + it).hashCode()) }
        }
        launch { repo.removeReminderTime(memberId, kind, index) }
    }

    fun currentReminder(): ReminderSetting =
        ui.value.reminders[ui.value.currentMemberId] ?: ReminderSetting()

    fun setFamilyName(name: String) {
        _familyName.value = name
        session.familyName = name
    }

    // ---------- 同步 ----------
    fun syncNow() = engine.kickPull()

    /** 概览气泡点击设置下次复查日期 */
    fun setNextCheckup(eventId: String, date: String) = launch { repo.updateNextCheckupDate(eventId, date) }

    // ---------- 复查导入（03：dry_run 预检 → 正式导入） ----------
    fun importDryRun(text: String, onResult: (Result<ImportResult>) -> Unit) = launch {
        onResult(runCatching { repo.importDryRun(text) })
    }

    fun importSubmit(text: String, onResult: (Result<ImportResult>) -> Unit) = launch {
        onResult(runCatching { repo.importSubmit(text) })
    }

    // ---------- 首配 / 换绑 ----------
    fun setup(server: String, secret: String, displayName: String) {
        if (server.isBlank() || secret.isBlank() || displayName.isBlank()) {
            _setup.value = SetupUiState(error = "服务器地址、家庭口令、署名都要填")
            return
        }
        launch {
            _setup.value = SetupUiState(loading = true)
            try {
                val r = client.auth(server.trimEnd('/'), secret, displayName)
                session.server = server
                session.secret = secret
                session.token = r.token
                session.deviceId = r.deviceId
                session.deviceName = displayName
                session.familyId = r.familyId
                session.lastSeq = 0
                engine.pullAll()
                _configured.value = true
                _setup.value = SetupUiState()
            } catch (e: ApiException) {
                _setup.value = SetupUiState(error = e.message)
            } catch (e: Exception) {
                _setup.value = SetupUiState(error = "连接失败：${e.message ?: "请检查服务器地址与网络"}")
            }
        }
    }

    /** 换绑（清空本地镜像后重新配置；原家庭数据仅存在服务器） */
    fun reconfigure() = launch {
        session.resetAuth()
        container.db.clearAllTables()
        _configured.value = false
    }

    private fun currentId(): String = ui.value.currentMember.id

    /** 账号页展示用会话信息 */
    data class SessionInfo(val server: String, val deviceName: String, val familyId: String)
    fun sessionInfo() = SessionInfo(session.server, session.deviceName, session.familyId)

    /** 本机设备令牌（API 令牌页展示/复制；03 §2 Bearer 凭证） */
    fun deviceToken(): String = session.token

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }.let { }
}
