package com.fuwaki.djifly.platform.chat

import com.fuwaki.djifly.platform.ws.WsReconnectBackoff
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

/** 一次 WS 会话的结束方式（分类依据：握手 HTTP 403 = 鉴权类，close 4001 = 顶号，其余按网络类处理） */
enum class WsSessionOutcome {
    /** 会话建立过（无论正常关闭、被顶掉还是 1000 关闭），可安全常规重试 */
    CONNECTED,

    /** 网络/未知失败 → 常规退避重试 */
    NETWORK_FAILURE,

    /** 鉴权失败（WS 握手 HTTP 403，WS 层无 401）→ 先做恢复动作（如过期 refresh）再重试 */
    AUTH_FAILURE,

    /** 被新登录顶掉（close 4001 "replaced by newer login"，Q12=A 单设备在线）→ 停止自动重连并提示用户 */
    KICKED_BY_NEWER_LOGIN
}

/**
 * 聊天 WS 会话监督循环（纯 Kotlin，可 JVM 单测，不依赖 Android/OkHttp）。
 *
 * 节奏与遥测 WS 既有重连模式对齐（[WsReconnectBackoff]：3s→10s→30s，成功后回到 1）：
 * - 每轮调用 [connectSession]（挂起直到会话结束），返回 [WsSessionOutcome]；
 * - [WsSessionOutcome.CONNECTED]：首次成功只建基线；之后断开再重连成功即回调 [onReconnected]
 *   （调用方在此补拉离线消息 sync）——被新登录顶掉（close 1000）也归此类，按常规节奏重试；
 * - [WsSessionOutcome.AUTH_FAILURE]（WS 握手 403）：先调 [onAuthFailure]（调用方本地 exp
 *   预检并按需 refresh，返回 true 表示已恢复）→ 恢复成功且未超 [maxConsecutiveAuthRecoveries]
 *   次则立即重试；否则按 [authBackoffMillis]（5 分钟级）长退避；
 * - [WsSessionOutcome.KICKED_BY_NEWER_LOGIN]（close 4001 "replaced by newer login"，
 *   Q12=A 单设备在线）：调 [onKickedByNewerLogin] 提示用户后**停止自动重连**，
 *   消除双设备互踢循环（QA-D7）；重新进入消息页可经 ensureConnected 手动重连；
 * - [stop] 置位后（用户登出/用户缺失/手动断开）不再重试，run() 正常返回。
 *
 * 注意：[connectSession] 内部应自行 runCatching 兜底（普通异常按「会话失败」处理，
 * 返回 NETWORK_FAILURE；CancellationException 会原样抛出以响应协程取消）。
 */
class ChatSessionSupervisor(
    private val backoff: WsReconnectBackoff = WsReconnectBackoff(),
    private val authBackoffMillis: Long = AUTH_BACKOFF_MILLIS,
    private val maxConsecutiveAuthRecoveries: Int = MAX_CONSECUTIVE_AUTH_RECOVERIES,
    private val connectSession: suspend () -> WsSessionOutcome,
    private val onReconnected: suspend () -> Unit = {},
    private val onAuthFailure: suspend () -> Boolean = { false },
    private val onKickedByNewerLogin: suspend () -> Unit = {}
) {

    private val stopped = AtomicBoolean(false)

    /** 请求停止重连循环（当前会话结束后生效） */
    fun stop() {
        stopped.set(true)
    }

    suspend fun run() {
        var reconnectAttempt = backoff.initialAttempt()
        var everConnected = false
        var consecutiveAuthRecoveries = 0

        while (!stopped.get()) {
            val outcome = connectSession()

            if (outcome == WsSessionOutcome.CONNECTED) {
                if (everConnected) {
                    onReconnected()
                }
                everConnected = true
                reconnectAttempt = backoff.initialAttempt()
                consecutiveAuthRecoveries = 0
            }

            if (stopped.get()) break

            when (outcome) {
                WsSessionOutcome.KICKED_BY_NEWER_LOGIN -> {
                    // QA-D7：被新登录顶掉（close 4001）→ 停止自动重连，避免双设备互踢循环；
                    // 用户重新进入消息页时可经 ensureConnected 手动重连
                    onKickedByNewerLogin()
                    return
                }

                WsSessionOutcome.AUTH_FAILURE -> {
                    val recovered = onAuthFailure()
                    if (recovered &&
                        consecutiveAuthRecoveries < maxConsecutiveAuthRecoveries &&
                        !stopped.get()
                    ) {
                        // 已用新 token，立即重试；上限防止"刷新成功但服务端仍拒"的刷新空转
                        consecutiveAuthRecoveries++
                        continue
                    }
                    delay(authBackoffMillis)
                }

                else -> {
                    delay(backoff.delayMillisForAttempt(reconnectAttempt))
                    reconnectAttempt += 1
                }
            }
        }
    }

    companion object {
        /** 鉴权类失败的长退避（5 分钟级），区别于网络断连的 3s/10s/30s */
        const val AUTH_BACKOFF_MILLIS = 300_000L

        /** 连续"refresh 成功仍被拒"的最大立即重试次数 */
        const val MAX_CONSECUTIVE_AUTH_RECOVERIES = 2
    }
}
