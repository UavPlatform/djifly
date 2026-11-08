package com.fuwaki.djifly.platform.livestream

import android.os.SystemClock
import android.util.Log
import com.fuwaki.djifly.di.ApplicationScope
import com.fuwaki.djifly.platform.network.PlatformApiService
import com.fuwaki.djifly.platform.network.generated.ApiDispatchLiveHeartbeatDTO
import com.fuwaki.djifly.platform.network.generated.ApiDispatchLiveStartDTO
import com.fuwaki.djifly.platform.network.generated.ApiResultDispatchLiveCredentialsVO
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
import retrofit2.Response

/** Outcome of one heartbeat, independent of Android/coroutine machinery. */
internal enum class HeartbeatVerdict {
    /** Server confirmed the lease; the local deadline may slide. */
    RENEWED,

    /** Server actively refused (4xx): the session is over and must not be revived. */
    ENDED,

    /** No usable answer (5xx or no response). The lease did NOT move; retry until the local bound. */
    NOT_RENEWED;

    internal companion object {
        /**
         * A 5xx or missing response must not renew the lease and must not tear the session down: a transient
         * outage should be able to recover, and the local lease bound ends the wait if it does not.
         */
        fun of(code: Int?, successful: Boolean): HeartbeatVerdict = when {
            successful && code != null -> RENEWED
            code == null -> NOT_RENEWED
            code < 500 -> ENDED
            else -> NOT_RENEWED
        }
    }
}

data class DispatchPublisherState(
    val orderId: String? = null,
    val streaming: Boolean = false,
    val error: String? = null
)

