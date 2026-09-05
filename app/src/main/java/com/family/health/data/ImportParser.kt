// 契约：docs/03-导入格式-v1.md；解析逻辑对应 prototype/app.js impParse/impConfirm
package com.family.health.data

import com.family.health.data.model.CheckupEvent
import com.family.health.data.model.IndicatorItem
import com.family.health.data.model.Report
import org.json.JSONObject
import java.util.UUID

class ImportFormatException(message: String) : Exception(message)

/** 解析导入文本为复查事件；非法文本抛 ImportFormatException */
fun parseImportJson(text: String): CheckupEvent {
    val root = try {
        JSONObject(text)
    } catch (e: Exception) {
        throw ImportFormatException("解析失败：不是合法的导入文本")
    }
    if (root.optString("format") != "family-health-import") {
        throw ImportFormatException("解析失败：不是合法的导入文本")
    }
    val payload = root.optJSONObject("payload") ?: throw ImportFormatException("解析失败：不是合法的导入文本")
    val ev = payload.optJSONObject("event") ?: throw ImportFormatException("解析失败：不是合法的导入文本")

    val reports = mutableListOf<Report>()
    val rs = ev.optJSONArray("reports")
    if (rs != null) {
        for (i in 0 until rs.length()) {
            val r = rs.getJSONObject(i)
            val items = mutableListOf<IndicatorItem>()
            val ia = r.optJSONArray("indicators")
            if (ia != null) {
                for (j in 0 until ia.length()) {
                    val it = ia.getJSONObject(j)
                    val raw = it.opt("value")
                    val (num, txt) = when (raw) {
                        is Number -> raw.toDouble() to null
                        else -> null to raw?.toString()
                    }
                    items.add(
                        IndicatorItem(
                            itemName = it.optString("item_name"),
                            valueNumeric = num,
                            valueText = txt,
                            unit = it.optString("unit"),
                            referenceRange = it.optString("reference_range"),
                        )
                    )
                }
            }
            reports.add(
                Report(
                    title = r.optString("title"),
                    reportDate = ev.optString("checkup_date"),
                    attachments = 0,
                    conclusionText = r.optString("conclusion_text"),
                    indicators = items,
                )
            )
        }
    }
    return CheckupEvent(
        id = "e" + UUID.randomUUID().toString().substring(0, 8),
        checkupDate = ev.optString("checkup_date"),
        hospital = ev.optString("hospital"),
        department = ev.optString("department"),
        note = ev.optString("note"),
        nextCheckupDate = ev.optString("next_checkup_date").ifBlank { null },
        medChangeSummary = "",
        reports = reports,
    )
}
