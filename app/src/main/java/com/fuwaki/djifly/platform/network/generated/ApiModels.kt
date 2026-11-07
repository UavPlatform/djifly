// GENERATED — DO NOT EDIT.
// 契约模型：后端 components.schemas 中「被调用端点可达」的传递闭包。
// 生成器：tools/openapi-gen.mjs
// 事实源：openapi/drone-backend.openapi.json (sha256: 2ba6bb1a876ac4d131c77031aa5e286f1e4585bc0a47004ad2dfe866f7f9193e)
// 生成命令：cd djifly && node tools/openapi-gen.mjs

package com.fuwaki.djifly.platform.network.generated

/**
 * 契约数据类（Json 注解与仓库既有的 Gson 风格一致：字段名与契约键名相同，
 * 因此正常无需 @SerializedName；仅当键名不是合法 Kotlin 标识符时才生成注解）。
 *
 * 可空性取自契约 required 列表：required 字段非空，其余可空（默认 null）。
 * 本文件由生成器维护，禁止手改。
 */

/** 契约 components.schemas.DispatchAssignmentDTO。 */
data class ApiDispatchAssignmentDTO(
    val assignmentVersion: Long
)

/** 契约 components.schemas.DispatchBillVO。 */
data class ApiDispatchBillVO(
    val balanceAmount: Double? = null,
    val finalAmount: Double? = null,
    val quote: ApiDispatchQuoteVO? = null,
    val reviewComment: String? = null,
    val reviewScore: Int? = null,
    val settledAt: String? = null,
    val settlementReference: String? = null
)

/** 契约 components.schemas.DispatchDeliveryDTO。 */
data class ApiDispatchDeliveryDTO(
    val assignmentVersion: Long,
    val materialIds: List<String>,
    val note: String? = null
)

/** 契约 components.schemas.DispatchExecutedDroneVO。 */
data class ApiDispatchExecutedDroneVO(
    val aircraftModelId: Long? = null,
    val modelCode: String? = null,
    val modelName: String? = null,
    val serialNumber: String? = null
)

/** 契约 components.schemas.DispatchLiveCredentialsVO。 */
data class ApiDispatchLiveCredentialsVO(
    val connectionId: String? = null,
    val expiresAt: Long? = null,
    val leaseExpiresAt: Long? = null,
    val liveId: String? = null,
    val privateMapKey: String? = null,
    val publisherUserId: String? = null,
    val roomId: String? = null,
    val sdkAppId: Long? = null,
    val userId: String? = null,
    val userSig: String? = null
)

/** 契约 components.schemas.DispatchLiveHeartbeatDTO。 */
data class ApiDispatchLiveHeartbeatDTO(
    val state: String
)

/** 契约 components.schemas.DispatchLiveStartDTO。 */
data class ApiDispatchLiveStartDTO(
    val requestKey: String
)

/** 契约 components.schemas.DispatchLiveStatusVO。 */
data class ApiDispatchLiveStatusVO(
    val configured: Boolean? = null,
    val connectionId: String? = null,
    val leaseExpiresAt: Long? = null,
    val liveId: String? = null,
    val state: String? = null
)

/** 契约 components.schemas.DispatchMaterialUploadDTO。 */
data class ApiDispatchMaterialUploadDTO(
    val contentType: String,
    val fileName: String,
    val purpose: String,
    val sizeBytes: Long
)

/** 契约 components.schemas.DispatchMaterialVO。 */
data class ApiDispatchMaterialVO(
    val confirmedAt: String? = null,
    val contentType: String? = null,
    val downloadUrl: String? = null,
    val fileName: String? = null,
    val materialId: String? = null,
    val purpose: String? = null,
    val sizeBytes: Long? = null,
    val uploadStatus: String? = null,
    val uploaderId: Long? = null
)

/** 契约 components.schemas.DispatchOrderVO。 */
data class ApiDispatchOrderVO(
    val acceptedAt: String? = null,
    val arrivedAt: String? = null,
    val assignedPilotId: Long? = null,
    val assignmentVersion: Long? = null,
    val bill: ApiDispatchBillVO? = null,
    val createdAt: String? = null,
    val currency: String? = null,
    val deliveredAt: String? = null,
    val deliveryNote: String? = null,
    val depositAmount: Double? = null,
    val depositPaidAt: String? = null,
    val executedDrone: ApiDispatchExecutedDroneVO? = null,
    val fields: Map<String, Any?>? = null,
    val fundsStatus: String? = null,
    val orderId: String? = null,
    val paymentMode: String? = null,
    val paymentReference: String? = null,
    val pilotServiceFee: Double? = null,
    val receiptAt: String? = null,
    val refundReference: String? = null,
    val stage: String? = null,
    val startedAt: String? = null,
    val updatedAt: String? = null,
    val waitingSince: String? = null
)

