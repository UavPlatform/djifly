package com.fuwaki.djifly.data.error

import com.fuwaki.djifly.data.models.ApiResult
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/**
 * 错误装配方（toAppError）纯单元测试（1B-2c：泛化解析接入点）。
 * 用 retrofit2 内存响应构造，不起网络；覆盖验收标准①③：
 * - 非 2xx errorBody 的 errorCode/message 被解析并透传（400 不再丢失）
 * - 其他错误码行为不变（401/429/5xx/无 errorCode 兜底），既有提示不劣化
 */
class AppErrorMappingTest {

    private fun errorResponse(code: Int, errorBody: String?): Response<Unit> {
        return Response.error(
            code,
            ResponseBody.create(null, errorBody ?: "")
        )
    }

    private val taskNotPaidBody =
        """{"success":false,"code":400,"errorCode":"TASK_NOT_PAID","message":"任务尚未支付，支付后将进入接单大厅"}"""

    @Test
    fun http_400_with_task_not_paid_maps_to_business_error() {
        val error = errorResponse(400, taskNotPaidBody).toAppError()

        assertTrue(error is AppError.Business)
        error as AppError.Business
        assertEquals("TASK_NOT_PAID", error.errorCode)
        assertEquals("任务尚未支付，支付后将进入接单大厅", error.message)
    }

    @Test
    fun http_400_without_error_code_keeps_http_error() {
        val error = errorResponse(400, """{"success":false,"message":"任务已被接单"}""").toAppError()

        assertTrue(error is AppError.Http)
        error as AppError.Http
        assertEquals(400, error.code)
        assertEquals("任务已被接单", error.message)
    }

    @Test
    fun http_400_without_body_keeps_legacy_fallback_text() {
        val error = errorResponse(400, null).toAppError()

        assertTrue(error is AppError.Http)
        assertEquals("请求失败 (400)", error.message)
    }

    @Test
    fun unauthorized_branch_unchanged() {
        val error = errorResponse(401, """{"message":"token 已失效"}""").toAppError()

        assertTrue(error is AppError.Unauthorized)
        assertEquals("token 已失效", error.message)
    }

    @Test
    fun drone_required_branch_unchanged() {
        val error = errorResponse(401, """{"message":"未绑定无人机"}""").toAppError()

        assertTrue(error is AppError.DroneRequired)
    }

    @Test
    fun rate_limited_branch_unchanged() {
        val error = errorResponse(429, """{"message":"操作过于频繁"}""").toAppError()

        assertTrue(error is AppError.RateLimited)
        assertEquals("操作过于频繁", error.message)
    }

    @Test
    fun server_error_branch_unchanged() {
        val error = errorResponse(500, """{"message":"internal"}""").toAppError()

        assertTrue(error is AppError.Http)
        assertEquals("服务器错误 (500)", error.message)
    }

    @Test
    fun result_envelope_success_false_maps_business_with_error_code() {
        // 2xx 信封 success=false 路径：errorCode 同样透传（1B-2c 扩展）
        val error = ApiResult<Unit>(
            success = false,
            code = 400,
            errorCode = "TASK_NOT_PAID",
            message = "任务尚未支付，支付后将进入接单大厅"
        ).toAppError()

        assertTrue(error is AppError.Business)
        error as AppError.Business
        assertEquals("TASK_NOT_PAID", error.errorCode)
        assertEquals("任务尚未支付，支付后将进入接单大厅", error.message)
    }

    @Test
    fun result_envelope_without_error_code_keeps_business_without_code() {
        val error = ApiResult<Unit>(success = false, code = 400, message = "任务已被接单").toAppError()

        assertTrue(error is AppError.Business)
        assertNull((error as AppError.Business).errorCode)
        assertEquals("任务已被接单", error.message)
    }
}
