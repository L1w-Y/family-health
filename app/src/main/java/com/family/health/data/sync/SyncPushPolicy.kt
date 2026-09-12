// 契约：docs/02-数据库与同步.md §4.2 上行分批；失败不阻断后续批次与下行
package com.family.health.data.sync

/** 按 idemKey 首次出现顺序分组（保持 outbox 时间序）。 */
fun orderedOutboxBatches(idemKeysInOrder: List<String>): List<String> {
    val seen = linkedSetOf<String>()
    idemKeysInOrder.forEach { seen += it }
    return seen.toList()
}

data class ContinuingBatchResult(
    val succeededIndices: List<Int>,
    val errors: List<String>,
) {
    val allSucceeded: Boolean get() = errors.isEmpty()
}

/**
 * 逐批执行：某批失败只记错并继续后续批次（不整体中断）。
 * [runBatch] 抛异常视为该批失败。
 */
suspend fun <T> runContinuingBatches(batches: List<T>, runBatch: suspend (T) -> Unit): ContinuingBatchResult {
    val succeeded = mutableListOf<Int>()
    val errors = mutableListOf<String>()
    batches.forEachIndexed { index, batch ->
        try {
            runBatch(batch)
            succeeded += index
        } catch (e: Exception) {
            errors += e.message?.takeIf { it.isNotBlank() } ?: "批次失败"
        }
    }
    return ContinuingBatchResult(succeeded, errors)
}

/** 合并推/拉阶段错误文案；任一侧失败都不吞掉另一侧结果。 */
fun combineSyncPhaseErrors(pushError: String?, pullError: String?): String? {
    val parts = buildList {
        if (!pushError.isNullOrBlank()) add("上行：$pushError")
        if (!pullError.isNullOrBlank()) add("下行：$pullError")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString("；")
}
