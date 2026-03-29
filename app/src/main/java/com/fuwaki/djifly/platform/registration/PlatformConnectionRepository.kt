package com.fuwaki.djifly.platform.registration

import com.fuwaki.djifly.platform.network.PlatformApiErrorResponse
import com.fuwaki.djifly.platform.network.PlatformApiService
import com.google.gson.Gson
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlatformConnectionRepository @Inject constructor(
    private val apiService: PlatformApiService,
    private val gson: Gson
) {

    suspend fun requestWebSocketConnection(deviceId: String): PlatformConnectionResult {
        return try {
            val response = apiService.requestDroneWebSocketConnection(deviceId)
            if (response.isSuccessful && response.body()?.success == true) {
                PlatformConnectionResult.Success(
                    message = response.body()?.message?.takeIf { it.isNotBlank() } ?: "WebSocket 连接申请已提交"
                )
            } else {
                parseFailure(
                    code = response.code(),
                    errorBody = response.errorBody()?.string()
                )
            }
        } catch (exception: IllegalStateException) {
            PlatformConnectionResult.Failure(
                message = exception.message ?: "平台连接配置异常",
                retryable = false
            )
        } catch (exception: IOException) {
            PlatformConnectionResult.Failure(
                message = exception.message ?: "网络不可用，无法申请平台连接",
                retryable = true
            )
        } catch (exception: HttpException) {
            parseFailure(
                code = exception.code(),
                errorBody = exception.response()?.errorBody()?.string()
            )
        } catch (exception: Exception) {
            PlatformConnectionResult.Failure(
                message = exception.message ?: "平台连接申请失败",
                retryable = false
            )
        }
    }

    private fun parseFailure(
        code: Int,
        errorBody: String?
    ): PlatformConnectionResult {
        val body = errorBody
            ?.takeIf { it.isNotBlank() }
            ?.let { gson.fromJson(it, PlatformApiErrorResponse::class.java) }

        return PlatformConnectionResult.Failure(
            message = body?.message?.takeIf { it.isNotBlank() } ?: "平台连接申请失败（HTTP $code）",
            retryable = code >= 500
        )
    }
}
