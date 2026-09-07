// 本机提醒（便签时刻/测量快捷提醒）：SharedPreferences 持久化 + 闹钟调度模型
// 契约：docs/04 §1（本地通知，一期不接推送通道）
package com.family.health.notif

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** kind：note=便签时刻；measure=测量；measure_once=今日单次测量 */
@Serializable
data class LocalReminder(
    val id: Int, // alarm requestCode（note 用 noteId.hashCode()，measure 用 ("measure"+time).hashCode()）
    val title: String,
    val text: String,
    val triggerAtMs: Long,
    val repeatDaily: Boolean,
    val kind: String,
    val timeLabel: String = "", // 每日重复展示用 "HH:mm"
)

class LocalReminderStore(context: Context) {
    private val sp = context.getSharedPreferences("family_health_reminders", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun all(): List<LocalReminder> = runCatching {
        json.decodeFromString<List<LocalReminder>>(sp.getString(K_LIST, "[]") ?: "[]")
    }.getOrDefault(emptyList())

    fun upsert(r: LocalReminder) {
        val list = all().filterNot { it.id == r.id } + r
        sp.edit().putString(K_LIST, json.encodeToString(list)).apply()
    }

    fun remove(id: Int) {
        sp.edit().putString(K_LIST, json.encodeToString(all().filterNot { it.id == id })).apply()
    }

    fun removeByKind(kind: String) {
        sp.edit().putString(K_LIST, json.encodeToString(all().filterNot { it.kind == kind })).apply()
    }

    private companion object { const val K_LIST = "list" }
}
