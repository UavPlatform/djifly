@file:OptIn(com.fuwaki.djifly.platform.eventstage1.ExperimentalStage1Api::class)

package com.fuwaki.djifly.platform.telemetry

import android.content.Context
import android.util.Log
import com.fuwaki.djifly.BuildConfig
import com.fuwaki.djifly.data.auth.TokenStore
import com.fuwaki.djifly.di.ApplicationScope
import com.fuwaki.djifly.di.EventUploadClient
import com.fuwaki.djifly.platform.dispatch.DispatchStages
import com.fuwaki.djifly.platform.eventstage1.EventStreamPacketEncoder
import com.fuwaki.djifly.platform.eventstage1.Stage1Authentication
import com.fuwaki.djifly.platform.eventstage1.Stage1Link
import com.fuwaki.djifly.platform.eventstage1.Stage1Receipt
import com.fuwaki.djifly.platform.eventstage1.Stage1UploadResult
import com.fuwaki.djifly.platform.eventstage1.Stage1Uploader
import com.fuwaki.djifly.platform.eventstore.core.EventPacketStore
import com.fuwaki.djifly.platform.eventstream.AircraftIdentity
import com.fuwaki.djifly.platform.eventstream.EventDraft
import com.fuwaki.djifly.platform.eventstream.EventInput
import com.fuwaki.djifly.platform.eventstream.EventRateClass
import com.fuwaki.djifly.platform.eventstream.EventStreamCoordinator
import com.fuwaki.djifly.platform.eventstream.RecordingOperation
import com.fuwaki.djifly.platform.network.PlatformApiService
import com.fuwaki.djifly.platform.network.PlatformEndpointResolver
import com.fuwaki.djifly.platform.network.generated.ApiDispatchOrderVO
import com.fuwaki.djifly.sdk.DjiSdkManager
import com.fuwaki.djifly.sdk.DjiSdkStatus
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.File
import java.util.UUID
import kotlin.math.roundToLong
import javax.inject.Inject
import javax.inject.Singleton

/** UI-facing state for the local event recording path. */
data class DispatchFlightState(
    val currentOrder: ApiDispatchOrderVO? = null,
    val recordingActive: Boolean = false,
    val pendingPackets: Int = 0,
    val cacheWarning: String? = null,
    val lastError: String? = null,
)

