// 契约：docs/05-页面结构与交互.md（交互行为）；数据为内存假数据（M2 前半段）
package com.family.health.data

import androidx.lifecycle.ViewModel
import com.family.health.data.model.CheckupEvent
import com.family.health.data.model.DailyMedItem
import com.family.health.data.model.Device
import com.family.health.data.model.Measurement
import com.family.health.data.model.MedChange
import com.family.health.data.model.MedicationItem
import com.family.health.data.model.Note
import com.family.health.data.model.Profile
import com.family.health.data.model.ReminderSetting
import com.family.health.data.model.WatchItem
import com.family.health.syncclient.FakeSyncClient
import com.family.health.syncclient.SyncClient
import com.family.health.util.todayStr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    val currentMember: Profile get() = members.first { it.id == currentMemberId }
}

class AppViewModel(
    val syncClient: SyncClient = FakeSyncClient(),
) : ViewModel() {

    private val _ui = MutableStateFlow(
        AppUiState(
            familyName = DemoData.FAMILY_NAME,
            devices = DemoData.devices,
            members = DemoData.members,
            currentMemberId = "yeye",
            reminders = DemoData.reminders,
        )
    )
    val ui: StateFlow<AppUiState> = _ui.asStateFlow()

    private fun newId(prefix: String) = prefix + UUID.randomUUID().toString().substring(0, 8)

    fun toast(text: String) =
        _ui.update { it.copy(toast = ToastMsg(System.nanoTime(), text)) }

    fun clearToast(id: Long) =
        _ui.update { if (it.toast?.id == id) it.copy(toast = null) else it }

    private fun updateMember(id: String, transform: (Profile) -> Profile) =
        _ui.update { s -> s.copy(members = s.members.map { if (it.id == id) transform(it) else it }) }

    private fun updateCurrent(transform: (Profile) -> Profile) =
        updateMember(_ui.value.currentMemberId, transform)

    // ---------- 导航/偏好 ----------
    fun selectMember(id: String) = _ui.update { it.copy(currentMemberId = id) }
    fun setRecordsSeg(seg: String) = _ui.update { it.copy(recordsSeg = seg) }
    fun setMeasureType(type: String) = _ui.update { it.copy(measureType = type) }
    fun setMeasurePeriod(period: MeasurePeriod) = _ui.update { it.copy(measurePeriod = period) }
    fun setTrendsTableMode(table: Boolean) = _ui.update { it.copy(trendsTableMode = table) }
    fun setTrendDays(days: Int) = _ui.update { it.copy(trendDays = days) }

    // ---------- 测量 ----------
    fun addMeasurement(m: Measurement) {
        updateCurrent { it.copy(measurements = listOf(m) + it.measurements) }
        if (m.glucoseContext != null) _ui.update { s -> s.copy(lastGlucoseCtx = m.glucoseContext) }
    }

    fun updateMeasurement(m: Measurement) = updateCurrent {
        it.copy(measurements = it.measurements.map { x -> if (x.id == m.id) m else x })
    }

    /** 软删除（契约：docs/02 §1 一律软删） */
    fun deleteMeasurement(id: String) = updateCurrent {
        it.copy(measurements = it.measurements.map { x -> if (x.id == id) x.copy(deleted = true) else x })
    }

    // ---------- 便签 ----------
    fun toggleNote(noteId: String) = updateCurrent {
        it.copy(notes = it.notes.map { n -> if (n.id == noteId) n.copy(done = !n.done) else n })
    }

    fun addNote(text: String, remindAt: String?, target: String?) = updateCurrent {
        val me = _ui.value.devices.firstOrNull { d -> d.self }?.displayName ?: "爸爸"
        it.copy(
            notes = listOf(
                Note(newId("n"), text, done = false, remindAt = remindAt,
                    remindTargetName = target, createdBy = me, createdAtLabel = "今天")
            ) + it.notes
        )
    }

    // ---------- 用药：编辑单条 ----------
    fun saveMed(item: MedicationItem) = updateCurrent {
        it.copy(meds = it.meds.map { m -> if (m.id == item.id) item else m })
    }

    /** 记用药变化：核对式一次保存（契约：docs/05 §5；docs/02 §3.9 改量链） */
    fun saveMedChange(
        date: String,
        note: String,
        linkedEventId: String?,
        stops: Set<String>,
        adjustments: Map<String, Pair<String, Set<String>>>, // medId -> (dosage, slots)
        additions: List<MedicationItem>,
    ): Triple<Int, Int, Int> {
        val changeId = newId("c")
        updateCurrent { p ->
            val meds = p.meds.toMutableList()
            stops.forEach { id ->
                val i = meds.indexOfFirst { it.id == id }
                if (i >= 0) meds[i] = meds[i].copy(endDate = date)
            }
            adjustments.forEach { (id, adj) ->
                val i = meds.indexOfFirst { it.id == id }
                if (i >= 0) {
                    val old = meds[i]
                    meds[i] = old.copy(endDate = date)
                    meds.add(
                        old.copy(
                            id = newId("m"), dosageText = adj.first.ifBlank { old.dosageText },
                            doseSlots = if (adj.second.isEmpty()) old.doseSlots else adj.second.toList(),
                            startDate = date, endDate = null,
                            supersedesId = id, changeId = changeId,
                        )
                    )
                }
            }
            additions.filter { it.name.isNotBlank() }.forEach { n ->
                meds.add(n.copy(startDate = date, changeId = changeId))
            }
            val total = stops.size + adjustments.size + additions.count { it.name.isNotBlank() }
            val changes = if (total > 0) {
                listOf(MedChange(changeId, date, note, linkedEventId)) + p.changes
            } else p.changes
            p.copy(meds = meds, changes = changes)
        }
        return Triple(stops.size, adjustments.size, additions.count { it.name.isNotBlank() })
    }

    // ---------- 今日用药 ----------
    fun upsertDaily(item: DailyMedItem) = updateCurrent { p ->
        if (p.daily.any { it.id == item.id }) {
            p.copy(daily = p.daily.map { if (it.id == item.id) item else it })
        } else p.copy(daily = p.daily + item)
    }

    fun deleteDaily(id: String) = updateCurrent { p ->
        p.copy(daily = p.daily.filterNot { it.id == id })
    }

    fun newDailyId() = newId("dl")
    fun newMedId() = newId("m")

    // ---------- 重点清单 ----------
    fun addWatch(name: String) = updateCurrent { p ->
        if (p.watchlist.any { it.canonicalName == name }) p
        else p.copy(watchlist = p.watchlist + WatchItem(name, listOf(name)))
    }

    // ---------- 复查导入 ----------
    fun importEvent(event: CheckupEvent) = updateCurrent {
        it.copy(events = listOf(event) + it.events)
    }

    // ---------- 我的：成员/设备/提醒 ----------
    fun saveProfile(id: String?, name: String, relation: String, gender: String, birth: String, note: String): String {
        return if (id != null) {
            updateMember(id) {
                it.copy(name = name, relation = relation, gender = gender, birthDate = birth, profileNote = note)
            }
            id
        } else {
            val nid = newId("u")
            _ui.update { s ->
                s.copy(members = s.members + Profile(nid, name, relation, gender, birth, note))
            }
            nid
        }
    }

    fun revokeDevice(id: String) =
        _ui.update { s -> s.copy(devices = s.devices.filterNot { it.id == id }) }

    fun addReminderTime(memberId: String, kind: String) = _ui.update { s ->
        val r = s.reminders[memberId] ?: ReminderSetting()
        val nr = if (kind == "med") r.copy(medTimes = r.medTimes + "12:00")
        else r.copy(measureTimes = r.measureTimes + "12:00")
        s.copy(reminders = s.reminders + (memberId to nr))
    }

    fun removeReminderTime(memberId: String, kind: String, index: Int) = _ui.update { s ->
        val r = s.reminders[memberId] ?: return@update s
        val nr = if (kind == "med") r.copy(medTimes = r.medTimes.filterIndexed { i, _ -> i != index })
        else r.copy(measureTimes = r.measureTimes.filterIndexed { i, _ -> i != index })
        s.copy(reminders = s.reminders + (memberId to nr))
    }

    fun currentReminder(): ReminderSetting =
        _ui.value.reminders[_ui.value.currentMemberId] ?: ReminderSetting()
}
