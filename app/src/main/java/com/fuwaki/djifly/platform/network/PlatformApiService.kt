package com.fuwaki.djifly.platform.network

import com.fuwaki.djifly.data.models.ApiResult
import com.fuwaki.djifly.data.models.ChatEnvelope
import com.fuwaki.djifly.data.models.CreateSessionRequest
import com.fuwaki.djifly.data.models.LoginRequest
import com.fuwaki.djifly.data.models.LiveStartVo
import com.fuwaki.djifly.data.models.LoginResponse
import com.fuwaki.djifly.data.models.MessageVO
import com.fuwaki.djifly.data.models.RefreshResponse
import com.fuwaki.djifly.data.models.RegisterRequest
import com.fuwaki.djifly.data.models.RiderStats
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
        @Query("taskNum") taskNum: String,
        @Query("note") note: String? = null
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
    // 统计
    // ═══════════════════════════════════════════════════════

    /** 飞手统计：今日接单/总完成/总收益（契约 C9，后端 RiderStatsVO） */
    @GET("rider/stats")
    suspend fun getRiderStats(): Response<ApiResult<RiderStats>>

    // ═══════════════════════════════════════════════════════
    // 直播（飞手主动开播，1B-4b）
    // ═══════════════════════════════════════════════════════

    /**
     * 飞手对自己绑定的在线设备主动开播（Q3=C）。
     * 响应 LiveStartVO 与 /live/req 同构；成功后后端经 /ws/drone 下发 START_LIVE（既有推流链路）。
     * 错误：403 NO_PERMISSION / 404 UAV_NOT_FOUND / 409 UAV_NOT_CONNECTED、LIVE_ALREADY_STARTING / 429。
     */
    @POST("live/rider/req")
    suspend fun requestRiderLive(@Query("deviceId") deviceId: String): Response<ApiResult<LiveStartVo>>

    // ═══════════════════════════════════════════════════════
    // 交付物（1B-9b，t43 契约）
    // ═══════════════════════════════════════════════════════

    /**
     * 请求交付物上传凭证：校验授权（仅接单飞手）与类型/大小限制后返回 presigned PUT（15 分钟）。
     * 503 = MinIO 未配置（安全降级）。
     */
    @POST("tasks/{taskNum}/attachments/upload-url")
    suspend fun requestAttachmentUploadUrl(
        @Path("taskNum") taskNum: String,
        @Query("fileName") fileName: String,
        @Query("contentType") contentType: String,
        @Query("sizeBytes") sizeBytes: Long
    ): Response<ApiResult<AttachmentUploadTicket>>

    /** 交付物列表：任务所有者与接单飞手可见，逐条附 presigned 下载 URL（15 分钟） */
    @GET("tasks/{taskNum}/attachments")
    suspend fun getAttachments(@Path("taskNum") taskNum: String): Response<ApiResult<List<AttachmentItem>>>

    // ═══════════════════════════════════════════════════════
    // 消息未读/已读（1B-8 收敛包，t49 已读推进契约）
    // ═══════════════════════════════════════════════════════

    /** 未读数（只读）：sessionId → 未读条数；消息 Tab 红点数据源 */
    @GET("chat/Message/unread/count")
    suspend fun getUnreadCount(): Response<ApiResult<Map<String, Int>>>

    /**
     * 标记会话已读（t49 显式已读契约）：进入会话时调用，推进该会话 lastReadTime=now。
     * 限流 30 次/60s；仅会话成员可调（非成员 403）。
     */
    @POST("chat/Message/read")
    suspend fun markSessionRead(@Query("sessionId") sessionId: Long): Response<ApiResult<Unit>>

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
