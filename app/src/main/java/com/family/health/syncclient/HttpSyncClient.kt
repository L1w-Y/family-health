// 真实同步客户端（HttpURLConnection 实现）。契约：docs/02 §3.2（auth）、§4（sync）；docs/03（import）
package com.family.health.syncclient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

/** 服务端业务错误（含服务端 message，供 toast 展示） */
class ApiException(val code: String, message: String) : Exception(message)

data class AuthResult(val token: String, val deviceId: String, val familyId: String)

/** 下行单条变更（table + 全字段行） */
data class ChangeRow(val table: String, val row: JsonObject)
data class PullResult(val changes: List<ChangeRow>, val next: Long)

/** 上行单条操作 */
data class WriteOp(val table: String, val op: String, val row: JsonObject)

data class ImportResult(val eventIds: List<String>, val reportCount: Int, val indicatorCount: Int)

class HttpSyncClient {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun auth(server: String, secret: String, displayName: String): AuthResult =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("secret", secret)
                put("display_name", displayName)
            }.toString()
            val resp = request("POST", "$server/auth", body, token = null, idemKey = null)
            val obj = json.parseToJsonElement(resp).jsonObject
            val dev = obj["device"]!!.jsonObject
            AuthResult(
                token = obj["token"]!!.jsonPrimitive.content,
                deviceId = dev["id"]!!.jsonPrimitive.content,
                familyId = dev["family_id"]!!.jsonPrimitive.content,
            )
        }

    suspend fun pull(server: String, token: String, since: Long, limit: Int = 500): PullResult =
        withContext(Dispatchers.IO) {
            val resp = request("GET", "$server/sync?since=$since&limit=$limit", null, token, idemKey = null)
            val obj = json.parseToJsonElement(resp).jsonObject
            val changes = obj["changes"]!!.jsonArray.map { el ->
                val c = el.jsonObject
                ChangeRow(c["table"]!!.jsonPrimitive.content, c["row"]!!.jsonObject)
            }
            PullResult(changes, obj["next"]!!.jsonPrimitive.long)
        }

    suspend fun push(server: String, token: String, idemKey: String, ops: List<WriteOp>) =
        withContext(Dispatchers.IO) {
            val arr = ops.map { op ->
                buildJsonObject {
                    put("table", op.table)
                    put("op", op.op)
                    put("row", op.row)
                }
            }
            val body = if (ops.size == 1) arr[0].toString()
            else kotlinx.serialization.json.JsonArray(arr).toString()
            request("POST", "$server/sync", body, token, idemKey)
            Unit
        }

    /** 导入（dryRun=true 只校验不写库，03 §2） */
    suspend fun importCall(
        server: String, token: String, idemKey: String, envelopeJson: String, dryRun: Boolean,
    ): ImportResult = withContext(Dispatchers.IO) {
        val envObj = json.parseToJsonElement(envelopeJson).jsonObject
        val finalObj = if (dryRun) {
            JsonObject(envObj + ("dry_run" to kotlinx.serialization.json.JsonPrimitive(true)))
        } else envObj
        val resp = request("POST", "$server/api/v1/import", finalObj.toString(), token, idemKey)
        val obj = json.parseToJsonElement(resp).jsonObject
        val created = obj["created"]!!.jsonObject
        ImportResult(
            eventIds = created["event_ids"]!!.jsonArray.map { it.jsonPrimitive.content },
            reportCount = created["report_count"]!!.jsonPrimitive.int,
            indicatorCount = created["indicator_count"]!!.jsonPrimitive.int,
        )
    }

    /** 统一请求；非 2xx 时抽取服务端错误码抛出 ApiException */
    private fun request(
        method: String, url: String, body: String?, token: String?, idemKey: String?,
    ): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            if (body != null) setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            if (idemKey != null) setRequestProperty("Idempotency-Key", idemKey)
            if (body != null) doOutput = true
        }
        try {
            if (body != null) {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) {
                val errCode = runCatching {
                    json.parseToJsonElement(text).jsonObject["errors"]!!.jsonArray[0]
                        .jsonObject["code"]!!.jsonPrimitive.content
                }.getOrDefault("HTTP_$code")
                val errMsg = runCatching {
                    json.parseToJsonElement(text).jsonObject["errors"]!!.jsonArray[0]
                        .jsonObject["message"]!!.jsonPrimitive.content
                }.getOrDefault(text.ifEmpty { "HTTP $code" })
                throw ApiException(errCode, errMsg)
            }
            return text
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** 从响应 ok 字段快速判定（备用） */
        fun isOk(json: Json, text: String): Boolean = runCatching {
            json.parseToJsonElement(text).jsonObject["ok"]!!.jsonPrimitive.boolean
        }.getOrDefault(false)
    }
}
