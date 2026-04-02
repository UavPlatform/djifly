package com.fuwaki.djifly.platform.connection

import com.fuwaki.djifly.platform.registration.RegisteredDroneSnapshot
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

                    val retryDelayMillis = reconnectDelayForAttempt(reconnectAttempt)
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

                    val retryDelayMillis = reconnectDelayForAttempt(reconnectAttempt)
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

    private fun reconnectDelayForAttempt(attempt: Int): Long {
        val index = (attempt - 1).coerceIn(0, RETRY_DELAYS_MS.lastIndex)
        return RETRY_DELAYS_MS[index]
    }

    private companion object {
        val RETRY_DELAYS_MS = listOf(3_000L, 10_000L, 30_000L)
    }
}
