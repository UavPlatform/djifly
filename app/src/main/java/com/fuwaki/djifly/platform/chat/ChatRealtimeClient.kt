package com.fuwaki.djifly.platform.chat

import android.util.Log
import com.fuwaki.djifly.data.auth.AccessTokenProvider
import com.fuwaki.djifly.data.auth.JwtUtils
import com.fuwaki.djifly.data.auth.TokenStore
import com.fuwaki.djifly.data.error.AppError
import com.fuwaki.djifly.data.error.GlobalErrorHandler
import com.fuwaki.djifly.data.models.ChatEnvelope
import com.fuwaki.djifly.data.models.MessageVO
import com.fuwaki.djifly.di.ApplicationScope
import com.fuwaki.djifly.platform.network.PlatformApiService
import com.fuwaki.djifly.platform.network.PlatformEndpointResolver
import com.fuwaki.djifly.platform.ws.WsReconnectBackoff
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 聊天实时客户端（追溯 FLY 高-4：原实现断线后无重连，实时通道死掉只能手动刷新）。
 *
 * - 断线自动重连：[ChatSessionSupervisor] 以与遥测 WS 一致的 3s/10s/30s 退避循环重建会话；
 * - 重连成功后调用 `chat/Message/sync` 补拉离线消息并推入 [incomingMessages]（离线补偿）；
 * - 握手鉴权（t3 协议）：/ws/{sid} 需 JWT 且 userId==sid，token 走 Authorization 头；
 * - 鉴权类失败分类（握手 HTTP 403，WS 层无 401）：本地 exp 预检——已过期先 refresh 再立即
 *   重试（上限 2 次防空转），未过期/刷新失败则 5 分钟级长退避并提示重新登录。
 */