/** Connects DJI state to the v2 event journal. */
@Singleton
class DispatchTelemetryService @Inject constructor(
    @ApplicationContext context: Context,
    private val api: PlatformApiService,
    private val sdkManager: DjiSdkManager,
    private val tokenStore: TokenStore,
    private val store: EventPacketStore,
    private val endpointResolver: PlatformEndpointResolver,
    @EventUploadClient private val eventUploadClient: OkHttpClient,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val orderFile = File(context.filesDir, "event-packets/current-order.json")
    private val gson = Gson()
    private val _state = MutableStateFlow(DispatchFlightState(currentOrder = loadOrder()))
    val state: StateFlow<DispatchFlightState> = _state.asStateFlow()

    @Volatile
    var intervalMs: Long = DEFAULT_INTERVAL_MS
        set(value) { field = value.coerceAtLeast(MIN_INTERVAL_MS) }

    private val authentication = SnapshotAuthentication()
    private var chainIdForContext: String? = null
    private val coordinator = EventStreamCoordinator(::appendDrafts, chainIdFactory = {
        chainIdForContext?.also { chainIdForContext = null } ?: UUID.randomUUID().toString()
    })
    private val encoder = EventStreamPacketEncoder()
    private var uploader: Stage1Uploader? = null
    private var job: Job? = null
    private var lastOrderRefresh = 0L
    private var operationId: String? = null
    private var operationOrderId: String? = null
    private var aircraftGeneration: Long? = null
    private var lastAircraft: AircraftIdentity? = null
    private var lastPacketContext: com.fuwaki.djifly.platform.eventstream.EventStreamContext? = null
    private var previousLink: Stage1Link? = null
    private var lastFlying: Boolean? = null
    private var lastUploadError: String? = null

    @Synchronized
    fun start() {
        if (job != null) return
        job = scope.launch {
            launch {
                tokenStore.userId.collectLatest { pilotId ->
                    authentication.pilotId = pilotId
                    if (pilotId == null) {
                        uploader = null
                        coordinator.setPilot(null)
                        resetAttribution()
                    } else {
                        coordinator.setPilot(pilotId)
                        // 传输配置错误只应让上传停摆并暴露原因，不能让整进程闪退。
                        uploader = runCatching { createUploader(pilotId) }
                            .onFailure { error ->
                                lastUploadError = "事件上传未启用：${error.message ?: error::class.java.simpleName}"
                                Log.e(TAG, "could not create event uploader", error)
                            }
                            .getOrNull()
                        if (uploader != null) authenticationChanged()
                    }
                }
            }
            launch {
                tokenStore.accessToken.collectLatest { token ->
                    authentication.token = token
                    if (!token.isNullOrBlank()) authenticationChanged()
                }
            }
            launch {
                while (isActive) {
                    val now = System.currentTimeMillis()
                    runCatching {
                        if (now - lastOrderRefresh >= ORDER_REFRESH_MS) refreshOrders()
                        sampleAndUpload(now)
                    }.onFailure { error ->
                        lastUploadError = "本地事件循环失败：${error.message ?: error::class.java.simpleName}"
                        Log.e(TAG, "event loop iteration failed", error)
                    }
                    delay(intervalMs)
                }
            }
        }
    }

    suspend fun refreshOrders() {
        lastOrderRefresh = System.currentTimeMillis()
        val response = runCatching { api.getMyDispatchOrders() }.getOrNull() ?: return
        if (!response.isSuccessful) return
        val order = DispatchStages.current(response.body()?.data.orEmpty())
        runCatching {
            orderFile.parentFile?.mkdirs()
            if (order == null) orderFile.delete() else orderFile.writeText(gson.toJson(order))
        }.onFailure { Log.w(TAG, "could not persist current order", it) }
        _state.value = _state.value.copy(currentOrder = order)
    }

    private fun sampleAndUpload(capturedAt: Long) {
        val status = sdkManager.sdkStatus.value
        val order = _state.value.currentOrder
        val pilotId = authentication.pilotId
        val serial = DroneModelMapper.validSerial(status.productInfo.serialNumber)
        val model = DroneModelMapper.modelCode(status.productInfo.type.name)

        if (pilotId == null || order?.orderId == null || serial == null || model == null || !status.isProductConnected) {
            if (aircraftGeneration != null) coordinator.aircraftDisconnected(aircraftGeneration!!)
            resetAttribution()
            publishLocalState(status)
            return
        }

        val orderId = order.orderId
        if (operationId == null || operationOrderId != orderId) {
            coordinator.endOperation()
            val recovered = store.operations().lastOrNull { it.orderId == orderId }
            operationId = recovered?.operationId ?: UUID.randomUUID().toString()
            operationOrderId = orderId
            if (recovered == null) store.beginOperation(operationId!!, orderId)
            val recoveredPacket = recovered?.let { store.recoverOperation(it.operationId).packets.lastOrNull() }
            val recoveredExpected = recoveredPacket?.let {
                runCatching { Stage1Receipt.readSummary(it.summary, pilotId) }.getOrNull()
            }
            chainIdForContext = recoveredExpected?.link?.identity?.chainId
            previousLink = recoveredExpected?.link
            coordinator.beginOperation(RecordingOperation(pilotId, orderId, store.recordingSourceId))
            lastPacketContext = null
            lastFlying = null
        }

        val aircraft = AircraftIdentity(serial, SERIAL_SOURCE, model)
        if (aircraftGeneration == null || lastAircraft != aircraft) {
            if (lastAircraft != null && lastAircraft != aircraft) {
                chainIdForContext = null
                previousLink = null
            }
            aircraftGeneration = coordinator.aircraftConnected(aircraft)
            lastAircraft = aircraft
            previousLink = null
            lastPacketContext = null
            lastFlying = null
        }

        val generation = aircraftGeneration ?: return
        val inputs = buildInputs(status, capturedAt)
        if (inputs.isNotEmpty()) {
            runCatching { coordinator.record(inputs, generation) }
                .onFailure { error -> lastUploadError = "本地事件记录失败：${error.message ?: "未知错误"}" }
        }
        uploadPending(orderId)
        publishLocalState(status)
    }

    private fun buildInputs(status: DjiSdkStatus, capturedAt: Long): List<EventInput> {
        val data = status.flightData
        return buildList {
            val latitude = data.latitude
            val longitude = data.longitude
            if (latitude != null && longitude != null && latitude.isFinite() && longitude.isFinite()) {
                add(EventInput("POSITION", capturedAt, mapOf(
                    "latitudeDegE7" to (latitude * 10_000_000.0).roundToLong(),
                    "longitudeDegE7" to (longitude * 10_000_000.0).roundToLong(),
                )))
            }
            data.altitude?.takeIf(Double::isFinite)?.let {
                add(EventInput("ALTITUDE", capturedAt, mapOf("altitudeMm" to (it * 1_000.0).roundToLong())))
            }
            data.speed?.takeIf { it.isFinite() && it >= 0.0 }?.let {
                add(EventInput("VELOCITY", capturedAt, mapOf("speedMmps" to (it * 1_000.0).roundToLong())))
            }
            if (data.batteryKnown) {
                add(EventInput("BATTERY", capturedAt, mapOf("chargePermille" to data.batteryPercentage.coerceIn(0, 100) * 10)))
            }
            data.isFlying?.let { flying ->
                if (flying != lastFlying) {
                    add(EventInput("ACTUAL_FLYING_STATE", capturedAt, mapOf("flying" to flying), EventRateClass.STATE_OR_INTENT))
                    lastFlying = flying
                }
            }
        }
    }

    private fun appendDrafts(drafts: List<EventDraft>) {
        val operation = operationId ?: error("No local recording operation")
        val context = drafts.first().context
        if (context != lastPacketContext) {
            previousLink = null
            lastPacketContext = context
        }
        val packet = encoder.encode(context, drafts, previousLink)
        val activeUploader = uploader ?: error("No pilot authentication snapshot")
        activeUploader.enqueue(operation, packet)
        previousLink = packet.link
        scheduleAttempt(activeUploader, operation, packet.link.packetId)
    }

    private fun uploadPending(orderId: String) {
        val activeUploader = uploader ?: return
        store.operations().filter { it.orderId == orderId }.forEach { operation ->
            store.pending(operation.operationId, limit = EventPacketStore.DEFAULT_PENDING_LIMIT)
                .forEach { packet -> scheduleAttempt(activeUploader, operation.operationId, packet.packetId) }
        }
    }

    private fun scheduleAttempt(activeUploader: Stage1Uploader, operation: String, packetId: String) {
        scope.launch {
            val result = runCatching { activeUploader.attempt(operation, packetId) }
                .getOrElse { error ->
                    lastUploadError = "上传失败：${error.message ?: "未知错误"}"
                    return@launch
                }
            when (result) {
                is Stage1UploadResult.HttpRejected -> lastUploadError = "上传被拒绝（HTTP ${result.status}）"
                is Stage1UploadResult.InvalidResponse -> lastUploadError = "上传响应无效：${result.detail}"
                is Stage1UploadResult.InterventionRequired -> lastUploadError = "上传需要处理：${result.reason}"
                Stage1UploadResult.AuthenticationRequired -> lastUploadError = "等待登录凭证"
                Stage1UploadResult.ChainForked -> lastUploadError = "事件链发生冲突，需要对账"
                is Stage1UploadResult.Durable, Stage1UploadResult.AlreadyDurable -> lastUploadError = null
                is Stage1UploadResult.NotDue, is Stage1UploadResult.RetryAfter -> Unit
            }
        }
    }

    private fun publishLocalState(status: DjiSdkStatus) {
        val orderId = _state.value.currentOrder?.orderId
        val pending = store.operations().filter { it.orderId == orderId }.sumOf { store.pendingCount(it.operationId) }
        val active = coordinator.currentContext != null && status.isProductConnected
        _state.value = _state.value.copy(
            recordingActive = active,
            pendingPackets = pending,
            cacheWarning = null,
            lastError = lastUploadError,
        )
    }

    private fun createUploader(pilotId: Long): Stage1Uploader = Stage1Uploader(
        endpointResolver.requireHttpBaseUrl().toHttpUrl(),
        eventUploadClient,
        store,
        pilotId,
        authentication,
        allowCleartextHttp = BuildConfig.ALLOW_CLEARTEXT_BACKEND,
    )

    private fun authenticationChanged() {
        val active = uploader ?: return
        if (!authentication.token.isNullOrBlank() && authentication.pilotId != null) {
            runCatching { active.authenticationChanged() }
        }
    }

    private fun resetAttribution() {
        coordinator.endOperation()
        operationId = null
        operationOrderId = null
        aircraftGeneration = null
        lastAircraft = null
        lastPacketContext = null
        previousLink = null
        lastFlying = null
    }

    private fun loadOrder(): ApiDispatchOrderVO? =
        runCatching { gson.fromJson(orderFile.readText(), ApiDispatchOrderVO::class.java) }.getOrNull()

    private class SnapshotAuthentication : Stage1Authentication {
        @Volatile var pilotId: Long? = null
        @Volatile var token: String? = null
        override fun currentPilotId(): Long? = pilotId
        override fun bearerToken(): String? = token
        override fun recover(): Boolean = false
    }

    private companion object {
        const val TAG = "DispatchTelemetry"
        const val SERIAL_SOURCE = "FLIGHT_CONTROLLER"
        const val ORDER_REFRESH_MS = 30_000L
        const val DEFAULT_INTERVAL_MS = 2_000L
        const val MIN_INTERVAL_MS = 1_000L
    }
}
