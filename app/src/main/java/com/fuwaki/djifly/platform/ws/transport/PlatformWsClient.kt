package com.fuwaki.djifly.platform.ws.transport

import android.util.Log
import com.fuwaki.djifly.di.ApplicationScope
import com.fuwaki.djifly.platform.network.PlatformEndpointResolver
import com.fuwaki.djifly.platform.ws.WsCommunicationState
import com.fuwaki.djifly.platform.ws.WsConnectionState
import com.fuwaki.djifly.platform.ws.protocol.PlatformWsInboundEnvelope
import com.fuwaki.djifly.platform.ws.protocol.PlatformWsMessageCodec
import com.fuwaki.djifly.platform.ws.session.PlatformWsRunResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlatformWsClient @Inject constructor(
    private val endpointResolver: PlatformEndpointResolver,
    private val okHttpClient: OkHttpClient,
    private val codec: PlatformWsMessageCodec,
    private val communicationState: WsCommunicationState,
    @ApplicationScope private val externalScope: CoroutineScope
) {

    private val tag = "PlatformWsClient"
    private val activeWebSocket = AtomicReference<WebSocket?>(null)

    suspend fun run(
        deviceId: String,
        onOpen: suspend (WsMessageSender) -> Unit,
        onMessage: suspend (PlatformWsInboundEnvelope, WsMessageSender) -> Unit
    ): PlatformWsRunResult {
        val webSocketUrl = runCatching {
            endpointResolver.resolveDroneWebSocketUrl(deviceId)
        }.getOrElse { error ->
            return PlatformWsRunResult(
                opened = false,
                fatal = true,
                reason = error.message ?: "无法构建平台 WebSocket 地址"
            )
        }

        communicationState.updateConnectionState(WsConnectionState.Connecting)

        val handshake = CompletableDeferred<WebSocketHandshakeResult>()
        val closedReason = CompletableDeferred<String>()
        val senderRef = AtomicReference<WsMessageSender?>(null)

        val request = Request.Builder()
            .url(webSocketUrl)
            .build()

        val webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!handshake.isCompleted) {
                    handshake.complete(WebSocketHandshakeResult.Opened)
                }
                Log.i(tag, "websocket connected: $deviceId")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val sender = senderRef.get() ?: return
                externalScope.launch {
                    val inbound = codec.decode(text)
                    if (inbound == null) {
                        communicationState.logReceived("INVALID", null, text)
                        Log.w(tag, "parse websocket payload failed")
                        return@launch
                    }

                    communicationState.logReceived(
                        type = inbound.typeRaw ?: "UNKNOWN",
                        name = inbound.nameRaw,
                        content = inbound.rawText
                    )

                    runCatching { onMessage(inbound, sender) }
                        .onFailure { error ->
                            Log.w(tag, "handle message failed: ${error.message}")
                        }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (!handshake.isCompleted) {
                    handshake.complete(
                        WebSocketHandshakeResult.Failed(
                            "WebSocket 被服务端关闭（$code: ${reason.ifBlank { "no reason" }}）"
                        )
                    )
                }
                if (!closedReason.isCompleted) {
                    closedReason.complete("WebSocket 正在关闭（$code: ${reason.ifBlank { "no reason" }}）")
                }
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!handshake.isCompleted) {
                    handshake.complete(
                        WebSocketHandshakeResult.Failed(
                            "WebSocket 已关闭（$code: ${reason.ifBlank { "no reason" }}）"
                        )
                    )
                }
                if (!closedReason.isCompleted) {
                    closedReason.complete("WebSocket 已关闭（$code: ${reason.ifBlank { "no reason" }}）")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val message = t.message ?: "unknown error"
                if (!handshake.isCompleted) {
                    handshake.complete(WebSocketHandshakeResult.Failed("WebSocket 连接失败：$message"))
                }
                if (!closedReason.isCompleted) {
                    closedReason.complete("WebSocket 异常断开：$message")
                }
            }
        })

        val sender = JsonWsMessageSender(codec, communicationState, webSocket)
        senderRef.set(sender)
        activeWebSocket.set(webSocket)

        try {
            val handshakeResult = try {
                withTimeout(WEBSOCKET_CONNECT_TIMEOUT_MS) {
                    handshake.await()
                }
            } catch (_: TimeoutCancellationException) {
                WebSocketHandshakeResult.Failed("WebSocket 建连超时")
            }

            when (handshakeResult) {
                is WebSocketHandshakeResult.Failed -> {
                    communicationState.updateConnectionState(WsConnectionState.Error(handshakeResult.reason))
                    webSocket.cancel()
                    return PlatformWsRunResult(
                        opened = false,
                        fatal = false,
                        reason = handshakeResult.reason
                    )
                }

                WebSocketHandshakeResult.Opened -> {
                    val startupFailure = runCatching {
                        communicationState.updateConnectionState(WsConnectionState.Connected)
                        onOpen(sender)
                    }.exceptionOrNull()

                    if (startupFailure != null) {
                        val reason = startupFailure.message ?: "WebSocket 会话初始化失败"
                        communicationState.updateConnectionState(WsConnectionState.Error(reason))
                        webSocket.cancel()
                        return PlatformWsRunResult(
                            opened = false,
                            fatal = true,
                            reason = reason
                        )
                    }
                }
            }

            val reason = closedReason.await()
            communicationState.updateConnectionState(WsConnectionState.Disconnected(reason))
            return PlatformWsRunResult(
                opened = true,
                fatal = false,
                reason = reason
            )
        } finally {
            activeWebSocket.compareAndSet(webSocket, null)
            webSocket.close(WEBSOCKET_NORMAL_CLOSE_CODE, "client session end")
            webSocket.cancel()
        }
    }

    fun disconnect() {
        val socket = activeWebSocket.getAndSet(null) ?: return
        socket.close(WEBSOCKET_NORMAL_CLOSE_CODE, "session switched")
        socket.cancel()
        communicationState.updateConnectionState(WsConnectionState.Idle)
    }

    private sealed interface WebSocketHandshakeResult {
        data object Opened : WebSocketHandshakeResult
        data class Failed(val reason: String) : WebSocketHandshakeResult
    }

    private companion object {
        const val WEBSOCKET_CONNECT_TIMEOUT_MS = 10_000L
        const val WEBSOCKET_NORMAL_CLOSE_CODE = 1000
    }
}