@Singleton
class ChatRealtimeClient @Inject constructor(
    private val endpointResolver: PlatformEndpointResolver,
    private val okHttpClient: OkHttpClient,
    private val tokenStore: TokenStore,
    private val accessTokenProvider: AccessTokenProvider,
    private val apiService: PlatformApiService,
    private val gson: Gson,
    private val errorHandler: GlobalErrorHandler,
    @ApplicationScope private val externalScope: CoroutineScope
) {
    private val tag = "ChatRealtimeClient"
    private val activeSocket = AtomicReference<WebSocket?>(null)
    private var connectionJob: Job? = null
    @Volatile
    private var activeSupervisor: ChatSessionSupervisor? = null
    @Volatile
    private var connectedUserId: Long? = null
    private val backoff = WsReconnectBackoff()

    private companion object {
        /** t35 契约：新登录顶掉旧连接时服务端下发的 close code */
        const val CLOSE_CODE_REPLACED_BY_NEWER_LOGIN = 4001
    }

    private val _incomingMessages = MutableSharedFlow<MessageVO>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<MessageVO> = _incomingMessages.asSharedFlow()

    fun ensureConnected() {
        if (connectionJob?.isActive == true) return

        connectionJob = externalScope.launch {
            val supervisor = ChatSessionSupervisor(
                backoff = backoff,
                connectSession = { connectAndHoldOnce() },
                onReconnected = { syncOfflineMessages() },
                onAuthFailure = { recoverFromAuthFailure() },
                onKickedByNewerLogin = { notifyKickedByNewerLogin() }
            )
            activeSupervisor = supervisor
            supervisor.run()
        }
    }

    /**
     * 建立一次聊天 WS 会话并挂起直到会话结束。
     */
    private suspend fun connectAndHoldOnce(): WsSessionOutcome {
        val userId = tokenStore.userId.firstOrNull()
        if (userId == null || userId <= 0L) {
            Log.w(tag, "skip chat websocket: missing user id, stop reconnect loop")
            activeSupervisor?.stop()
            return WsSessionOutcome.NETWORK_FAILURE
        }
        connectedUserId = userId

        val url = runCatching { endpointResolver.resolveChatWebSocketUrl(userId) }
            .getOrElse { error ->
                errorHandler.emit(AppError.Unknown(error.message ?: "无法构建聊天连接地址"))
                return WsSessionOutcome.NETWORK_FAILURE
            }

        // t3 握手协议：/ws/{sid} 校验 JWT 且 userId==sid，token 走 Authorization 头
        val accessToken = runCatching { tokenStore.getAccessToken() }.getOrNull()

        val request = Request.Builder()
            .url(url)
            .apply {
                if (accessToken != null) {
                    header("Authorization", "Bearer $accessToken")
                }
            }
            .build()

        val opened = AtomicBoolean(false)
        val sessionEnded = CompletableDeferred<Unit>()
        var failureOutcome = WsSessionOutcome.NETWORK_FAILURE

        val socket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.set(true)
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
                // QA-D7：close 4001 = 被新登录顶掉（t35 契约/Q12=A 单设备在线）→ 单独分类，停止自动重连
                failureOutcome = if (code == CLOSE_CODE_REPLACED_BY_NEWER_LOGIN) {
                    WsSessionOutcome.KICKED_BY_NEWER_LOGIN
                } else {
                    WsSessionOutcome.CONNECTED
                }
                sessionEnded.complete(Unit)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                activeSocket.compareAndSet(webSocket, null)
                val httpCode = response?.code
                Log.w(
                    tag,
                    "chat websocket failed: ${t.message}${if (httpCode != null) " (HTTP $httpCode)" else ""}"
                )
                // WS 握手层无 401：后端鉴权失败统一 403 → 归鉴权类，交给 supervisor 做恢复/长退避
                failureOutcome = when (httpCode) {
                    403 -> WsSessionOutcome.AUTH_FAILURE
                    else -> WsSessionOutcome.NETWORK_FAILURE
                }
                if (failureOutcome == WsSessionOutcome.NETWORK_FAILURE) {
                    errorHandler.emit(AppError.Network("聊天实时连接断开，将自动重连"))
                }
                sessionEnded.complete(Unit)
            }
        })
        activeSocket.set(socket)

        sessionEnded.await()
        return when {
            // 顶号信号优先于"已打开过"判定（被顶的会话一定先建立成功）
            failureOutcome == WsSessionOutcome.KICKED_BY_NEWER_LOGIN -> failureOutcome
            opened.get() -> WsSessionOutcome.CONNECTED
            else -> failureOutcome
        }
    }

    /**
     * 被顶提示（QA-D7）：停止自动重连后告知用户原因；
     * 重新进入消息页时 ChatViewModel init → ensureConnected 可手动重连。
     */
    private suspend fun notifyKickedByNewerLogin() {
        Log.w(tag, "chat websocket kicked by newer login, stop auto reconnect")
        errorHandler.emit(AppError.Network("账号已在其他设备登录"))
    }

    /**
     * 鉴权失败（握手 403）的恢复动作：本地 exp 预检——
     * token 已过期 → refresh 一次（成功返回 true，supervisor 立即重试）；
     * token 未过期仍被拒（角色/绑定类）→ 刷新无意义，提示重新登录并返回 false（长退避）。
     */
    private suspend fun recoverFromAuthFailure(): Boolean {
        val token = tokenStore.getAccessToken()
        return if (JwtUtils.isJwtExpired(token)) {
            val refreshed = accessTokenProvider.refreshToken()
            errorHandler.emit(
                if (refreshed) {
                    AppError.Network("聊天通道登录凭证已过期，已自动刷新并重连")
                } else {
                    AppError.Network("聊天通道鉴权失败且刷新失败，请重新登录")
                }
            )
            refreshed
        } else {
            errorHandler.emit(AppError.Network("聊天通道鉴权被拒绝（HTTP 403），请重新登录后再试"))
            false
        }
    }

    /**
     * 重连成功后的离线补偿：拉取 sync 未读消息并推入实时流。
     * 失败只记日志，不影响重连循环（下次重连会再次尝试）。
     */
    private suspend fun syncOfflineMessages() {
        runCatching { apiService.syncMessages() }
            .onSuccess { response ->
                val messages = response.body()?.data.orEmpty()
                    .map { it.toMessageVO() }
                    .filter { it.id.isNotBlank() }
                messages.forEach { _incomingMessages.tryEmit(it) }
                Log.i(tag, "chat offline sync pulled ${messages.size} messages")
            }
            .onFailure { error ->
                Log.w(tag, "chat offline sync failed: ${error.message}")
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
        activeSupervisor?.stop()
        connectionJob?.cancel()
        connectionJob = null
        connectedUserId = null
        activeSocket.getAndSet(null)?.let { socket ->
            socket.close(1000, "chat realtime disconnect")
            socket.cancel()
        }
    }
}
