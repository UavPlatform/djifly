package com.fuwaki.djifly.platform.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * HTTP errorBody 解析器纯单元测试（1B-2c：泛化解析 errorCode/message）。
 * 覆盖验收标准①：非 2xx 响应 errorBody 中的 errorCode/message 被解析（400 不再丢失）。
 */
class ApiErrorBodyParserTest {

    @Test
    fun parses_task_not_paid_error_code_and_message() {
        // 后端契约：订单未支付类错误 → 400 + TASK_NOT_PAID（错误体结构与 t22 一致）
        val body = """{"success":false,"code":400,"errorCode":"TASK_NOT_PAID","message":"任务尚未支付，支付后将进入接单大厅"}"""

        val parsed = ApiErrorBodyParser.parse(body)

        assertEquals("TASK_NOT_PAID", parsed!!.errorCode)
        assertEquals("任务尚未支付，支付后将进入接单大厅", parsed.message)
    }

    @Test
    fun parses_message_when_error_code_missing() {
        val parsed = ApiErrorBodyParser.parse("""{"success":false,"message":"任务已被接单"}""")

        assertNull(parsed!!.errorCode)
        assertEquals("任务已被接单", parsed.message)
    }

    @Test
    fun blank_fields_are_normalized_to_null() {
        val parsed = ApiErrorBodyParser.parse("""{"errorCode":"","message":""}""")

        assertNull(parsed!!.errorCode)
        assertNull(parsed.message)
    }

    @Test
    fun garbage_or_empty_body_returns_null() {
        assertNull(ApiErrorBodyParser.parse(null))
        assertNull(ApiErrorBodyParser.parse(""))
        assertNull(ApiErrorBodyParser.parse("   "))
        assertNull(ApiErrorBodyParser.parse("not-json{{{"))
        assertNull(ApiErrorBodyParser.parse("[1,2,3]"))   // 顶层非对象
    }

    @Test
    fun extra_fields_are_ignored() {
        val body = """{"success":false,"code":400,"errorCode":"TASK_NOT_PAID","message":"x","orderNum":"NO-1","data":null}"""

        val parsed = ApiErrorBodyParser.parse(body)

        assertEquals("TASK_NOT_PAID", parsed!!.errorCode)
        assertEquals("x", parsed.message)
    }
}
