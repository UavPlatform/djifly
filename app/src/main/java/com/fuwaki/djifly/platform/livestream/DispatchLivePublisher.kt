package com.fuwaki.djifly.platform.livestream

import android.os.SystemClock
import android.util.Log
import com.fuwaki.djifly.di.ApplicationScope
import com.fuwaki.djifly.platform.network.PlatformApiService
import com.fuwaki.djifly.platform.network.generated.ApiDispatchLiveHeartbeatDTO
import com.fuwaki.djifly.platform.network.generated.ApiDispatchLiveStartDTO
import com.fuwaki.djifly.platform.telemetry.DispatchTelemetryService
import com.fuwaki.djifly.sdk.DjiSdkManager
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

data class DispatchPublisherState(
    val orderId: String? = null,
    val streaming: Boolean = false,
    val error: String? = null
)

/** Owns the publisher and lease for one server-registered flight connection. */
@Singleton
class DispatchLivePublisher @Inject constructor(
    private val api: PlatformApiService,
    private val telemetry: DispatchTelemetryService,
    private val sdkManager: DjiSdkManager,
    private val video: DjiTrtcLiveService,
    @ApplicationScope private val scope: CoroutineScope
) {
    private data class Session(
        val orderId: String,
        val connectionId: String,
        val liveId: String,
        val roomId: String
    )

    private val mutex = Mutex()
    private val _state = MutableStateFlow(DispatchPublisherState())
    val state = _state.asStateFlow()
    private var session: Session? = null

    fun toggle(orderId: String) {
        scope.launch(Dispatchers.Main.immediate) {
            if (!mutex.tryLock()) return@launch
            try {
                val current = session
                if (current?.orderId == orderId) {
                    finish(current)
                } else {
                    if (current != null) finish(current)
                    start(orderId)
                }
            } finally {
                mutex.unlock()
            }
        }
    }

    private suspend fun start(orderId: String) {
        val connection = telemetry.state.value.connection
        val connectionId = connection?.serverId
        if (connection?.orderId != orderId || connectionId.isNullOrBlank() ||
            !isCurrentConnection(orderId, connectionId)
        ) {
            fail("当前订单的飞行连接尚未登记，请稍后重试")
            return
        }
        if (video.isSessionActive) {
            fail("已有设备直播正在运行，请先关闭后再开启订单直播")
            return
        }
        _state.value = DispatchPublisherState(orderId)
        val requestKey = UUID.randomUUID().toString()
        val response = try {
            withTimeoutOrNull(START_TIMEOUT_MS) {
                api.startDispatchLive(orderId, connectionId, ApiDispatchLiveStartDTO(requestKey))
            }
        } catch (_: IOException) {
            null
        }
        val body = response?.body()
        val ticket = body?.data
        val liveId = ticket?.liveId
        if (response?.isSuccessful != true || body?.success != true || liveId.isNullOrBlank()) {
            Log.w(TAG, "start rejected: HTTP ${response?.code()}")
            fail(body?.message?.takeIf { it.isNotBlank() && body.success != true }
                ?: "无法开启订单直播${response?.let { "（HTTP ${it.code()}）" }.orEmpty()}，请重试")
            return
        }

        val current = Session(orderId, connectionId, liveId, ticket.roomId.orEmpty())
        session = current
        val appId = ticket.sdkAppId?.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt() ?: 0
        val userId = ticket.userId.orEmpty()
        val userSig = ticket.userSig.orEmpty()
        val roomKey = ticket.privateMapKey.orEmpty()
        if (ticket.connectionId != connectionId || appId <= 0 || current.roomId.isBlank() ||
            userId.isBlank() || userSig.isBlank() || roomKey.isBlank()
        ) {
            finish(current, "平台返回的直播凭证不完整")
            return
        }
        if (!isCurrentConnection(orderId, connectionId) || video.isSessionActive) {
            finish(current, "飞行连接或直播状态已变化，请重新开播")
            return
        }

        video.start(DjiTrtcLiveConfig(appId, userId, userSig, current.roomId, privateMapKey = roomKey))
        Log.i(TAG, "order live session created")
        scope.launch(Dispatchers.Main.immediate) { monitor(current) }
    }

    private suspend fun monitor(current: Session) {
        var lastLease = SystemClock.elapsedRealtime()
        var lastHeartbeat = 0L
        while (true) {
            delay(500)
            if (session !== current) return
            val now = SystemClock.elapsedRealtime()
            val running = video.isSendingVideo(current.roomId)
            val error = when {
                !isCurrentConnection(current.orderId, current.connectionId) -> "飞行连接已结束，直播已停止"
                video.status.value.roomId != current.roomId ||
                    video.status.value.phase == DjiTrtcLivePhase.Error -> "图传已中断，请重新开播"
                !running && (_state.value.streaming || now - lastLease >= LEASE_SAFETY_MS) -> "未收到持续图传画面，直播已停止"
                now - lastLease >= LEASE_SAFETY_MS -> "直播连接超时，请重新开播"
                else -> null
            }
            if (error != null) {
                mutex.withLock { if (session === current) finish(current, error) }
                return
            }
            if (!running || now - lastHeartbeat < HEARTBEAT_MS) continue

            lastHeartbeat = now
            val response = try {
                withTimeoutOrNull(HEARTBEAT_TIMEOUT_MS) {
                    api.heartbeatDispatchLive(
                        current.orderId, current.connectionId, current.liveId, ApiDispatchLiveHeartbeatDTO("RUNNING")
                    )
                }
            } catch (_: IOException) {
                null
            }
            if (session !== current) return
            val status = response?.body()
            if (response?.isSuccessful == true && status?.success == true && status.data?.state == "RUNNING") {
                lastLease = now
                if (!_state.value.streaming) {
                    Log.i(TAG, "order live RUNNING after TRTC first frame")
                    _state.value = DispatchPublisherState(current.orderId, streaming = true)
                }
            } else if (response != null && response.code() < 500) {
                Log.w(TAG, "heartbeat rejected: HTTP ${response.code()}")
                mutex.withLock { if (session === current) finish(current, "平台已结束直播，请重新开播") }
                return
            }
        }
    }

    private fun isCurrentConnection(orderId: String, connectionId: String): Boolean {
        val current = telemetry.state.value
        val connection = current.connection
        return sdkManager.sdkStatus.value.isProductConnected && current.currentOrder?.orderId == orderId &&
            connection?.orderId == orderId && connection.serverId == connectionId && connection.endedAt == null
    }

    private suspend fun finish(current: Session, error: String? = null) {
        session = null
        if (video.status.value.roomId == current.roomId && current.roomId.isNotBlank()) video.stop()
        try {
            val response = withTimeoutOrNull(HEARTBEAT_TIMEOUT_MS) {
                api.stopDispatchLive(current.orderId, current.connectionId, current.liveId)
            }
            Log.i(TAG, "order live stopped: HTTP ${response?.code()}")
        } catch (_: IOException) {
            Log.w(TAG, "stop unavailable; order live lease will expire")
        } finally {
            _state.value = DispatchPublisherState(error = error)
        }
    }

    private fun fail(message: String) {
        _state.value = DispatchPublisherState(error = message)
    }

    private companion object {
        const val TAG = "DispatchLivePublisher"
        const val START_TIMEOUT_MS = 8_000L
        const val HEARTBEAT_MS = 5_000L
        const val HEARTBEAT_TIMEOUT_MS = 3_000L
        const val LEASE_SAFETY_MS = 15_000L
    }
}
