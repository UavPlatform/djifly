package com.fuwaki.djifly.platform.telemetry

import android.content.Context
import android.util.Log
import com.fuwaki.djifly.di.ApplicationScope
import com.fuwaki.djifly.platform.dispatch.DispatchStages
import com.fuwaki.djifly.platform.network.PlatformApiService
import com.fuwaki.djifly.platform.network.generated.ApiDispatchOrderVO
import com.fuwaki.djifly.platform.network.generated.ApiFlightConnectDTO
import com.fuwaki.djifly.platform.network.generated.ApiFlightConnectionVO
import com.fuwaki.djifly.platform.network.generated.ApiFlightSampleBatchDTO
import com.fuwaki.djifly.sdk.DjiSdkManager
import com.google.gson.Gson
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
import javax.inject.Inject
import javax.inject.Singleton

/** 首页展示用的执飞状态。 */
data class DispatchFlightState(
    val currentOrder: ApiDispatchOrderVO? = null,
    val connection: LocalConnection? = null,
    val pendingSamples: Int = 0,
    val cacheWarning: String? = null,
    val lastError: String? = null
)

/**
 * 派单执行时的自动连机记录与遥测上报（ADR-0007 #6、ADR-0009）。两个独立协程：
 * - 采样协程：按间隔（默认 2 秒，最短 1 秒）读 SDK 状态，连上飞机即在本机记录连机（机型、机身号、开始时间），
 *   采样追加写入本机队列；只做本地 IO，不等网络；
 * - 上传协程：每 [UPLOAD_MS] 刷新当前订单、登记本机连机记录、补传采样、上报连接结束；断网时跳过，下一轮重试。
 * 当前订单会落盘，离线重启后仍能记录。
 */
@Singleton
class DispatchTelemetryService @Inject constructor(
    @ApplicationContext context: Context,
    private val api: PlatformApiService,
    private val sdkManager: DjiSdkManager,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val tag = "DispatchTelemetry"
    private val dir = File(context.filesDir, "telemetry")
    private val orderFile = File(dir, "current-order.json")
    private val gson = Gson()
    private val recorder = FlightRecorder(
        FileTelemetryQueue(File(dir, "pending-samples.jsonl")),
        FileConnectionJournal(File(dir, "connections.jsonl"))
    )
    private val flightApi = object : FlightApi {
        override suspend fun connect(orderId: String, request: ApiFlightConnectDTO): ApiReply<ApiFlightConnectionVO> {
            val r = api.connectFlight(orderId, request)
            return ApiReply(r.code(), r.body()?.data)
        }
        override suspend fun upload(orderId: String, connectionId: String, batch: ApiFlightSampleBatchDTO): Int =
            api.uploadFlightSamples(orderId, connectionId, batch).code()
        override suspend fun close(orderId: String, connectionId: String): Int = api.closeFlight(orderId, connectionId).code()
    }
    private val _state = MutableStateFlow(DispatchFlightState(currentOrder = loadOrder()))
    val state: StateFlow<DispatchFlightState> = _state.asStateFlow()

    /** 采样间隔，毫秒；会被夹到 [TelemetryInterval.MIN_MS] 以上。 */
    @Volatile var intervalMs: Long = TelemetryInterval.DEFAULT_MS
        set(value) { field = TelemetryInterval.clamp(value) }

    private var samplingJob: Job? = null
    private var uploadJob: Job? = null
    private var lastOrderRefresh = 0L

    @Synchronized
    fun start() {
        if (samplingJob != null) return
        samplingJob = scope.launch {
            while (isActive) {
                runCatching { recorder.record(System.currentTimeMillis(), snapshot()) }
                    .onFailure { Log.w(tag, "telemetry sample failed: ${it.message}") }
                publish()
                delay(intervalMs)
            }
        }
        uploadJob = scope.launch {
            while (isActive) {
                if (System.currentTimeMillis() - lastOrderRefresh > ORDER_REFRESH_MS) refreshOrders()
                runCatching { recorder.sync(flightApi) }.onFailure { Log.w(tag, "telemetry sync failed: ${it.message}") }
                publish()
                delay(UPLOAD_MS)
            }
        }
    }

    /** 刷新当前订单（首页下拉刷新时也会调用）；失败时保留上次的订单，离线也能继续记录。 */
    suspend fun refreshOrders() {
        lastOrderRefresh = System.currentTimeMillis()
        val response = runCatching { api.getMyDispatchOrders() }.getOrNull() ?: return
        if (!response.isSuccessful) return
        val order = DispatchStages.current(response.body()?.data.orEmpty())
        runCatching { dir.mkdirs(); if (order == null) orderFile.delete() else orderFile.writeText(gson.toJson(order)) }
        _state.value = _state.value.copy(currentOrder = order)
    }

    private fun snapshot(): FlightSnapshot {
        val status = sdkManager.sdkStatus.value
        return FlightSnapshot(
            productConnected = status.isProductConnected,
            order = _state.value.currentOrder,
            serialNumber = DroneModelMapper.validSerial(status.productInfo.serialNumber),
            modelCode = DroneModelMapper.modelCode(status.productInfo.type.name),
            modelName = status.productInfo.name,
            flight = status.flightData
        )
    }

    private fun publish() {
        val s = recorder.status()
        _state.value = _state.value.copy(connection = s.active, pendingSamples = s.pendingSamples,
            cacheWarning = s.cacheWarning, lastError = s.error)
    }

    private fun loadOrder(): ApiDispatchOrderVO? =
        runCatching { gson.fromJson(orderFile.readText(), ApiDispatchOrderVO::class.java) }.getOrNull()

    private companion object {
        const val ORDER_REFRESH_MS = 30_000L
        const val UPLOAD_MS = 5_000L
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
