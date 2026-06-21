package com.fuwaki.djifly.platform.network

import com.fuwaki.djifly.data.models.ApiResult
import com.fuwaki.djifly.data.models.ChatEnvelope
import com.fuwaki.djifly.data.models.CreateSessionRequest
import com.fuwaki.djifly.data.models.LoginRequest
import com.fuwaki.djifly.data.models.LoginResponse
import com.fuwaki.djifly.data.models.MessageVO
import com.fuwaki.djifly.data.models.RefreshResponse
import com.fuwaki.djifly.data.models.RegisterRequest
import com.fuwaki.djifly.data.models.RiderUav
import com.fuwaki.djifly.data.models.SendMessageRequest
import com.fuwaki.djifly.data.models.SessionVO
import com.fuwaki.djifly.data.models.TaskPageVO
import com.fuwaki.djifly.data.models.TaskVo
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface PlatformApiService {

    // ═══════════════════════════════════════════════════════
    // 无人机注册（原有）
    // ═══════════════════════════════════════════════════════

    @POST("appUav/add")
    suspend fun registerDrone(
        @Body request: RegisterDroneRequest
    ): Response<RegisterDroneResponse>

    @POST("api/ws/request")
    suspend fun requestDroneWebSocketConnection(
        @Query("deviceId") deviceId: String
    ): Response<RequestDroneWebSocketConnectionResponse>

    // ═══════════════════════════════════════════════════════
    // 认证
    // ═══════════════════════════════════════════════════════

    @POST("user/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<ApiResult<LoginResponse>>

    @POST("rider/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<ApiResult<LoginResponse>>

    @POST("user/refresh")
    suspend fun refreshToken(
        @Header("Refresh-Token") refreshToken: String
    ): Response<ApiResult<RefreshResponse>>

    // ═══════════════════════════════════════════════════════
    // 任务 — 飞手端
    // ═══════════════════════════════════════════════════════

    @GET("rider/square")
    suspend fun getTaskSquare(): Response<ApiResult<TaskPageVO>>

    @POST("rider/accept")
    suspend fun acceptTask(
        @Query("taskNum") taskNum: String
    ): Response<ApiResult<Unit>>

    @GET("rider/my-tasks")
    suspend fun getMyTasks(): Response<ApiResult<TaskPageVO>>

    @GET("rider/my-tasks/history")
    suspend fun getTaskHistory(): Response<ApiResult<TaskPageVO>>

    @POST("rider/cancel")
    suspend fun cancelTask(
        @Query("taskNum") taskNum: String
    ): Response<ApiResult<Unit>>

    @POST("rider/complete")
    suspend fun completeTask(
        @Query("taskNum") taskNum: String
    ): Response<ApiResult<Unit>>

    @GET("rider/task/detail")
    suspend fun getTaskDetail(
        @Query("taskNum") taskNum: String
    ): Response<ApiResult<TaskVo>>

    // ═══════════════════════════════════════════════════════
    // 聊天
    // ═══════════════════════════════════════════════════════

    @GET("chat/session/list")
    suspend fun getSessions(): Response<ApiResult<List<SessionVO>>>

    @POST("chat/session/create")
    suspend fun createSession(
        @Body request: CreateSessionRequest
    ): Response<ApiResult<SessionVO>>

    @GET("chat/Message/messages/{sessionId}")
    suspend fun getMessages(
        @Path("sessionId") sessionId: Long
    ): Response<ApiResult<List<ChatEnvelope>>>

    @POST("chat/Message/send")
    suspend fun sendMessage(
        @Body request: SendMessageRequest
    ): Response<ApiResult<Unit>>

    @GET("chat/Message/sync")
    suspend fun syncMessages(): Response<ApiResult<List<ChatEnvelope>>>

    // ═══════════════════════════════════════════════════════
    // 无人机绑定
    // ═══════════════════════════════════════════════════════

    @GET("rider/drone/list")
    suspend fun getDroneList(): Response<ApiResult<List<RiderUav>>>

    @POST("rider/drone/bind")
    suspend fun bindDrone(
        @Query("djiId") djiId: String
    ): Response<ApiResult<Unit>>

    @DELETE("rider/drone/unbind")
    suspend fun unbindDrone(
        @Query("djiId") djiId: String
    ): Response<ApiResult<Unit>>
}

// ═══════════════════════════════════════════════════════
// 保留原有的无人机注册相关模型
// ═══════════════════════════════════════════════════════

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
