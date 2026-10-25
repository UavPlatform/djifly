package com.fuwaki.djifly.platform.telemetry

import android.content.Context
import android.util.Log
import com.fuwaki.djifly.di.ApplicationScope
import com.fuwaki.djifly.platform.dispatch.DispatchOrder
import com.fuwaki.djifly.platform.dispatch.DispatchStages
import com.fuwaki.djifly.platform.dispatch.FlightConnectRequest
import com.fuwaki.djifly.platform.dispatch.FlightConnection
import com.fuwaki.djifly.platform.dispatch.FlightSampleBatchRequest
import com.fuwaki.djifly.platform.network.PlatformApiService
import com.fuwaki.djifly.sdk.DjiSdkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** 首页 / 飞行页展示用的执飞状态。 */
data class DispatchFlightState(
    val currentOrder: DispatchOrder? = null,
    val connection: FlightConnection? = null,
    val pendingSamples: Int = 0,
    val lastError: String? = null
)

/**
 * 派单执行时的自动连机记录与遥测上报（ADR-0007 #6、ADR-0009）：
 * 1. 定期拉取本人派单，取「运输中」或「飞手到场」的订单作为当前订单；
 * 2. 连上飞机且读到机型与机身号后自动登记连机记录（无需手填编号），换机会生成新记录；
 * 3. 按固定间隔（默认 2 秒，最短 1 秒）采样，先写入本机持久队列，再批量上报；
 *    断网时保留，恢复后补传；飞机断开时结束连接。
 */
@Singleton
class DispatchTelemetryService @Inject constructor(
    @ApplicationContext context: Context,
    private val api: PlatformApiService,
    private val sdkManager: DjiSdkManager,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val tag = "DispatchTelemetry"
    private val queue = FileTelemetryQueue(File(context.filesDir, "telemetry/pending-samples.jsonl"))
    private val uploader = TelemetryUploader(queue, BatchSender { orderId, connectionId, samples ->
        runCatching { api.uploadFlightSamples(orderId, connectionId, FlightSampleBatchRequest(samples)) }
            .fold(
                onSuccess = { r -> TelemetryHttp.outcome(r.code()) },
                onFailure = { UploadOutcome.Retry }
            )
    })
    private val _state = MutableStateFlow(DispatchFlightState(pendingSamples = queue.size()))
    val state: StateFlow<DispatchFlightState> = _state.asStateFlow()

    /** 采样间隔，毫秒；外部可调（例如调试设置），会被夹到 [TelemetryInterval.MIN_MS] 以上。 */
    @Volatile var intervalMs: Long = TelemetryInterval.DEFAULT_MS
        set(value) { field = TelemetryInterval.clamp(value) }

    private var job: Job? = null
    private var sampler = TelemetrySampler()
    private var pendingConnectKey: Pair<String, String>? = null
    private var pendingRequestKey: String? = null
    private var lastOrderRefresh = 0L

    fun start() {
        if (job != null) return
        job = scope.launch {
            while (isActive) {
                runCatching { tick(System.currentTimeMillis()) }
                    .onFailure { Log.w(tag, "telemetry tick failed: ${it.message}") }
                delay(intervalMs)
            }
        }
    }

    /** 立即刷新当前订单（首页下拉刷新时调用）。 */
    suspend fun refreshOrders() {
        val response = runCatching { api.getMyDispatchOrders() }.getOrNull()
        if (response?.isSuccessful == true) {
            val orders = response.body()?.data.orEmpty()
            _state.value = _state.value.copy(currentOrder = DispatchStages.current(orders), lastError = null)
        }
        lastOrderRefresh = System.currentTimeMillis()
    }

    private suspend fun tick(now: Long) {
        if (now - lastOrderRefresh > ORDER_REFRESH_MS) refreshOrders()
        val status = sdkManager.sdkStatus.value
        val order = _state.value.currentOrder
        val serial = DroneModelMapper.validSerial(status.productInfo.serialNumber)
        val modelCode = DroneModelMapper.modelCode(status.productInfo.type.name)
        val active = _state.value.connection

        if (!status.isProductConnected || order == null || !DispatchStages.isFlyable(order) || serial == null || modelCode == null) {
            if (active != null) close(active)
        } else {
            val connection = if (active != null && active.orderId == order.orderId && active.serialNumber == serial) active
                else connect(order, serial, modelCode, status.productInfo.name)
            if (connection != null) {
                queue.add(QueuedSample(order.orderId, connection.connectionId, sampler.sample(status.flightData, now)))
            }
        }
        uploader.flush()
        _state.value = _state.value.copy(pendingSamples = queue.size())
    }

    private suspend fun connect(order: DispatchOrder, serial: String, modelCode: String, modelName: String): FlightConnection? {
        val key = order.orderId to serial
        // 同一次连机的重试复用同一个 requestKey，后端幂等，不会产生重复记录。
        if (pendingConnectKey != key) { pendingConnectKey = key; pendingRequestKey = UUID.randomUUID().toString() }
        val request = FlightConnectRequest(order.assignmentVersion, pendingRequestKey!!, serial, modelCode, modelName.ifBlank { modelCode })
        val response = runCatching { api.connectFlight(order.orderId, request) }.getOrNull() ?: return null
        val connection = response.body()?.data
        if (!response.isSuccessful || connection == null) {
            _state.value = _state.value.copy(lastError = "连机记录上报失败（${response.code()}）")
            if (response.code() == 409) refreshOrders()
            return null
        }
        pendingConnectKey = null; pendingRequestKey = null
        sampler = TelemetrySampler()
        _state.value = _state.value.copy(connection = connection.copy(orderId = order.orderId), lastError = null)
        return connection.copy(orderId = order.orderId)
    }

    private suspend fun close(connection: FlightConnection) {
        runCatching { api.closeFlight(connection.orderId ?: return, connection.connectionId) }
        _state.value = _state.value.copy(connection = null)
    }

    private companion object {
        const val ORDER_REFRESH_MS = 30_000L
    }
}

/** HTTP 状态码 → 是否从缓存删除。纯函数，便于单测。 */
object TelemetryHttp {
    fun outcome(code: Int): UploadOutcome = when (code) {
        in 200..299 -> UploadOutcome.Sent
        401, 408, 429 -> UploadOutcome.Retry
        in 400..499 -> UploadOutcome.Discard
        else -> UploadOutcome.Retry
    }
}
