package com.fuwaki.djifly.platform.network

import com.google.gson.Gson

/** 非 2xx errorBody 解析结果：后端 Result 信封中的业务错误码与文案 */
data class ApiErrorBody(
    val errorCode: String?,
    val message: String?
)

/**
 * HTTP errorBody 解析器（纯 Kotlin，可 JVM 单测）。
 *
 * 泛化动机（1B-2c / 全景 P0-2）：apiCall 既有错误链路只取 errorBody 里的 message，
 * 后端 Result 信封的 errorCode（如 1B-2a 新增的 TASK_NOT_PAID）被丢弃，
 * 客户端无法按错误类别做专属呈现。本解析器统一提取 errorCode/message，
 * 供 toAppError 等错误装配方使用，全端点受益。
 */
object ApiErrorBodyParser {

    private val gson = Gson()

    /**
     * @param rawErrorBody errorBody 原文（响应体只能读一次，调用方负责读出后传入）
     * @return 空/非法 JSON/非对象返回 null
     */
    fun parse(rawErrorBody: String?): ApiErrorBody? {
        if (rawErrorBody.isNullOrBlank()) return null
        return runCatching {
            val map = gson.fromJson(rawErrorBody.trim(), Map::class.java) as? Map<*, *>
                ?: return null
            ApiErrorBody(
                errorCode = (map["errorCode"] as? String)?.takeIf { it.isNotBlank() },
                message = (map["message"] as? String)?.takeIf { it.isNotBlank() }
            )
        }.getOrNull()
    }
}
