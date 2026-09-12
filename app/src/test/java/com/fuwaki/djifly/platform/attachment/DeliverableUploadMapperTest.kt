package com.fuwaki.djifly.platform.attachment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 交付物上传结果→用户文案映射纯单元测试（1B-9b，t43 契约语义）。
 * 覆盖验收标准「上传状态映射」：503 未配置降级、后端 message 透传、兜底与 PUT 失败文案。
 */
class DeliverableUploadMapperTest {

    private fun errorBodyJson(errorCode: String?, message: String?): String {
        val codeField = errorCode?.let { "\"errorCode\":\"$it\"," } ?: ""
        val msgField = message?.let { "\"message\":\"$it\"" } ?: ""
        return """{"success":false,"code":400,$codeField$msgField}"""
    }

    @Test
    fun http_503_maps_to_storage_not_configured_message() {
        // t43：MinIO 未配置时安全降级 503 INTERNAL_ERROR
        val outcome = DeliverableUploadMapper.outcomeFromHttpResponse(
            503,
            errorBodyJson("INTERNAL_ERROR", "对象存储未配置，请联系管理员")
        )

        assertTrue(outcome is DeliverableUploadOutcome.Failed)
        assertEquals(
            "对象存储未配置，请联系管理员稍后再试",
            (outcome as DeliverableUploadOutcome.Failed).reason
        )
    }

    @Test
    fun http_503_with_garbage_body_still_maps_to_configured_message() {
        val outcome = DeliverableUploadMapper.outcomeFromHttpResponse(503, "not-json")

        assertEquals("对象存储未配置，请联系管理员稍后再试", (outcome as DeliverableUploadOutcome.Failed).reason)
    }

    @Test
    fun http_403_no_permission_passes_backend_message_through() {
        val outcome = DeliverableUploadMapper.outcomeFromHttpResponse(
            403,
            errorBodyJson("NO_PERMISSION", "无权执行此操作")
        )

        assertEquals("无权执行此操作", (outcome as DeliverableUploadOutcome.Failed).reason)
    }

    @Test
    fun http_400_invalid_param_passes_backend_message_through() {
        val outcome = DeliverableUploadMapper.outcomeFromHttpResponse(
            400,
            errorBodyJson("INVALID_PARAM", "不支持的文件类型")
        )

        assertEquals("不支持的文件类型", (outcome as DeliverableUploadOutcome.Failed).reason)
    }

    @Test
    fun http_error_without_message_uses_fallback_with_code() {
        val outcome = DeliverableUploadMapper.outcomeFromHttpResponse(400, "{}")

        assertEquals("上传凭证获取失败 (400)", (outcome as DeliverableUploadOutcome.Failed).reason)
    }

    @Test
    fun put_failure_uses_retry_hint() {
        assertEquals(DeliverableUploadMapper.PUT_FAILURE_MESSAGE, DeliverableUploadMapper.putFailure().reason)
        assertEquals("上传失败，请重试", DeliverableUploadMapper.putFailure().reason)
    }

    @Test
    fun network_failure_uses_retry_hint() {
        assertEquals("网络不可用，上传失败请重试", DeliverableUploadMapper.networkFailure().reason)
    }

    @Test
    fun ticket_missing_uses_retry_hint() {
        assertEquals("未获取到上传凭证，请重试", DeliverableUploadMapper.ticketMissing().reason)
    }
}