/** 契约 components.schemas.DispatchQuoteVO。 */
data class ApiDispatchQuoteVO(
    val depositAmount: Double? = null,
    val distanceBasis: String? = null,
    val distanceCharge: Double? = null,
    val distanceMeters: Double? = null,
    val pilotServiceFee: Double? = null,
    val pricePerMeter: Double? = null,
    val rule: String? = null,
    val totalAmount: Double? = null
)

/** 契约 components.schemas.DispatchUploadTicketVO。 */
data class ApiDispatchUploadTicketVO(
    val contentType: String? = null,
    val expireSeconds: Int? = null,
    val materialId: String? = null,
    val method: String? = null,
    val sizeBytes: Long? = null,
    val uploadUrl: String? = null
)

/**
 * 契约 components.schemas.EventDraftDTO。
 *
 * 仅用于读取响应/参考，禁止作为 event-packets 请求体编码器：该端点接收原始字节，
 * 必填/nullable 由 Stage1Codec 严格校验，生成 DTO 的可空性不代表契约。
 */
data class ApiEventDraftDTO(
    val capturedAt: Long? = null,
    val eventId: String? = null,
    val payload: Any? = null,
    val type: String? = null
)

/**
 * 契约 components.schemas.EventPacketAckVO。
 *
 * 仅用于读取响应/参考，禁止作为 event-packets 请求体编码器：该端点接收原始字节，
 * 必填/nullable 由 Stage1Codec 严格校验，生成 DTO 的可空性不代表契约。
 */
data class ApiEventPacketAckVO(
    val progress: ApiEventProgressVO? = null,
    val receipt: ApiEventReceiptVO? = null,
    val schemaVersion: Int? = null
)

/**
 * 契约 components.schemas.EventPacketDraftDTO。
 *
 * 仅用于读取响应/参考，禁止作为 event-packets 请求体编码器：该端点接收原始字节，
 * 必填/nullable 由 Stage1Codec 严格校验，生成 DTO 的可空性不代表契约。
 */
data class ApiEventPacketDraftDTO(
    val assignmentVersion: Long? = null,
    val chainHash: String? = null,
    val chainId: String? = null,
    val events: List<ApiEventDraftDTO>? = null,
    val hashVersion: Int? = null,
    val height: Long? = null,
    val modelCode: String? = null,
    val orderId: String? = null,
    val packetId: String? = null,
    val prevChainHash: String? = null,
    val prevPacketId: String? = null,
    val recordingSourceId: String? = null,
    val schemaVersion: Int? = null,
    val serialNumber: String? = null,
    val serialSource: String? = null
)

/**
 * 契约 components.schemas.EventProgressVO。
 *
 * 仅用于读取响应/参考，禁止作为 event-packets 请求体编码器：该端点接收原始字节，
 * 必填/nullable 由 Stage1Codec 严格校验，生成 DTO 的可空性不代表契约。
 */
data class ApiEventProgressVO(
    val contiguousHash: String? = null,
    val contiguousHeight: Long? = null,
    val highestReceivedHeight: Long? = null,
    val integrity: String? = null,
    val missingCursor: Long? = null,
    val missingRanges: List<ApiMissingRangeVO>? = null,
    val projectionRevision: Long? = null,
    val validationVersion: Long? = null
)

/**
 * 契约 components.schemas.EventReceiptVO。
 *
 * 仅用于读取响应/参考，禁止作为 event-packets 请求体编码器：该端点接收原始字节，
 * 必填/nullable 由 Stage1Codec 严格校验，生成 DTO 的可空性不代表契约。
 */
data class ApiEventReceiptVO(
    val chainHash: String? = null,
    val contentDigest: String? = null,
    val decidedAt: Long? = null,
    val durability: String? = null,
    val packetId: String? = null,
    val receiptVersion: Int? = null,
    val results: List<ApiEventResultVO>? = null
)

/**
 * 契约 components.schemas.EventResultVO。
 *
 * 仅用于读取响应/参考，禁止作为 event-packets 请求体编码器：该端点接收原始字节，
 * 必填/nullable 由 Stage1Codec 严格校验，生成 DTO 的可空性不代表契约。
 */
data class ApiEventResultVO(
    val eventId: String? = null,
    val outcome: String? = null,
    val reason: String? = null,
    val sourcePacketId: String? = null
)

/** 契约 components.schemas.FlightConnectDTO。 */
data class ApiFlightConnectDTO(
    val assignmentVersion: Long? = null,
    val modelCode: String,
    val modelName: String,
    val requestKey: String,
    val serialNumber: String,
    val startedAt: Long? = null
)

