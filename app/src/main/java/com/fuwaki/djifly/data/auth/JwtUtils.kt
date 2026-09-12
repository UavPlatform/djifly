package com.fuwaki.djifly.data.auth

import com.google.gson.Gson

/**
 * JWT 工具（纯 Kotlin，可 JVM 单测）。
 *
 * 用于 WS 鉴权失败分类（t16）：本地 exp 预检——token 已过期则先 refresh 再重试，
 * 未过期仍被拒（角色/绑定类）则直接长退避/提示重登，避免无谓刷新。
 *
 * base64url 解码为手写实现：android.util.Base64 依赖 Android runtime（JVM 单测不可用），
 * java.util.Base64 需要 API 26+（本项目 minSdk 24），手写解码器两端兼容。
 */
object JwtUtils {

    private val gson = Gson()

    /**
     * 解析 JWT payload 的 exp 字段（Unix 秒）。
     * @return exp 秒数；token 无效/无 exp 返回 null
     */
    fun parseJwtExpSeconds(token: String?): Long? {
        if (token.isNullOrBlank()) return null
        val parts = token.split(".")
        if (parts.size < 2) return null

        val payloadBytes = decodeBase64Url(parts[1]) ?: return null
        return runCatching {
            val map = gson.fromJson(String(payloadBytes, Charsets.UTF_8), Map::class.java)
            (map["exp"] as? Number)?.toLong()
        }.getOrNull()
    }

    /**
     * 本地判断 JWT 是否已过期（exp*1000 <= nowMillis）。
     * - token 为空 → 已过期（无可用凭证）
     * - 无法解析 exp（非 JWT/无该字段）→ 返回 false（无法证明过期，不触发刷新）
     */
    fun isJwtExpired(token: String?, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val exp = parseJwtExpSeconds(token) ?: return token.isNullOrBlank()
        return exp * 1000L <= nowMillis
    }

    /**
     * base64url（RFC 4648 §5，兼标准 base64 字符）解码；
     * 容忍 padding，非法输入返回 null。
     */
    internal fun decodeBase64Url(input: String): ByteArray? {
        if (input.isEmpty()) return null
        val out = ArrayList<Byte>(input.length * 3 / 4 + 3)
        var buffer = 0
        var bits = 0
        for (c in input) {
            if (c == '=') break
            val value = when (c) {
                in 'A'..'Z' -> c - 'A'
                in 'a'..'z' -> c - 'a' + 26
                in '0'..'9' -> c - '0' + 52
                '+', '-' -> 62
                '/', '_' -> 63
                else -> return null
            }
            buffer = (buffer shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.add(((buffer shr bits) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }
}
