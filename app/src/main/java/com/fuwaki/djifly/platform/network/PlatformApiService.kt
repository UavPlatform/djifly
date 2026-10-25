package com.fuwaki.djifly.platform.network

import com.fuwaki.djifly.data.models.ApiResult
import com.fuwaki.djifly.data.models.LiveStartVo
import com.fuwaki.djifly.data.models.LoginResponse
import com.fuwaki.djifly.data.models.RefreshResponse
import com.fuwaki.djifly.platform.network.generated.ApiDispatchAssignmentDTO
import com.fuwaki.djifly.platform.network.generated.ApiDispatchDeliveryDTO
import com.fuwaki.djifly.platform.network.generated.ApiDispatchMaterialUploadDTO
import com.fuwaki.djifly.platform.network.generated.ApiFlightConnectDTO
import com.fuwaki.djifly.platform.network.generated.ApiFlightSampleBatchDTO
import com.fuwaki.djifly.platform.network.generated.ApiResultDispatchMaterialVO
import com.fuwaki.djifly.platform.network.generated.ApiResultDispatchOrderVO
import com.fuwaki.djifly.platform.network.generated.ApiResultDispatchUploadTicketVO
import com.fuwaki.djifly.platform.network.generated.ApiResultFlightConnectionVO
import com.fuwaki.djifly.platform.network.generated.ApiResultFlightSampleBatchVO
import com.fuwaki.djifly.platform.network.generated.ApiResultListDispatchMaterialVO
import com.fuwaki.djifly.platform.network.generated.ApiResultListDispatchOrderVO
import com.fuwaki.djifly.platform.network.generated.ApiPaths
import com.fuwaki.djifly.platform.network.generated.ApiUserLoginDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface PlatformApiService {

    @POST(ApiPaths.APP_UAV_ADD)
    suspend fun registerDrone(
        @Body request: RegisterDroneRequest
    ): Response<RegisterDroneResponse>

    @POST(ApiPaths.API_WS_REQUEST)
    suspend fun requestDroneWebSocketConnection(
        @Query("deviceId") deviceId: String
    ): Response<RequestDroneWebSocketConnectionResponse>

    @POST(ApiPaths.USER_LOGIN)
    suspend fun login(
        @Body request: ApiUserLoginDto
    ): Response<ApiResult<LoginResponse>>

    @POST(ApiPaths.USER_REFRESH)
    suspend fun refreshToken(
        @Header("Refresh-Token") refreshToken: String
    ): Response<ApiResult<RefreshResponse>>

    @POST(ApiPaths.LIVE_RIDER_REQ)
    suspend fun requestRiderLive(@Query("deviceId") deviceId: String): Response<ApiResult<LiveStartVo>>

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

    @POST(ApiPaths.DISPATCH_PILOT_ORDERS_FLIGHT_CONNECTIONS)
    suspend fun connectFlight(
        @Path("orderId") orderId: String,
        @Body request: ApiFlightConnectDTO
    ): Response<ApiResultFlightConnectionVO>

    @POST(ApiPaths.DISPATCH_PILOT_ORDERS_FLIGHT_CONNECTIONS_SAMPLES_BATCH)
    suspend fun uploadFlightSamples(
        @Path("orderId") orderId: String,
        @Path("connectionId") connectionId: String,
        @Body request: ApiFlightSampleBatchDTO
    ): Response<ApiResultFlightSampleBatchVO>

    @POST(ApiPaths.DISPATCH_PILOT_ORDERS_FLIGHT_CONNECTIONS_CLOSE)
    suspend fun closeFlight(
        @Path("orderId") orderId: String,
        @Path("connectionId") connectionId: String
    ): Response<ApiResult<Unit>>
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
