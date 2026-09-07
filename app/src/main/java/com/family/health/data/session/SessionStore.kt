// 会话持久化（SharedPreferences）：服务器配置 + 设备令牌 + 同步游标。契约：docs/02 §3.2、§4.1
package com.family.health.data.session

import android.content.Context
import android.content.SharedPreferences

class SessionStore(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("family_health_session", Context.MODE_PRIVATE)

    var server: String
        get() = sp.getString(K_SERVER, "") ?: ""
        set(v) = sp.edit().putString(K_SERVER, v.trimEnd('/')).apply()

    var secret: String
        get() = sp.getString(K_SECRET, "") ?: ""
        set(v) = sp.edit().putString(K_SECRET, v).apply()

    var token: String
        get() = sp.getString(K_TOKEN, "") ?: ""
        set(v) = sp.edit().putString(K_TOKEN, v).apply()

    var deviceId: String
        get() = sp.getString(K_DEVICE_ID, "") ?: ""
        set(v) = sp.edit().putString(K_DEVICE_ID, v).apply()

    var deviceName: String
        get() = sp.getString(K_DEVICE_NAME, "") ?: ""
        set(v) = sp.edit().putString(K_DEVICE_NAME, v).apply()

    var familyId: String
        get() = sp.getString(K_FAMILY_ID, "") ?: ""
        set(v) = sp.edit().putString(K_FAMILY_ID, v).apply()

    var familyName: String
        get() = sp.getString(K_FAMILY_NAME, "我的家庭") ?: "我的家庭"
        set(v) = sp.edit().putString(K_FAMILY_NAME, v).apply()

    /** 下行游标（02 §4.1：lastSeq，首次 0 全量） */
    var lastSeq: Long
        get() = sp.getLong(K_LAST_SEQ, 0L)
        set(v) = sp.edit().putLong(K_LAST_SEQ, v).apply()

    var currentMemberId: String
        get() = sp.getString(K_MEMBER, "") ?: ""
        set(v) = sp.edit().putString(K_MEMBER, v).apply()

    val isConfigured: Boolean
        get() = server.isNotEmpty() && secret.isNotEmpty() && token.isNotEmpty()

    /** 重新配置：清令牌与游标（保留 server/secret 便于改署名后重连） */
    fun resetAuth() = sp.edit()
        .remove(K_TOKEN).remove(K_DEVICE_ID).remove(K_FAMILY_ID)
        .putLong(K_LAST_SEQ, 0L).apply()

    private companion object {
        const val K_SERVER = "server"
        const val K_SECRET = "secret"
        const val K_TOKEN = "token"
        const val K_DEVICE_ID = "device_id"
        const val K_DEVICE_NAME = "device_name"
        const val K_FAMILY_ID = "family_id"
        const val K_FAMILY_NAME = "family_name"
        const val K_LAST_SEQ = "last_seq"
        const val K_MEMBER = "current_member"
    }
}
