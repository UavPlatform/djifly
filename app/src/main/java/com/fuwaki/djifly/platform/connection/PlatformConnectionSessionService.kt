package com.fuwaki.djifly.platform.connection

import com.fuwaki.djifly.platform.registration.RegisteredDroneSnapshot
import com.fuwaki.djifly.platform.ws.WsReconnectBackoff
import com.fuwaki.djifly.platform.ws.session.PlatformWsSessionService
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlatformConnectionSessionService @Inject constructor(
    private val connectionRepository: PlatformConnectionRepository,
    private val wsSessionService: PlatformWsSessionService
) {

    // 重连节奏抽到 WsReconnectBackoff，与聊天通道（ChatSessionSupervisor）共用同一节奏
    private val backoff = WsReconnectBackoff()

    suspend fun maintainRegisteredSession(
        serialNumber: String,
        snapshot: RegisteredDroneSnapshot,
        onConnected: () -> Unit,
        onRetryScheduled: (nextAttempt: Int, retryAfterSeconds: Long, lastError: String) -> Unit
    ): PlatformConnectionSessionResult {
        var reconnectAttempt = 1

        while (currentCoroutineContext().isActive) {
            when (val result = connectionRepository.requestWebSocketConnection(serialNumber)) {
                is PlatformConnectionResult.Success -> {
                    val webSocketResult = wsSessionService.connectAndRun(
                        serialNumber = serialNumber,
                        snapshot = snapshot,
                        onConnected = onConnected
                    )

                    if (webSocketResult.fatal) {
                        return PlatformConnectionSessionResult.Failed(
                            reason = webSocketResult.reason,
                            retryable = false
                        )
                    }

                    if (!currentCoroutineContext().isActive) {
                        return PlatformConnectionSessionResult.Stopped
                    }

                    if (webSocketResult.opened) {
                        reconnectAttempt = 1
                    }

                    val retryDelayMillis = backoff.delayMillisForAttempt(reconnectAttempt)
                    onRetryScheduled(
                        reconnectAttempt + 1,
                        retryDelayMillis / 1000,
                        webSocketResult.reason
                    )
                    delay(retryDelayMillis)
                    reconnectAttempt += 1
                }

                is PlatformConnectionResult.Failure -> {
                    val shouldRetry = result.retryable || result.message.contains("已连接")
                    if (!shouldRetry) {
                        return PlatformConnectionSessionResult.Failed(
                            reason = result.message,
                            retryable = false
                        )
                    }

                    val retryDelayMillis = backoff.delayMillisForAttempt(reconnectAttempt)
                    onRetryScheduled(
                        reconnectAttempt + 1,
                        retryDelayMillis / 1000,
                        result.message
                    )
                    delay(retryDelayMillis)
                    reconnectAttempt += 1
                }
            }
        }

        return PlatformConnectionSessionResult.Stopped
    }

    fun disconnect() {
        wsSessionService.disconnect()
    }
}
