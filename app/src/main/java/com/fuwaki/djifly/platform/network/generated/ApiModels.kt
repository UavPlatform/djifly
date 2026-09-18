// GENERATED — DO NOT EDIT.
// 契约模型：后端 components.schemas 中「被调用端点可达」的传递闭包。
// 生成器：tools/openapi-gen.mjs
// 事实源：openapi/drone-backend.openapi.json (sha256: 649c3f3e02243ba7e828cd225e84f117f18a498515312774f75bd0f66376f7da)
// 生成命令：cd djifly && node tools/openapi-gen.mjs

package com.fuwaki.djifly.platform.network.generated

/**
 * 契约数据类（Json 注解与仓库既有的 Gson 风格一致：字段名与契约键名相同，
 * 因此正常无需 @SerializedName；仅当键名不是合法 Kotlin 标识符时才生成注解）。
 *
 * 可空性取自契约 required 列表：required 字段非空，其余可空（默认 null）。
 * 本文件由生成器维护，禁止手改。
 */

/** 契约 components.schemas.LiveStartVO。 */
data class ApiLiveStartVO(
    val ackConfirmed: Boolean? = null,
    val code: String? = null,
    val liveState: String? = null,
    val requestId: String? = null,
    val roomId: String? = null
)

/** 契约 components.schemas.MessageDTO。 */
data class ApiMessageDTO(
    val content: String,
    val createTime: Long? = null,
    val fromUserId: Long? = null,
    val msgId: String? = null,
    val sessionId: Long
)

/** 契约 components.schemas.ResultListMapStringObject。 */
data class ApiResultListMapStringObject(
    val code: Int? = null,
    val data: List<Map<String, Any?>>? = null,
    val errorCode: String? = null,
    val message: String? = null,
    val success: Boolean? = null
)

/** 契约 components.schemas.ResultListRiderUav。 */
data class ApiResultListRiderUav(
    val code: Int? = null,
    val data: List<ApiRiderUav>? = null,
    val errorCode: String? = null,
    val message: String? = null,
    val success: Boolean? = null
)

/** 契约 components.schemas.ResultLiveStartVO。 */
data class ApiResultLiveStartVO(
    val code: Int? = null,
    val data: ApiLiveStartVO? = null,
    val errorCode: String? = null,
    val message: String? = null,
    val success: Boolean? = null
)

/** 契约 components.schemas.ResultMapStringObject。 */
data class ApiResultMapStringObject(
    val code: Int? = null,
    val data: Map<String, Any?>? = null,
    val errorCode: String? = null,
    val message: String? = null,
    val success: Boolean? = null
)

/** 契约 components.schemas.ResultRiderStatsVO。 */
data class ApiResultRiderStatsVO(
    val code: Int? = null,
    val data: ApiRiderStatsVO? = null,
    val errorCode: String? = null,
    val message: String? = null,
    val success: Boolean? = null
)

/** 契约 components.schemas.ResultSessionVO。 */
data class ApiResultSessionVO(
    val code: Int? = null,
    val data: ApiSessionVO? = null,
    val errorCode: String? = null,
    val message: String? = null,
    val success: Boolean? = null
)

/** 契约 components.schemas.ResultTaskPageVO。 */
data class ApiResultTaskPageVO(
    val code: Int? = null,
    val data: ApiTaskPageVO? = null,
    val errorCode: String? = null,
    val message: String? = null,
    val success: Boolean? = null
)

/** 契约 components.schemas.ResultTaskVo。 */
data class ApiResultTaskVo(
    val code: Int? = null,
    val data: ApiTaskVo? = null,
    val errorCode: String? = null,
    val message: String? = null,
    val success: Boolean? = null
)

/** 契约 components.schemas.ResultTokenRefreshVO。 */
data class ApiResultTokenRefreshVO(
    val code: Int? = null,
    val data: ApiTokenRefreshVO? = null,
    val errorCode: String? = null,
    val message: String? = null,
    val success: Boolean? = null
)

/** 契约 components.schemas.ResultUavVo。 */
data class ApiResultUavVo(
    val code: Int? = null,
    val data: ApiUavVo? = null,
    val errorCode: String? = null,
    val message: String? = null,
    val success: Boolean? = null
)

