package com.fuwaki.djifly.data.error

/**
 * 业务错误码 → 飞手可操作中文文案（纯 Kotlin，可 JVM 单测）。
 */
object AppErrorMapping {

    const val DELIVERY_EVIDENCE_REQUIRED_CODE = "DELIVERY_EVIDENCE_REQUIRED"

    const val DELIVERY_EVIDENCE_REQUIRED_MESSAGE =
        "请先拍摄并上传送达凭证，再点击「确认送达」"

    fun userMessageFor(error: AppError): String = when (error) {
        is AppError.Business -> businessMessage(error)
        else -> error.message
    }

    fun businessMessage(error: AppError.Business): String = when (error.errorCode) {
        DELIVERY_EVIDENCE_REQUIRED_CODE -> DELIVERY_EVIDENCE_REQUIRED_MESSAGE
        else -> error.message
    }
}
