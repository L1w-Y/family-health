// 契约：docs/02-数据库与同步.md §4（同步策略）。本期仅占位：接口 + 本地 fake，后续接真同步。
package com.family.health.syncclient

/** 同步载荷行（契约 §4.1：{table, row} 数组） */
data class SyncRow(
    val table: String,
    val rowId: String,
    val deleted: Boolean = false,
    val payload: Map<String, Any?> = emptyMap(),
)

/** 同步结果 */
sealed interface SyncResult {
    data object Ok : SyncResult
    data class Pending(val reason: String) : SyncResult
    data class Failed(val error: String) : SyncResult
}

/**
 * 同步客户端接口（架构占位）。
 * 下行：GET /sync?since={lastSeq}；上行：携带客户端 UUID + Idempotency-Key。
 * Room 与网络层本期不实现。
 */
interface SyncClient {
    /** 当前本地已应用的 seq */
    val lastSeq: Long

    /** 下行增量拉取（since=0 为首次全量） */
    suspend fun pull(since: Long, limit: Int = 500): List<SyncRow>

    /** 上行单批变更 */
    suspend fun push(rows: List<SyncRow>, idempotencyKey: String): SyncResult
}

/** 本地 fake 实现：全部成功、无远端数据 */
class FakeSyncClient : SyncClient {
    override val lastSeq: Long = 0

    override suspend fun pull(since: Long, limit: Int): List<SyncRow> = emptyList()

    override suspend fun push(rows: List<SyncRow>, idempotencyKey: String): SyncResult = SyncResult.Ok
}
