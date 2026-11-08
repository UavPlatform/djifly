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
import com.fuwaki.djifly.platform.eventstream.EventCaptureResult
import com.fuwaki.djifly.platform.eventstream.EventDraft
import com.fuwaki.djifly.platform.eventstream.EventInput
import com.fuwaki.djifly.platform.eventstream.EventRateClass
import com.fuwaki.djifly.platform.eventstream.EventStreamCoordinator
import com.fuwaki.djifly.platform.eventstream.RecordingOperation
import com.fuwaki.djifly.platform.network.PlatformApiService
import com.fuwaki.djifly.platform.network.PlatformEndpointResolver
import com.fuwaki.djifly.platform.network.generated.ApiDispatchOrderVO
import com.fuwaki.djifly.platform.network.generated.ApiEventReconciliationDraftDTO
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
    private val telemetryUiState: TelemetryUiState,
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
    private var lastGateReason: String? = null
    private var lastReconcileAt = 0L

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
            publishGateReason(pilotId, order?.orderId, serial, model, status)
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
                // A different airframe is a different chain: drop the recovered link with it.
                chainIdForContext = null
                previousLink = null
            }
            aircraftGeneration = coordinator.aircraftConnected(aircraft)
            lastAircraft = aircraft
            // previousLink is NOT reset here: a recovered link belongs to this same chain, and dropping it while
            // keeping chainIdForContext would re-send height=1 against a chain the backend already holds.
            lastPacketContext = null
            lastFlying = null
        }

        val generation = aircraftGeneration ?: return
        val inputs = buildInputs(status, capturedAt)
        if (inputs.isNotEmpty()) {
            runCatching { coordinator.record(inputs, generation) }
                .onSuccess { result ->
                    // Only drafts that actually reached the journal are shown; throttled/ignored input is not an event.
                    if (result is EventCaptureResult.Recorded) publishCaptured(result.events)
                }
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
        // Chain continuity depends on the chain identity, not on recordingGeneration: that generation bumps on every
        // reconnect/rebuild, so comparing whole contexts would drop the link and restart the chain at height 1.
        if (context.chainId != lastPacketContext?.chainId) {
            previousLink = null
        }
        lastPacketContext = context
        val packet = encoder.encode(context, drafts, previousLink)
        val activeUploader = uploader ?: error("No pilot authentication snapshot")
        activeUploader.enqueue(operation, packet)
        previousLink = packet.link
        scheduleAttempt(activeUploader, operation, packet.link.packetId)
    }

    /**
     * The panel is otherwise a silent empty list; state exactly which precondition is missing so an operator can act
     * on it. Never invent events to fill the panel.
     */
    private fun publishGateReason(
        pilotId: Long?,
        orderId: String?,
        serial: String?,
        model: String?,
        status: DjiSdkStatus,
    ) {
        val reason = when {
            pilotId == null -> "未登录，本地事件记录未启用"
            orderId == null -> "暂无执飞订单，本地事件记录未启用"
            !status.isProductConnected -> "飞机未连接，本地事件记录未启用"
            serial == null -> "未读取到飞机序列号，本地事件记录未启用"
            model == null -> "未识别机型（${status.productInfo.type.name}），本地事件记录未启用"
            else -> return
        }
        if (reason != lastGateReason) {
            lastGateReason = reason
            Log.i(TAG, "event capture gated: $reason")
        }
        telemetryUiState.updateStreamState(TelemetryStreamState.Disconnected(reason))
    }

    private fun publishCaptured(drafts: List<EventDraft>) {
        telemetryUiState.record(
            drafts.map { draft ->
                TelemetryMessageLog(
                    timestamp = draft.capturedAt,
                    direction = TelemetryMessageDirection.LOCAL,
                    type = draft.type,
                    name = draft.context.aircraft.modelCode,
                    content = draft.payload.entries.joinToString(", ") { (key, value) -> "$key=$value" },
                )
            }
        )
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
                Stage1UploadResult.ChainForked -> reconcileChain(activeUploader, operation, packetId)
                is Stage1UploadResult.Durable, Stage1UploadResult.AlreadyDurable -> lastUploadError = null
                is Stage1UploadResult.NotDue, is Stage1UploadResult.RetryAfter -> Unit
            }
        }
    }

    /**
     * A fork means the local chain disagrees with what the backend already holds. The backend can state the height and
     * hash of its contiguous prefix; adopt exactly that as the new local link so the next packet continues the real
     * chain instead of restarting at height 1. Anything less certain (no usable prefix, or a prefix the local journal
     * cannot corroborate) stays parked and visible: this never invents chain content.
     *
     * Packets already recorded on the old link are NOT resent: their prevChainHash is now wrong and would fork again.
     * They stay in the journal for later explicit reconciliation, while live capture continues from the adopted link.
     */
    private suspend fun reconcileChain(activeUploader: Stage1Uploader, operation: String, packetId: String) {
        val orderId = operationOrderId ?: return
        val now = System.currentTimeMillis()
        if (now - lastReconcileAt < RECONCILE_INTERVAL_MS) return
        lastReconcileAt = now
        val local = runCatching { store.packet(operation, packetId) }.getOrNull() ?: return
        val expected = runCatching { Stage1Receipt.readSummary(local.summary, authentication.pilotId ?: return) }
            .getOrNull() ?: return
        val identity = expected.link.identity
        val response = runCatching {
            api.reconcileEventChain(
                orderId,
                ApiEventReconciliationDraftDTO(
                    chainId = identity.chainId,
                    schemaVersion = 2,
                    fromHeight = 1L,
                    toHeight = expected.link.height,
                ),
            )
        }.getOrNull()
        val data = response?.takeIf { it.isSuccessful }?.body()?.data
        val contiguousHeight = data?.contiguousHeight
        val contiguousHash = data?.contiguousHash
        if (data == null || data.integrity != "OPEN" || contiguousHeight == null) {
            lastUploadError = "事件链冲突，对账未取得可用共同前缀，上传暂停"
            Log.w(TAG, "reconcile unusable: ${response?.code()} ${response?.body()?.errorCode}")
            return
        }
        if (contiguousHeight == 0L) {
            // Backend holds no prefix for this chain, so the local anchor is a legal continuation point. Packets the
            // journal already recorded at height 1 remain unconfirmed and are left parked rather than resent.
            previousLink = null
            lastPacketContext = null
            activeUploader.clearChainFork(identity.chainId)
            lastUploadError = "事件链已按后端空链重置，历史未确认包保留待人工处理"
            Log.i(TAG, "reconcile: backend has no prefix for ${identity.chainId}, continuing from anchor")
            return
        }
        if (contiguousHash.isNullOrBlank()) {
            lastUploadError = "事件链冲突，对账返回缺少哈希，上传暂停"
            Log.w(TAG, "reconcile: non-zero height without contiguousHash")
            return
        }
        val adopted = localLinkFor(operation, identity.chainId, contiguousHeight, contiguousHash)
        if (adopted == null) {
            lastUploadError = "事件链冲突，后端前缀在本地无对应记录，需人工对账"
            Log.w(TAG, "reconcile: no local packet matches height=$contiguousHeight")
            return
        }
        previousLink = adopted
        lastPacketContext = null
        activeUploader.clearChainFork(identity.chainId)
        lastUploadError = null
        Log.i(TAG, "reconcile: adopted backend prefix height=$contiguousHeight, live capture continues")
    }

    /** Local packet at the backend's contiguous height, used as the trusted continuation point. */
    private fun localLinkFor(
        operation: String,
        chainId: String,
        height: Long,
        hash: String,
    ): Stage1Link? {
        val pilotId = authentication.pilotId ?: return null
        val snapshot = runCatching { store.recoverOperation(operation) }.getOrNull() ?: return null
        val match = snapshot.packets.firstOrNull { stored ->
            val summary = runCatching { Stage1Receipt.readSummary(stored.summary, pilotId) }.getOrNull()
            summary != null && summary.link.identity.chainId == chainId &&
                summary.link.height == height && summary.link.chainHash == hash
        } ?: return null
        return runCatching { Stage1Receipt.readSummary(match.summary, pilotId) }.getOrNull()?.link
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
        telemetryUiState.updateStreamState(
            when {
                // Capture can be healthy while upload is stuck; a fork/error must not be masked by "Connected".
                lastUploadError != null -> TelemetryStreamState.Error(lastUploadError!!)
                active -> TelemetryStreamState.Connected
                authentication.pilotId == null -> TelemetryStreamState.Disconnected("未登录，本地事件记录未启用")
                _state.value.currentOrder?.orderId == null -> TelemetryStreamState.Disconnected("暂无执飞订单，本地事件记录未启用")
                !status.isProductConnected -> TelemetryStreamState.Disconnected("飞机未连接，本地事件记录未启用")
                else -> TelemetryStreamState.Connecting
            }
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
        /** Reconciliation is a repair step, not a polling loop: at most one attempt per minute per process. */
        const val RECONCILE_INTERVAL_MS = 60_000L
    }
}
