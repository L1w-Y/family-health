// 今日用药勾选（本机视觉状态，不写库）：按日期键存储，跨天自然清零（契约：05 §3 今日用药卡）
package com.family.health.data.session

import android.content.Context
import java.time.LocalDate

class DailyChecks(context: Context) {
    private val sp = context.getSharedPreferences("family_health_daily_checks", Context.MODE_PRIVATE)

    private fun todayKey() = "checks_${LocalDate.now()}"

    fun checkedSet(): Set<String> = sp.getStringSet(todayKey(), emptySet()) ?: emptySet()

    fun toggle(itemId: String) {
        val set = checkedSet().toMutableSet()
        if (!set.add(itemId)) set.remove(itemId)
        sp.edit().putStringSet(todayKey(), set).apply()
        // 顺手清理非当日键，避免膨胀
        val today = todayKey()
        sp.all.keys.filter { it.startsWith("checks_") && it != today }
            .forEach { sp.edit().remove(it).apply() }
    }
}
