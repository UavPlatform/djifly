package com.fuwaki.djifly.platform.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface PlatformApiService {

    @POST("appUav/add")
    suspend fun registerDrone(
        @Body request: RegisterDroneRequest
    ): Response<RegisterDroneResponse>

    @POST("api/ws/request")
    suspend fun requestDroneWebSocketConnection(
        @Query("deviceId") deviceId: String
    ): Response<RequestDroneWebSocketConnectionResponse>
}

data class RegisterDroneRequest(
    val uavName: String,
    val onlineStatus: Char,
    val djiId: String,
    val controllerModel: String
)

data class RegisterDroneResponse(
    val success: Boolean = false,
    val id: Long? = null,
    val name: String? = null,
    val message: String? = null
)

data class RequestDroneWebSocketConnectionResponse(
    val success: Boolean = false,
    val code: String? = null,
    val message: String? = null
)

data class PlatformApiErrorResponse(
    val success: Boolean = false,
    val code: String? = null,
    val id: Long? = null,
    val name: String? = null,
    val message: String? = null
)