/** 契约 components.schemas.ResultUserLoginVO。 */
data class ApiResultUserLoginVO(
    val code: Int? = null,
    val data: ApiUserLoginVO? = null,
    val errorCode: String? = null,
    val message: String? = null,
    val success: Boolean? = null
)

/** 契约 components.schemas.ResultVoid。 */
data class ApiResultVoid(
    val code: Int? = null,
    val data: Any? = null,
    val errorCode: String? = null,
    val message: String? = null,
    val success: Boolean? = null
)

/** 契约 components.schemas.RiderRegisterDto。 */
data class ApiRiderRegisterDto(
    val djiId: String? = null,
    val password: String? = null,
    val userName: String? = null
)

/** 契约 components.schemas.RiderStatsVO。 */
data class ApiRiderStatsVO(
    val riderId: Long? = null,
    val riderName: String? = null,
    val todayOrders: Long? = null,
    val totalCompleted: Long? = null,
    val totalEarnings: Double? = null
)

/** 契约 components.schemas.RiderUav。 */
data class ApiRiderUav(
    val createTime: String? = null,
    val djiId: String? = null,
    val id: Long? = null,
    val userId: Long? = null
)

/** 契约 components.schemas.SessionDTO。 */
data class ApiSessionDTO(
    val avatar: String? = null,
    val description: String? = null,
    val name: String,
    val type: Int,
    val userIds: List<Long>
)

/** 契约 components.schemas.SessionVO。 */
data class ApiSessionVO(
    val avatar: String? = null,
    val createTime: Long? = null,
    val description: String? = null,
    val id: Long? = null,
    val lastMessage: String? = null,
    val lastMessageTime: Long? = null,
    val name: String? = null,
    val otherUserName: String? = null,
    val ownerId: Long? = null,
    val type: Int? = null,
    val unreadCount: Int? = null,
    val userIds: List<Long>? = null
)

/** 契约 components.schemas.TaskPageVO。 */
data class ApiTaskPageVO(
    val currentPage: Int? = null,
    val tasks: List<ApiTaskVo>? = null,
    val totalElements: Long? = null,
    val totalPages: Int? = null
)

/** 契约 components.schemas.TaskVo。 */
data class ApiTaskVo(
    val acceptTime: String? = null,
    val actionHint: String? = null,
    val completeNote: String? = null,
    val createTime: String? = null,
    val description: String? = null,
    val deviceId: String? = null,
    val id: Long? = null,
    val liveState: String? = null,
    val orderNum: String? = null,
    val orderStatus: String? = null,
    val riderId: Long? = null,
    val riderName: String? = null,
    val taskName: String? = null,
    val taskNum: String? = null,
    val taskStatus: String? = null,
    val taskTime: String? = null,
    val taskType: String? = null,
    val totalAmount: Double? = null,
    val totalDistance: Double? = null,
    val updateTime: String? = null,
    val userId: Long? = null,
    val waypoints: List<ApiWaypointVo>? = null
)

/** 契约 components.schemas.TokenRefreshVO。 */
data class ApiTokenRefreshVO(
    val token: String? = null
)

/** 契约 components.schemas.UavDto。 */
data class ApiUavDto(
    val controllerModel: String? = null,
    val djiId: String? = null,
    val onlineStatus: String? = null,
    val uavName: String? = null
)

/** 契约 components.schemas.UavVo。 */
data class ApiUavVo(
    val controllerModel: String? = null,
    val djiId: String? = null,
    val id: Long? = null,
    val isAvailable: String? = null,
    val uavName: String? = null
)

/** 契约 components.schemas.UserLoginDto。 */
data class ApiUserLoginDto(
    val password: String? = null,
    val userName: String? = null
)

/** 契约 components.schemas.UserLoginVO。 */
data class ApiUserLoginVO(
    val refreshToken: String? = null,
    val token: String? = null
)

/** 契约 components.schemas.WaypointVo。 */
data class ApiWaypointVo(
    val altitude: Double? = null,
    val id: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val orderIndex: Int? = null
)
