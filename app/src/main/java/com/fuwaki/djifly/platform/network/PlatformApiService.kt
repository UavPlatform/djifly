package com.fuwaki.djifly.platform.network

import com.fuwaki.djifly.data.models.ApiResult
import com.fuwaki.djifly.data.models.LiveStartVo
import com.fuwaki.djifly.data.models.LoginResponse
import com.fuwaki.djifly.data.models.RefreshResponse
import com.fuwaki.djifly.data.models.RiderUav
import com.fuwaki.djifly.data.models.TaskPageVO
import com.fuwaki.djifly.data.models.TaskVo
import com.fuwaki.djifly.platform.network.generated.ApiPaths
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

    @POST(ApiPaths.USER_REFRESH)
    suspend fun refreshToken(
        @Header("Refresh-Token") refreshToken: String
    ): Response<ApiResult<RefreshResponse>>

    // ═══════════════════════════════════════════════════════
    // 任务 — 飞手端
    // ═══════════════════════════════════════════════════════

    @GET(ApiPaths.RIDER_MY_TASKS)
    suspend fun getMyTasks(): Response<ApiResult<TaskPageVO>>

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

    @POST(ApiPaths.TASKS_ATTACHMENTS_CONFIRM_UPLOAD)
    suspend fun confirmAttachmentUpload(
        @Path("taskNum") taskNum: String,
        @Query("objectKey") objectKey: String
    ): Response<ApiResult<Map<String, String>>>

    /** 交付物列表：任务所有者与接单飞手可见，逐条附 presigned 下载 URL（15 分钟） */
    @GET(ApiPaths.TASKS_ATTACHMENTS)
    suspend fun getAttachments(@Path("taskNum") taskNum: String): Response<ApiResult<List<AttachmentItem>>>

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