/** Owns the publisher and lease for one order-level live session. */
@Singleton
class DispatchLivePublisher @Inject constructor(
    private val api: PlatformApiService,
    private val video: DjiTrtcLiveService,
    @ApplicationScope private val scope: CoroutineScope
) {
    private data class Session(
        val orderId: String,
        val liveId: String,
        val roomId: String
    )

    /** Serializes whole user operations (start/stop), so a second tap queues instead of being dropped. */
    private val opMutex = Mutex()

    /** Guards only [session] and [state] transitions; never held across network I/O or TRTC calls. */
    private val mutex = Mutex()

    private val _state = MutableStateFlow(DispatchPublisherState())
    val state = _state.asStateFlow()

    @Volatile
    private var session: Session? = null

    fun toggle(orderId: String) {
        scope.launch(Dispatchers.Main.immediate) {
            opMutex.withLock {
                val previous = mutex.withLock {
                    val current = session
                    if (current != null) {
                        session = null
                        _state.value = DispatchPublisherState()
                    }
                    current
                }
                // Teardown happens outside [mutex] so observers and the UI are never blocked by a stop call.
                if (previous != null) teardown(previous)
                if (previous?.orderId != orderId) start(orderId)
            }
        }
    }

    /**
     * Non-blocking hand-off for UI lifecycle (leaving the flight screen). Ends the current order-live session
     * through the same path as [toggle] instead of relying on the monitor loop noticing the TRTC room went away.
     * @return true when an order-live session existed and its teardown was scheduled.
     */
    fun stopNow(): Boolean {
        val current = session ?: return false
        scope.launch(Dispatchers.Main.immediate) {
            opMutex.withLock {
                if (!detach(current, null)) return@withLock
                teardown(current)
            }
        }
        return true
    }

    private suspend fun start(orderId: String) {
        if (video.isSessionActive) {
            fail("已有图传会话正在运行，请先关闭后再开启订单直播")
            return
        }
        _state.value = DispatchPublisherState(orderId)
        // One requestKey per start attempt. The server treats a repeat as the same session, so a start that
        // timed out after the server committed is resolved instead of creating a duplicate session.
        val requestKey = UUID.randomUUID().toString()
        var response = requestStart(orderId, requestKey)
        val timedOut = response == null
        if (timedOut) {
            Log.w(TAG, "start timed out; retrying once with the same requestKey")
            response = requestStart(orderId, requestKey)
        }
        val body = response?.body()
        val ticket = body?.data
        val liveId = ticket?.liveId
        if (response?.isSuccessful != true || body?.success != true || liveId.isNullOrBlank()) {
            Log.w(TAG, "start rejected: HTTP ${response?.code()}")
            // Both attempts timed out, so the commit state is unknown. `stop` is not idempotent for an unknown
            // liveId, and the server expires an unrenewed lease in ~20s, so report the situation instead of
            // guessing. Retrying the start with the same key later resolves it to that session.
            fail(
                body?.message?.takeIf { it.isNotBlank() && body.success != true }
                    ?: if (timedOut) "开启直播超时；若平台已记录该会话，约 20 秒后自动失效，请稍后重试"
                    else "无法开启订单直播（HTTP ${response?.code()}），请重试"
            )
            return
        }

        val current = Session(orderId, liveId, ticket.roomId.orEmpty())
        mutex.withLock { session = current }
        val appId = ticket.sdkAppId?.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt() ?: 0
        val userId = ticket.userId.orEmpty()
        val userSig = ticket.userSig.orEmpty()
        val roomKey = ticket.privateMapKey.orEmpty()
        if (appId <= 0 || current.roomId.isBlank() ||
            userId.isBlank() || userSig.isBlank() || roomKey.isBlank()
        ) {
            finish(current, "平台返回的直播凭证不完整")
            return
        }
        if (video.isSessionActive) {
            finish(current, "图传状态已变化，请重新开播")
            return
        }

        video.start(DjiTrtcLiveConfig(appId, userId, userSig, current.roomId, privateMapKey = roomKey))
        Log.i(TAG, "order live session created")
        scope.launch(Dispatchers.Main.immediate) { monitor(current) }
    }

    private suspend fun requestStart(
        orderId: String,
        requestKey: String
    ): Response<ApiResultDispatchLiveCredentialsVO>? = try {
        withTimeoutOrNull(START_TIMEOUT_MS) {
            api.startDispatchLive(orderId, ApiDispatchLiveStartDTO(requestKey))
        }
    } catch (_: IOException) {
        null
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
                video.status.value.roomId != current.roomId ||
                    video.status.value.phase == DjiTrtcLivePhase.Error -> "图传已中断，请重新开播"
                !running && (_state.value.streaming || now - lastLease >= LEASE_SAFETY_MS) -> "未收到持续图传画面，直播已停止"
                now - lastLease >= LEASE_SAFETY_MS -> "直播连接超时，请重新开播"
                else -> null
            }
            if (error != null) {
                // Detach before the stop call so the UI and other callers observe the end immediately.
                if (!detach(current, error)) return
                teardown(current)
                return
            }
            if (!running || now - lastHeartbeat < HEARTBEAT_MS) continue

            lastHeartbeat = now
            val response = try {
                withTimeoutOrNull(HEARTBEAT_TIMEOUT_MS) {
                    api.heartbeatDispatchLive(current.orderId, current.liveId, ApiDispatchLiveHeartbeatDTO("RUNNING"))
                }
            } catch (_: IOException) {
                null
            }
            if (session !== current) return
            when (val verdict = HeartbeatVerdict.of(response?.code(), response?.isSuccessful == true)) {
                HeartbeatVerdict.RENEWED -> {
                    lastLease = now
                    if (!_state.value.streaming) {
                        Log.i(TAG, "order live RUNNING after TRTC first frame")
                        _state.value = DispatchPublisherState(current.orderId, streaming = true)
                    }
                }
                HeartbeatVerdict.ENDED -> {
                    Log.w(TAG, "heartbeat rejected: HTTP ${response?.code()}")
                    if (!detach(current, "平台已结束直播，请重新开播")) return
                    teardown(current)
                    return
                }
                HeartbeatVerdict.NOT_RENEWED -> {
                    // 5xx or a dropped connection: the lease was NOT renewed. Keep looping so a short outage can
                    // recover; the local LEASE_SAFETY_MS bound ends the session if it does not.
                    Log.w(TAG, "heartbeat not renewed (${verdict}): " + (response?.let { "HTTP ${it.code()}" } ?: "no response"))
                }
            }
        }
    }

    /** Fast, lock-bounded transition. Returns false when [current] is no longer the live session. */
    private suspend fun detach(current: Session, error: String?): Boolean = mutex.withLock {
        if (session !== current) return@withLock false
        session = null
        _state.value = DispatchPublisherState(error = error)
        true
    }

    private suspend fun finish(current: Session, error: String? = null) {
        if (!detach(current, error)) return
        teardown(current)
    }

    /** Stops TRTC and releases the server session. Never runs while [mutex] is held. */
    private suspend fun teardown(current: Session) {
        if (video.status.value.roomId == current.roomId && current.roomId.isNotBlank()) video.stop()
        try {
            val response = withTimeoutOrNull(HEARTBEAT_TIMEOUT_MS) {
                api.stopDispatchLive(current.orderId, current.liveId)
            }
            Log.i(TAG, "order live stopped: HTTP ${response?.code()}")
        } catch (_: IOException) {
            Log.w(TAG, "stop unavailable; order live lease will expire")
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
