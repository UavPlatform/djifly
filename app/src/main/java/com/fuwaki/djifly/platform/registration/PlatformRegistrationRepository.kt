package com.fuwaki.djifly.platform.registration

import com.fuwaki.djifly.platform.network.PlatformApiService
import com.fuwaki.djifly.platform.network.PlatformNetwork
import com.fuwaki.djifly.platform.network.RegisterDroneRequest
import com.fuwaki.djifly.platform.network.RegisterDroneResponse
import retrofit2.HttpException
import java.io.IOException

class PlatformRegistrationRepository(
    private val apiFactory: () -> PlatformApiService
) {

    private val gson = PlatformNetwork.gson()

    suspend fun registerDrone(
        serialNumber: String,
        controllerModel: String
    ): PlatformRegistrationResult {
        val request = RegisterDroneRequest(
            uavName = serialNumber,
            onlineStatus = ONLINE_STATUS_ONLINE,
            djiId = serialNumber,
            controllerModel = controllerModel
        )

        return try {
            val response = apiFactory().registerDrone(request)
            if (response.isSuccessful) {
                val body = response.body()
                PlatformRegistrationResult.Success(
                    droneId = body?.id,
                    droneName = body?.name?.takeIf { it.isNotBlank() } ?: serialNumber,
                    createdOnServer = true
                )
            } else {
                parseFailure(
                    code = response.code(),
                    errorBody = response.errorBody()?.string(),
                    serialNumber = serialNumber
                )
            }
        } catch (exception: IllegalStateException) {
            PlatformRegistrationResult.Failure(
                message = exception.message ?: "平台注册配置异常",
                retryable = false
            )
        } catch (exception: IOException) {
            PlatformRegistrationResult.Failure(
                message = exception.message ?: "网络不可用，平台注册失败",
                retryable = true
            )
        } catch (exception: HttpException) {
            parseFailure(
                code = exception.code(),
                errorBody = exception.response()?.errorBody()?.string(),
                serialNumber = serialNumber
            )
        } catch (exception: Exception) {
            PlatformRegistrationResult.Failure(
                message = exception.message ?: "平台注册失败",
                retryable = false
            )
        }
    }

    private fun parseFailure(
        code: Int,
        errorBody: String?,
        serialNumber: String
    ): PlatformRegistrationResult {
        val body = errorBody
            ?.takeIf { it.isNotBlank() }
            ?.let { gson.fromJson(it, RegisterDroneResponse::class.java) }
        val message = body?.message?.takeIf { it.isNotBlank() } ?: "平台注册失败（HTTP $code）"

        // 后端 add 接口不是幂等的；如果同一 SN 已存在，这里按“已登记”处理，避免每次重启都被视为失败。
        if (message.contains("已注册") || message.contains("已存在")) {
            return PlatformRegistrationResult.Success(
                droneId = body?.id,
                droneName = body?.name?.takeIf { it.isNotBlank() } ?: serialNumber,
                createdOnServer = false
            )
        }

        return PlatformRegistrationResult.Failure(
            message = message,
            retryable = code >= 500
        )
    }

    private companion object {
        const val ONLINE_STATUS_ONLINE: Char = '1'
    }
}