/** 契约 components.schemas.FlightConnectionVO。 */
data class ApiFlightConnectionVO(
    val aircraftModelId: Long? = null,
    val assignmentVersion: Long? = null,
    val connectionId: String? = null,
    val endReason: String? = null,
    val endedAt: String? = null,
    val maxPayloadKg: Double? = null,
    val modelCode: String? = null,
    val modelName: String? = null,
    val openedAt: String? = null,
    val orderId: String? = null,
    val overweight: Boolean? = null,
    val serialNumber: String? = null
)

/** 契约 components.schemas.FlightSampleBatchDTO。 */
data class ApiFlightSampleBatchDTO(
    val samples: List<ApiFlightSampleDTO>
)

/** 契约 components.schemas.FlightSampleBatchVO。 */
data class ApiFlightSampleBatchVO(
    val accepted: Int? = null,
    val duplicates: Int? = null,
    val rejected: Int? = null
)

/** 契约 components.schemas.FlightSampleDTO。 */
data class ApiFlightSampleDTO(
    val altitude: Double? = null,
    val battery: Int? = null,
    val capturedAt: Long? = null,
    val flying: Boolean? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val payloadKg: Double? = null,
    val sequence: Long? = null,
    val speed: Double? = null
)

/** 契约 components.schemas.LiveStartVO。 */
data class ApiLiveStartVO(
    val ackConfirmed: Boolean? = null,
    val code: String? = null,
    val liveState: String? = null,
    val requestId: String? = null,
    val roomId: String? = null
)

/**
 * 契约 components.schemas.MissingRangeVO。
 *
 * 仅用于读取响应/参考，禁止作为 event-packets 请求体编码器：该端点接收原始字节，
 * 必填/nullable 由 Stage1Codec 严格校验，生成 DTO 的可空性不代表契约。
 */
data class ApiMissingRangeVO(
    val fromHeight: Long? = null,
    val toHeight: Long? = null
)

/** 契约 components.schemas.ResultDispatchLiveCredentialsVO。 */
data class ApiResultDispatchLiveCredentialsVO(
    val code: Int? = null,
    val data: ApiDispatchLiveCredentialsVO? = null,
    val errorCode: String? = null,
    val message: String? = null,
    val success: Boolean? = null
)

/** 契约 components.schemas.ResultDispatchLiveStatusVO。 */
data class ApiResultDispatchLiveStatusVO(
    val code: Int? = null,
    val data: ApiDispatchLiveStatusVO? = null,
    val errorCode: String? = null,
    val message: String? = null,
    val success: Boolean? = null
)

/** 契约 components.schemas.ResultDispatchMaterialVO。 */
data class ApiResultDispatchMaterialVO(
    val code: Int? = null,
    val data: ApiDispatchMaterialVO? = null,
    val errorCode: String? = null,
    val message: String? = null,
    val success: Boolean? = null
)

/** 契约 components.schemas.ResultDispatchOrderVO。 */
data class ApiResultDispatchOrderVO(
    val code: Int? = null,
    val data: ApiDispatchOrderVO? = null,
    val errorCode: String? = null,
    val message: String? = null,
    val success: Boolean? = null
)

/** 契约 components.schemas.ResultDispatchUploadTicketVO。 */
data class ApiResultDispatchUploadTicketVO(
    val code: Int? = null,
    val data: ApiDispatchUploadTicketVO? = null,
    val errorCode: String? = null,
    val message: String? = null,
    val success: Boolean? = null
)

/**
 * 契约 components.schemas.ResultEventPacketAckVO。
 *
 * 仅用于读取响应/参考，禁止作为 event-packets 请求体编码器：该端点接收原始字节，
 * 必填/nullable 由 Stage1Codec 严格校验，生成 DTO 的可空性不代表契约。
 */
data class ApiResultEventPacketAckVO(
    val code: Int? = null,
    val data: ApiEventPacketAckVO? = null,
    val errorCode: String? = null,
    val message: String? = null,
    val success: Boolean? = null
)

/** 契约 components.schemas.ResultFlightConnectionVO。 */
data class ApiResultFlightConnectionVO(
    val code: Int? = null,
    val data: ApiFlightConnectionVO? = null,
    val errorCode: String? = null,
    val message: String? = null,
    val success: Boolean? = null
)

/** 契约 components.schemas.ResultFlightSampleBatchVO。 */
data class ApiResultFlightSampleBatchVO(
    val code: Int? = null,
    val data: ApiFlightSampleBatchVO? = null,
    val errorCode: String? = null,
    val message: String? = null,
    val success: Boolean? = null
)

/** 契约 components.schemas.ResultListDispatchMaterialVO。 */
data class ApiResultListDispatchMaterialVO(
    val code: Int? = null,
    val data: List<ApiDispatchMaterialVO>? = null,
    val errorCode: String? = null,
    val message: String? = null,
    val success: Boolean? = null
)

/** 契约 components.schemas.ResultListDispatchOrderVO。 */
data class ApiResultListDispatchOrderVO(
    val code: Int? = null,
    val data: List<ApiDispatchOrderVO>? = null,
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
