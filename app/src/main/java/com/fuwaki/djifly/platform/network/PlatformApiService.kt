package com.fuwaki.djifly.platform.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface PlatformApiService {

    @POST("appUav/add")
    suspend fun registerDrone(
        @Body request: RegisterDroneRequest
    ): Response<RegisterDroneResponse>
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
