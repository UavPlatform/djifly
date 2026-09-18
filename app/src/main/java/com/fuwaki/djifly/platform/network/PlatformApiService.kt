package com.fuwaki.djifly.platform.network

import com.fuwaki.djifly.data.models.ApiResult
import com.fuwaki.djifly.data.models.ChatEnvelope
import com.fuwaki.djifly.data.models.LiveStartVo
import com.fuwaki.djifly.data.models.LoginResponse
import com.fuwaki.djifly.data.models.MessageVO
import com.fuwaki.djifly.data.models.RefreshResponse
import com.fuwaki.djifly.data.models.RiderUav
import com.fuwaki.djifly.data.models.SessionVO
import com.fuwaki.djifly.data.models.TaskPageVO
import com.fuwaki.djifly.data.models.TaskVo
import com.fuwaki.djifly.platform.network.generated.ApiMessageDTO
import com.fuwaki.djifly.platform.network.generated.ApiPaths
import com.fuwaki.djifly.platform.network.generated.ApiRiderRegisterDto
import com.fuwaki.djifly.platform.network.generated.ApiRiderStatsVO
import com.fuwaki.djifly.platform.network.generated.ApiSessionDTO
import com.fuwaki.djifly.platform.network.generated.ApiUserLoginDto
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

    @POST(ApiPaths.APP_UAV_ADD)
    suspend fun registerDrone(
        @Body request: RegisterDroneRequest
    ): Response<RegisterDroneResponse>

    @POST(ApiPaths.API_WS_REQUEST)
    suspend fun requestDroneWebSocketConnection(
        @Query("deviceId") deviceId: String
    ): Response<RequestDroneWebSocketConnectionResponse>

    // ═══════════════════════════════════════════════════════
    // 认证
    // ═══════════════════════════════════════════════════════

    @POST(ApiPaths.USER_LOGIN)
    suspend fun login(
        @Body request: ApiUserLoginDto
    ): Response<ApiResult<LoginResponse>>

    @POST(ApiPaths.RIDER_REGISTER)
    suspend fun register(
        @Body request: ApiRiderRegisterDto
    ): Response<ApiResult<LoginResponse>>

    @POST(ApiPaths.USER_REFRESH)
    suspend fun refreshToken(
        @Header("Refresh-Token") refreshToken: String
    ): Response<ApiResult<RefreshResponse>>

    // ═══════════════════════════════════════════════════════
    // 任务 — 飞手端
    // ═══════════════════════════════════════════════════════

    @GET(ApiPaths.RIDER_SQUARE)
    suspend fun getTaskSquare(): Response<ApiResult<TaskPageVO>>

    @POST(ApiPaths.RIDER_ACCEPT)
    suspend fun acceptTask(
        @Query("taskNum") taskNum: String
    ): Response<ApiResult<Unit>>

    @GET(ApiPaths.RIDER_MY_TASKS)
    suspend fun getMyTasks(): Response<ApiResult<TaskPageVO>>

    @GET(ApiPaths.RIDER_MY_TASKS_HISTORY)
    suspend fun getTaskHistory(): Response<ApiResult<TaskPageVO>>

    @POST(ApiPaths.RIDER_CANCEL)
    suspend fun cancelTask(
        @Query("taskNum") taskNum: String
    ): Response<ApiResult<Unit>>

    @POST(ApiPaths.RIDER_COMPLETE)
    suspend fun completeTask(
        @Query("taskNum") taskNum: String,
        @Query("note") note: String? = null
    ): Response<ApiResult<Unit>>

    @GET(ApiPaths.RIDER_TASK_DETAIL)
    suspend fun getTaskDetail(
        @Query("taskNum") taskNum: String
    ): Response<ApiResult<TaskVo>>

    // ═══════════════════════════════════════════════════════
    // 聊天
    // ═══════════════════════════════════════════════════════

    @GET(ApiPaths.CHAT_SESSION_LIST)
    suspend fun getSessions(): Response<ApiResult<List<SessionVO>>>

    @POST(ApiPaths.CHAT_SESSION_CREATE)
    suspend fun createSession(
        @Body request: ApiSessionDTO
    ): Response<ApiResult<SessionVO>>

    @GET(ApiPaths.CHAT_MESSAGE_MESSAGES)
    suspend fun getMessages(
        @Path("sessionId") sessionId: Long
    ): Response<ApiResult<List<ChatEnvelope>>>

    @POST(ApiPaths.CHAT_MESSAGE_SEND)
    suspend fun sendMessage(
        @Body request: ApiMessageDTO
    ): Response<ApiResult<Unit>>

    @GET(ApiPaths.CHAT_MESSAGE_SYNC)
    suspend fun syncMessages(): Response<ApiResult<List<ChatEnvelope>>>

    // ═══════════════════════════════════════════════════════
    // 统计
    // ═══════════════════════════════════════════════════════

    /** 飞手统计：今日接单/总完成/总收益（契约 C9，后端 RiderStatsVO） */
    @GET(ApiPaths.RIDER_STATS)
    suspend fun getRiderStats(): Response<ApiResult<ApiRiderStatsVO>>

    // ═══════════════════════════════════════════════════════
    // 直播（飞手主动开播，1B-4b）
    // ═══════════════════════════════════════════════════════

    /**
     * 飞手对自己绑定的在线设备主动开播（Q3=C）。
     * 响应 LiveStartVO 与 /live/req 同构；成功后后端经 /ws/drone 下发 START_LIVE（既有推流链路）。
     * 错误：403 NO_PERMISSION / 404 UAV_NOT_FOUND / 409 UAV_NOT_CONNECTED、LIVE_ALREADY_STARTING / 429。
     */
    @POST(ApiPaths.LIVE_RIDER_REQ)
    suspend fun requestRiderLive(@Query("deviceId") deviceId: String): Response<ApiResult<LiveStartVo>>

    // ═══════════════════════════════════════════════════════
    // 交付物（1B-9b，t43 契约）
    // ═══════════════════════════════════════════════════════

    /**
     * 请求交付物上传凭证：校验授权（仅接单飞手）与类型/大小限制后返回 presigned PUT（15 分钟）。
     * 503 = MinIO 未配置（安全降级）。
     */
    @POST(ApiPaths.TASKS_ATTACHMENTS_UPLOAD_URL)
    suspend fun requestAttachmentUploadUrl(
        @Path("taskNum") taskNum: String,
        @Query("fileName") fileName: String,
        @Query("contentType") contentType: String,
        @Query("sizeBytes") sizeBytes: Long
    ): Response<ApiResult<AttachmentUploadTicket>>

    /** 交付物列表：任务所有者与接单飞手可见，逐条附 presigned 下载 URL（15 分钟） */
    @GET(ApiPaths.TASKS_ATTACHMENTS)
    suspend fun getAttachments(@Path("taskNum") taskNum: String): Response<ApiResult<List<AttachmentItem>>>

    // ═══════════════════════════════════════════════════════
    // 消息未读/已读（1B-8 收敛包，t49 已读推进契约）
    // ═══════════════════════════════════════════════════════

    /** 未读数（只读）：sessionId → 未读条数；消息 Tab 红点数据源 */
    @GET(ApiPaths.CHAT_MESSAGE_UNREAD_COUNT)
    suspend fun getUnreadCount(): Response<ApiResult<Map<String, Int>>>

    /**
     * 标记会话已读（t49 显式已读契约）：进入会话时调用，推进该会话 lastReadTime=now。
     * 限流 30 次/60s；仅会话成员可调（非成员 403）。
     */
    @POST(ApiPaths.CHAT_MESSAGE_READ)
    suspend fun markSessionRead(@Query("sessionId") sessionId: Long): Response<ApiResult<Unit>>

    // ═══════════════════════════════════════════════════════
    // 无人机绑定
    // ═══════════════════════════════════════════════════════

    @GET(ApiPaths.RIDER_DRONE_LIST)
    suspend fun getDroneList(): Response<ApiResult<List<RiderUav>>>

    @POST(ApiPaths.RIDER_DRONE_BIND)
    suspend fun bindDrone(
        @Query("djiId") djiId: String
    ): Response<ApiResult<Unit>>

    @DELETE(ApiPaths.RIDER_DRONE_UNBIND)
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