package com.fuwaki.djifly.data.error

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全局错误处理器
 *
 * ViewModel 或 Repository 中捕获错误后调用 [emit]，UI 层通过 [errors] Flow 观察并展示。
 */
@Singleton
class GlobalErrorHandler @Inject constructor() {

    private val _errors = MutableSharedFlow<AppError>(extraBufferCapacity = 8)
    val errors: SharedFlow<AppError> = _errors.asSharedFlow()

    /**
     * 发射一个错误事件，UI 层会弹出对应的提示/对话框
     */
    fun emit(error: AppError) {
        _errors.tryEmit(error)
    }

    /**
     * 便捷方法：从 Response 解析错误并发射
     */
    fun <T> emitFromResponse(response: retrofit2.Response<T>) {
        emit(response.toAppError())
    }

    /**
     * 便捷方法：从异常发射
     */
    fun emitFromException(e: Throwable) {
        emit(e.toAppError())
    }
}
