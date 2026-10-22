package com.fuwaki.djifly.data.error

import com.fuwaki.djifly.data.models.ApiResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

/** REQ-DJIFLY-001: completion succeeds with the backend's Result<Void> envelope. */
class ApiCallEmptyDataTest {
    @Test fun completionEnvelopeWithNullDataCallsSuccess() = runBlocking {
        val type = object : TypeToken<ApiResult<Unit>>() {}.type
        val body: ApiResult<Unit> = Gson().fromJson(
            """{"success":true,"code":200,"message":"任务已完成","data":null}""", type)
        var successes = 0
        var failures = 0
        val result = apiCall(GlobalErrorHandler(), emptySuccessValue = Unit) { Response.success(body) }
            .onSuccess { successes++ }.onError { failures++ }
        assertTrue(result.isSuccess)
        assertEquals(1, successes)
        assertEquals(0, failures)
    }

    @Test fun missingDataDoesNotInventDataForQueries() = runBlocking {
        val result = apiCall(GlobalErrorHandler()) {
            Response.success(ApiResult<String>(success = true, code = 200))
        }
        assertFalse(result.isSuccess)
    }

    @Test fun businessFailureIsStillFailure() = runBlocking {
        var error: AppError? = null
        val result = apiCall(GlobalErrorHandler(), emptySuccessValue = Unit) {
            Response.success(ApiResult<Unit>(success = false, code = 400,
                errorCode = "DELIVERY_EVIDENCE_REQUIRED", message = "请先上传凭证"))
        }.onSuccess { fail("Business failure must not complete delivery") }.onError { error = it }
        assertFalse(result.isSuccess)
        assertEquals("DELIVERY_EVIDENCE_REQUIRED", (error as AppError.Business).errorCode)
    }

    @Test fun httpFailureIsStillFailure() = runBlocking {
        val result = apiCall(GlobalErrorHandler(), emptySuccessValue = Unit) {
            Response.error<ApiResult<Unit>>(409,
                """{"success":false,"errorCode":"ORDER_STATUS_INVALID","message":"状态不允许"}""".toResponseBody())
        }.onSuccess { fail("HTTP error must not complete delivery") }
        assertFalse(result.isSuccess)
    }

    @Test fun missingEnvelopeIsStillFailure() = runBlocking {
        val result = apiCall(GlobalErrorHandler(), emptySuccessValue = Unit) {
            Response.success<ApiResult<Unit>>(null)
        }.onSuccess { fail("Missing envelope must not complete delivery") }
        assertFalse(result.isSuccess)
    }

    @Test fun populatedDataIsPreserved() = runBlocking {
        var received: String? = null
        apiCall(GlobalErrorHandler(), emptySuccessValue = "fallback") {
            Response.success(ApiResult(success = true, code = 200, data = "actual"))
        }.onSuccess { received = it }
        assertEquals("actual", received)
    }

    @Test fun cancellationIsNotReportedAsAnOperationFailure() = runBlocking {
        try {
            apiCall<Unit>(GlobalErrorHandler(), emptySuccessValue = Unit) { throw CancellationException("cancelled") }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            // Coroutine cancellation is lifecycle control, not a business failure.
        }
    }
}
