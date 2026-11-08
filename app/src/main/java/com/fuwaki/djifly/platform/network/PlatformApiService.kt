package com.fuwaki.djifly.platform.network

import com.fuwaki.djifly.data.models.ApiResult
import com.fuwaki.djifly.data.models.LoginResponse
import com.fuwaki.djifly.data.models.RefreshResponse
import com.fuwaki.djifly.platform.network.generated.ApiDispatchAssignmentDTO
import com.fuwaki.djifly.platform.network.generated.ApiDispatchDeliveryDTO
import com.fuwaki.djifly.platform.network.generated.ApiDispatchLiveHeartbeatDTO
import com.fuwaki.djifly.platform.network.generated.ApiDispatchLiveStartDTO
import com.fuwaki.djifly.platform.network.generated.ApiDispatchMaterialUploadDTO
import com.fuwaki.djifly.platform.network.generated.ApiEventReconciliationDraftDTO
import com.fuwaki.djifly.platform.network.generated.ApiPaths
import com.fuwaki.djifly.platform.network.generated.ApiResultDispatchLiveCredentialsVO
import com.fuwaki.djifly.platform.network.generated.ApiResultDispatchLiveStatusVO
import com.fuwaki.djifly.platform.network.generated.ApiResultDispatchMaterialVO
import com.fuwaki.djifly.platform.network.generated.ApiResultDispatchOrderVO
import com.fuwaki.djifly.platform.network.generated.ApiResultDispatchUploadTicketVO
import com.fuwaki.djifly.platform.network.generated.ApiResultEventPacketAckVO
import com.fuwaki.djifly.platform.network.generated.ApiResultEventReconciliationVO
import com.fuwaki.djifly.platform.network.generated.ApiResultFlightEventPageVO
import com.fuwaki.djifly.platform.network.generated.ApiResultFlightProjectionVO
import com.fuwaki.djifly.platform.network.generated.ApiResultListDispatchMaterialVO
import com.fuwaki.djifly.platform.network.generated.ApiResultListDispatchOrderVO
import com.fuwaki.djifly.platform.network.generated.ApiUserLoginDto
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Single HTTP boundary for the dispatch client. */
interface PlatformApiService {

    @POST(ApiPaths.USER_LOGIN)
    suspend fun login(@Body request: ApiUserLoginDto): Response<ApiResult<LoginResponse>>

    @POST(ApiPaths.USER_REFRESH)
    suspend fun refreshToken(@Header("Refresh-Token") refreshToken: String): Response<ApiResult<RefreshResponse>>

    @GET(ApiPaths.DISPATCH_PILOT_ORDERS)
    suspend fun getMyDispatchOrders(): Response<ApiResultListDispatchOrderVO>

    @POST(ApiPaths.DISPATCH_ORDERS_START_TRANSPORT)
    suspend fun startTransport(
        @Path("orderId") orderId: String,
        @Body body: ApiDispatchAssignmentDTO
    ): Response<ApiResultDispatchOrderVO>

    @POST(ApiPaths.DISPATCH_ORDERS_DELIVER)
    suspend fun deliverOrder(
        @Path("orderId") orderId: String,
        @Body body: ApiDispatchDeliveryDTO
    ): Response<ApiResultDispatchOrderVO>

    @GET(ApiPaths.DISPATCH_ORDERS_MATERIALS)
    suspend fun listMaterials(@Path("orderId") orderId: String): Response<ApiResultListDispatchMaterialVO>

    @POST(ApiPaths.DISPATCH_ORDERS_MATERIALS_UPLOAD_TICKET)
    suspend fun requestMaterialUploadTicket(
        @Path("orderId") orderId: String,
        @Body body: ApiDispatchMaterialUploadDTO
    ): Response<ApiResultDispatchUploadTicketVO>

    @POST(ApiPaths.DISPATCH_ORDERS_MATERIALS_CONFIRM)
    suspend fun confirmMaterialUpload(
        @Path("orderId") orderId: String,
        @Path("materialId") materialId: String
    ): Response<ApiResultDispatchMaterialVO>

    /** Uploads the already encoded v2 packet; this method never builds protocol JSON. */
    @POST(ApiPaths.DISPATCH_PILOT_ORDERS_EVENT_PACKETS)
    suspend fun uploadEventPacket(
        @Path("orderId") orderId: String,
        @Body packet: RequestBody
    ): Response<ApiResultEventPacketAckVO>

    /**
     * Reconciliation body is sent as exact bytes, not a DTO: the backend requires the field set to equal exactly nine
     * keys (nullable cursors present as explicit nulls), which Gson's default null-skipping would silently violate.
     */
    @POST(ApiPaths.DISPATCH_PILOT_ORDERS_EVENT_RECONCILIATION)
    suspend fun reconcileEventChain(
        @Path("orderId") orderId: String,
        @Body request: RequestBody
    ): Response<ApiResultEventReconciliationVO>

    @GET(ApiPaths.DISPATCH_ORDERS_FLIGHT)
    suspend fun getFlightProjection(
        @Path("orderId") orderId: String
    ): Response<ApiResultFlightProjectionVO>

    @GET(ApiPaths.DISPATCH_ORDERS_FLIGHT_TRACK)
    suspend fun getFlightTrack(
        @Path("orderId") orderId: String,
        @Query("after") after: Long? = null,
        @Query("limit") limit: Int? = null
    ): Response<ApiResultFlightEventPageVO>

    @GET(ApiPaths.DISPATCH_ORDERS_FLIGHT_HISTORY)
    suspend fun getFlightHistory(
        @Path("orderId") orderId: String,
        @Query("afterCapturedAt") afterCapturedAt: Long? = null,
        @Query("afterEventId") afterEventId: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("serialNumber") serialNumber: String? = null,
        @Query("serialSource") serialSource: String? = null
    ): Response<ApiResultFlightEventPageVO>

    @POST(ApiPaths.DISPATCH_PILOT_ORDERS_LIVE)
    suspend fun startDispatchLive(
        @Path("orderId") orderId: String,
        @Body request: ApiDispatchLiveStartDTO
    ): Response<ApiResultDispatchLiveCredentialsVO>

    @POST(ApiPaths.DISPATCH_PILOT_ORDERS_LIVE_HEARTBEAT)
    suspend fun heartbeatDispatchLive(
        @Path("orderId") orderId: String,
        @Path("liveId") liveId: String,
        @Body request: ApiDispatchLiveHeartbeatDTO
    ): Response<ApiResultDispatchLiveStatusVO>

    @POST(ApiPaths.DISPATCH_PILOT_ORDERS_LIVE_STOP)
    suspend fun stopDispatchLive(
        @Path("orderId") orderId: String,
        @Path("liveId") liveId: String
    ): Response<ApiResult<Unit>>

    @GET(ApiPaths.DISPATCH_ORDERS_LIVE)
    suspend fun getDispatchLive(
        @Path("orderId") orderId: String
    ): Response<ApiResultDispatchLiveStatusVO>

    @POST(ApiPaths.DISPATCH_ORDERS_LIVE_WATCH)
    suspend fun watchDispatchLive(
        @Path("orderId") orderId: String,
        @Path("liveId") liveId: String
    ): Response<ApiResultDispatchLiveCredentialsVO>
}

data class PlatformApiErrorResponse(
    val success: Boolean = false,
    val code: String? = null,
    val id: Long? = null,
    val name: String? = null,
    val message: String? = null
)
