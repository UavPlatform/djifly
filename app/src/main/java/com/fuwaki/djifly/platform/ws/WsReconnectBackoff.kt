package com.fuwaki.djifly.platform.ws

/**
 * WS 断线重连退避节奏（纯 Kotlin，可 JVM 单测）。
 *
 * 遥测会话（PlatformConnectionSessionService）与聊天会话（ChatRealtimeClient/
 * ChatSessionSupervisor）共用同一节奏，保证两条实时通道行为一致：
 * 3s → 10s → 30s（封顶）。连接成功后由调用方把 attempt 重置回 [initialAttempt]。
 */
class WsReconnectBackoff(private val delaysMs: List<Long> = DEFAULT_DELAYS_MS) {

    init {
        require(delaysMs.isNotEmpty()) { "delaysMs 不能为空" }
    }

    /** 第 attempt 次重试前的等待时长；attempt 从 1 开始，越界封顶在最后一档 */
    fun delayMillisForAttempt(attempt: Int): Long {
        val index = (attempt - 1).coerceIn(0, delaysMs.lastIndex)
        return delaysMs[index]
    }

    fun initialAttempt(): Int = 1

    companion object {
        /** 既有遥测会话重连节奏（survey §4.2），两条通道统一沿用 */
        val DEFAULT_DELAYS_MS = listOf(3_000L, 10_000L, 30_000L)
    }
}
