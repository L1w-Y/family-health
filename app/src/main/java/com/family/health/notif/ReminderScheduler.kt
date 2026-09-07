// 提醒调度：AlarmManager 定时触发；优先精确闹钟，无权限时退回非精确（省电）
package com.family.health.notif

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

class ReminderScheduler(private val context: Context) {
    private val store = LocalReminderStore(context)

    fun store(): LocalReminderStore = store

    fun schedule(r: LocalReminder) {
        store.upsert(r)
        setAlarm(r)
    }

    fun cancel(id: Int) {
        store.remove(id)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(id, null))
    }

    /** 开机/启动时全量重排 */
    fun rescheduleAll() {
        val now = System.currentTimeMillis()
        store.all().forEach { r ->
            if (r.repeatDaily) {
                val next = nextDailyAt(r.triggerAtMs, now)
                setAlarm(r.copy(triggerAtMs = next))
            } else if (r.triggerAtMs > now) {
                setAlarm(r)
            } else {
                store.remove(r.id) // 过期单次提醒清理
            }
        }
    }

    /** 触发后处理（接收器调用）：每日重复 → 排次日；单次 → 清除 */
    fun onFired(id: Int) {
        val r = store.all().firstOrNull { it.id == id } ?: return
        if (r.repeatDaily) {
            setAlarm(r.copy(triggerAtMs = nextDailyAt(r.triggerAtMs, System.currentTimeMillis())))
        } else {
            store.remove(id)
        }
    }

    private fun setAlarm(r: LocalReminder) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(r.id, r)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.triggerAtMs, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.triggerAtMs, pi)
        }
    }

    private fun pendingIntent(id: Int, r: LocalReminder?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            r?.let {
                putExtra(ReminderReceiver.X_ID, it.id)
                putExtra(ReminderReceiver.X_TITLE, it.title)
                putExtra(ReminderReceiver.X_TEXT, it.text)
            }
        }
        return PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        /** 同日 time 的下一个触发点（已过则次日） */
        fun nextDailyAt(baseMs: Long, now: Long): Long {
            var t = baseMs
            val dayMs = 24 * 3600 * 1000L
            while (t <= now) t += dayMs
            return t
        }
    }
}
