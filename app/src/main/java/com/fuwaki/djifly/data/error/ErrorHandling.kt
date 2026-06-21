package com.fuwaki.djifly.data.error

import android.util.Log
import com.fuwaki.djifly.data.models.ApiResult

private const val TAG = "ApiError"

/**
 * ViewModel 统一错误处理扩展
 *
 * 用法：
 * ```kotlin
 * try {
 *     val response = api.getTaskSquare()
 *     val result = response.handleApiResponse(
 *         onSuccess = { data -> _tasks.value = data?.tasks ?: emptyList() },
 *         onError = { error -> _actionState.value = ActionState.Error(error.message) }
 *     )
 * } catch (e: Exception) {
 *     e.handleException(errorHandler) { error ->
 *         _actionState.value = ActionState.Error(error.message)
 *     }
 * }
 * ```

 * 或更简单的用法：
 * ```kotlin
 * apiCall(errorHandler) { api.getTaskSquare() }
 *     .onSuccess { data -> _tasks.value = data?.tasks ?: emptyList() }
 *     .onError { error -> _actionState.value = ActionState.Error(error.message) }
 * ```
 */

/**
 * 处理 API 响应：解析成功/失败，自动发射全局错误
 *
 * @return 成功时返回 data，失败时返回 null
 */
fun <T> retrofit2.Response<ApiResult<T>>.handleApiResponse(
    errorHandler: GlobalErrorHandler,
    onSuccess: (T?) -> Unit
): T? {
    return if (isSuccessful) {
        val body = body()
        if (body != null && body.success) {
            onSuccess(body.data)
            body.data
        } else {
            val error = body?.toAppError() ?: AppError.Unknown("响应解析失败")
            Log.e(TAG, "API error: ${error.message}")
            errorHandler.emit(error)
            null
        }
    } else {
        val error = toAppError()
        Log.e(TAG, "HTTP error ${code()}: ${error.message}")
        errorHandler.emit(error)
        null
    }
}

/**
 * 处理网络异常：统一捕获并发射全局错误
 */
fun <T> Throwable.handleException(
    errorHandler: GlobalErrorHandler,
    onError: ((AppError) -> Unit)? = null
) {
    val error = toAppError()
    Log.e(TAG, "Exception: ${error.message}", this)
    errorHandler.emit(error)
    onError?.invoke(error)
}

/**
 * 流式 API 调用封装
 *
 * 用法：
 * ```
 * apiCall(errorHandler) { api.getTaskSquare() }
 *     .onSuccess { data -> ... }
 *     .onError { error -> ... }
 * ```
 */
class ApiCallResult<T>(
    private val data: T?,
    private val error: AppError?,
    private val errorHandler: GlobalErrorHandler
) {
    fun onSuccess(block: (T) -> Unit): ApiCallResult<T> {
        if (data != null && error == null) block(data)
        return this
    }

    fun onError(block: (AppError) -> Unit): ApiCallResult<T> {
        if (error != null) block(error)
        return this
    }

    val isSuccess: Boolean get() = error == null && data != null
}

suspend fun <T> apiCall(
    errorHandler: GlobalErrorHandler,
    block: suspend () -> retrofit2.Response<ApiResult<T>>
): ApiCallResult<T> {
    return try {
        val response = block()
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null && body.success) {
                ApiCallResult(body.data, null, errorHandler)
            } else {
                val error = body?.toAppError() ?: AppError.Unknown("响应解析失败")
                errorHandler.emit(error)
                ApiCallResult(null, error, errorHandler)
            }
        } else {
            val error = response.toAppError()
            errorHandler.emit(error)
            ApiCallResult(null, error, errorHandler)
        }
    } catch (e: Exception) {
        val error = e.toAppError()
        errorHandler.emit(error)
        ApiCallResult(null, error, errorHandler)
    }
}
