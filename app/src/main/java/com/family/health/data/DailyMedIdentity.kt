// 契约：docs/02-数据库与同步.md §3.15 daily_med_items.id 为独立执行行 UUID
package com.family.health.data

import java.util.UUID

/** 新建今日用药执行行主键；禁止复用 medication_item_id。 */
fun newDailyMedExecutionId(): String = UUID.randomUUID().toString()

/**
 * 保存今日用药时解析应写入的行 id：
 * - 已有活跃执行行 → 复用其 id（update）
 * - 否则用调用方提出的独立 UUID；若提出 id 非法复用了药品 id → 另发新 UUID
 */
fun resolveDailyMedRowId(
    existingActiveId: String?,
    proposedId: String,
    medicationItemId: String,
): String {
    if (existingActiveId != null) return existingActiveId
    if (proposedId.isNotBlank() && proposedId != medicationItemId) return proposedId
    return newDailyMedExecutionId()
}

/** 历史坏数据特征：执行行 id 被写成了药品条目 id。 */
fun isCorruptDailyMedIdentity(id: String, medicationItemId: String): Boolean =
    id.isNotBlank() && id == medicationItemId
