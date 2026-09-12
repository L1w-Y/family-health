// 契约：docs/02-数据库与同步.md §3.15；一次性清理 id==medication_item_id 的坏今日用药队列
package com.family.health.data.sync

import com.family.health.data.isCorruptDailyMedIdentity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val DAILY_MED_ID_REPAIR_META_KEY = "daily_med_id_repair_v1"

data class DailyMedLocalRow(val id: String, val medicationItemId: String, val seq: Long)

data class OutboxScanRow(
    val id: Long,
    val tableName: String,
    val op: String,
    val rowJson: String,
)

data class DailyMedRepairPlan(
    val localIdsToDelete: List<String>,
    val outboxIdsToDelete: List<Long>,
)

private val repairJson = Json { ignoreUnknownKeys = true }

/** 从未同步成功的乐观行：seq=0 且 id 误用药品条目 id。 */
fun corruptOptimisticDailyIds(rows: List<DailyMedLocalRow>): List<String> =
    rows.filter { it.seq == 0L && isCorruptDailyMedIdentity(it.id, it.medicationItemId) }
        .map { it.id }

/**
 * 丢弃坏今日用药 outbox：
 * - insert/update 且 row.id == row.medication_item_id
 * - 或 row.id 属于待删的本地乐观坏行（含仅含 id 的 delete 墓碑操作）
 */
fun planCorruptDailyMedRepair(
    localRows: List<DailyMedLocalRow>,
    outbox: List<OutboxScanRow>,
): DailyMedRepairPlan {
    val localIds = corruptOptimisticDailyIds(localRows).toSet()
    val outboxIds = outbox.mapNotNull { row ->
        if (row.tableName != "daily_med_items") return@mapNotNull null
        val ids = parseDailyMedOutboxIds(row.rowJson) ?: return@mapNotNull null
        val corruptPair = ids.medicationItemId != null &&
            isCorruptDailyMedIdentity(ids.id, ids.medicationItemId)
        val touchesCorruptLocal = ids.id in localIds
        if (corruptPair || touchesCorruptLocal) row.id else null
    }
    return DailyMedRepairPlan(
        localIdsToDelete = localIds.toList(),
        outboxIdsToDelete = outboxIds,
    )
}

private data class ParsedDailyIds(val id: String, val medicationItemId: String?)

private fun parseDailyMedOutboxIds(rowJson: String): ParsedDailyIds? = runCatching {
    val obj = repairJson.parseToJsonElement(rowJson).jsonObject
    val id = obj["id"]?.jsonPrimitive?.content ?: return null
    val medId = obj["medication_item_id"]?.jsonPrimitive?.content
    ParsedDailyIds(id, medId)
}.getOrNull()
