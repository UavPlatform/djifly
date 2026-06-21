package com.fuwaki.djifly.platform.chat

import android.util.Log
import com.fuwaki.djifly.data.auth.TokenStore
import com.fuwaki.djifly.data.error.AppError
import com.fuwaki.djifly.data.error.GlobalErrorHandler
import com.fuwaki.djifly.data.models.ChatEnvelope
import com.fuwaki.djifly.data.models.MessageVO
import com.fuwaki.djifly.di.ApplicationScope
import com.fuwaki.djifly.platform.network.PlatformEndpointResolver
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRealtimeClient @Inject constructor(
    private val endpointResolver: PlatformEndpointResolver,
    private val okHttpClient: OkHttpClient,
    private val tokenStore: TokenStore,
    private val gson: Gson,
    private val errorHandler: GlobalErrorHandler,
    @ApplicationScope private val externalScope: CoroutineScope
) {
    private val tag = "ChatRealtimeClient"
    private val activeSocket = AtomicReference<WebSocket?>(null)
    private var connectionJob: Job? = null
    private var connectedUserId: Long? = null

    private val _incomingMessages = MutableSharedFlow<MessageVO>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<MessageVO> = _incomingMessages.asSharedFlow()

    fun ensureConnected() {
        if (activeSocket.get() != null || connectionJob?.isActive == true) return

        connectionJob = externalScope.launch {
            val userId = tokenStore.userId.firstOrNull()
            if (userId == null || userId <= 0L) {
                Log.w(tag, "skip chat websocket: missing user id")
                return@launch
            }

            connectedUserId = userId
            val url = runCatching { endpointResolver.resolveChatWebSocketUrl(userId) }
                .getOrElse { error ->
                    errorHandler.emit(AppError.Unknown(error.message ?: "无法构建聊天连接地址"))
                    return@launch
                }

            val request = Request.Builder().url(url).build()
            val socket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.i(tag, "chat websocket connected: user=$userId")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val message = runCatching {
                        gson.fromJson(text, ChatEnvelope::class.java).toMessageVO()
                    }.onFailure { error ->
                        Log.w(tag, "parse chat websocket payload failed: ${error.message}")
                    }.getOrNull()

                    if (message != null && message.id.isNotBlank()) {
                        _incomingMessages.tryEmit(message)
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    activeSocket.compareAndSet(webSocket, null)
                    Log.i(tag, "chat websocket closed: $code ${reason.ifBlank { "no reason" }}")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    activeSocket.compareAndSet(webSocket, null)
                    Log.w(tag, "chat websocket failed: ${t.message}")
                    errorHandler.emit(AppError.Network("聊天实时连接失败，将使用刷新同步"))
                }
            })
            activeSocket.set(socket)
        }
    }

    fun reconnectIfUserChanged() {
        externalScope.launch {
            val userId = tokenStore.userId.firstOrNull()
            if (userId != null && userId > 0L && userId != connectedUserId) {
                disconnect()
                ensureConnected()
            }
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        connectedUserId = null
        activeSocket.getAndSet(null)?.let { socket ->
            socket.close(1000, "chat realtime disconnect")
            socket.cancel()
        }
    }
}
