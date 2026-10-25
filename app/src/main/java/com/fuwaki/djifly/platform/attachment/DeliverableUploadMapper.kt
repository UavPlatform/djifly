package com.fuwaki.djifly.platform.attachment

import com.fuwaki.djifly.data.error.AppErrorMapping
import com.fuwaki.djifly.platform.network.ApiErrorBodyParser

/** 单个交付物的上传结果（纯 Kotlin，可 JVM 单测） */
sealed interface DeliverableUploadOutcome {
    /** 直传成功，附件已登记并与任务关联 */
    data class Uploaded(val materialId: String, val fileName: String) : DeliverableUploadOutcome

    /** 类型/大小预检拒绝（本地规则），不可重试同一文件 */
    data class Rejected(val reason: String) : DeliverableUploadOutcome

    /** 凭证获取或直传失败，可重试 */
    data class Failed(val reason: String) : DeliverableUploadOutcome
}

/**
 * 交付物上传结果 → 用户文案映射（纯 Kotlin，t43 契约语义）。
 */
object DeliverableUploadMapper {

    const val PUT_FAILURE_MESSAGE = "上传失败，请重试"
    const val TICKET_MISSING_MESSAGE = "未获取到上传凭证，请重试"
    const val NETWORK_FAILURE_MESSAGE = "网络不可用，上传失败请重试"
    const val STORAGE_NOT_CONFIGURED_MESSAGE = "对象存储未配置，请联系管理员稍后再试"

    /**
     * upload-url 端点非 2xx → 失败文案。
     * 503 INTERNAL_ERROR = MinIO 未配置（t43 安全降级）；其余透传后端 message。
     */
    fun outcomeFromHttpResponse(httpCode: Int, rawErrorBody: String?): DeliverableUploadOutcome.Failed {
        val parsed = ApiErrorBodyParser.parse(rawErrorBody)
        val reason = when {
            httpCode == 503 -> STORAGE_NOT_CONFIGURED_MESSAGE
            parsed?.errorCode == AppErrorMapping.DELIVERY_EVIDENCE_REQUIRED_CODE ->
                AppErrorMapping.DELIVERY_EVIDENCE_REQUIRED_MESSAGE
            parsed?.message != null -> parsed.message
            else -> "上传凭证获取失败 ($httpCode)"
        }
        return DeliverableUploadOutcome.Failed(reason)
    }

    fun networkFailure(): DeliverableUploadOutcome.Failed =
        DeliverableUploadOutcome.Failed(NETWORK_FAILURE_MESSAGE)

    fun putFailure(): DeliverableUploadOutcome.Failed =
        DeliverableUploadOutcome.Failed(PUT_FAILURE_MESSAGE)

    fun ticketMissing(): DeliverableUploadOutcome.Failed =
        DeliverableUploadOutcome.Failed(TICKET_MISSING_MESSAGE)
}
