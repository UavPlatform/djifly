package com.fuwaki.djifly.platform.ws

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * WS 重连退避节奏纯单元测试。
 * 遥测会话与聊天会话共用同一节奏（3s/10s/30s），测试锁定该契约。
 */
class WsReconnectBackoffTest {

    @Test
    fun first_three_attempts_follow_3s_10s_30s() {
        val backoff = WsReconnectBackoff()

        assertEquals(3_000L, backoff.delayMillisForAttempt(1))
        assertEquals(10_000L, backoff.delayMillisForAttempt(2))
        assertEquals(30_000L, backoff.delayMillisForAttempt(3))
    }

    @Test
    fun caps_at_last_delay_for_later_attempts() {
        val backoff = WsReconnectBackoff()

        assertEquals(30_000L, backoff.delayMillisForAttempt(4))
        assertEquals(30_000L, backoff.delayMillisForAttempt(10))
        assertEquals(30_000L, backoff.delayMillisForAttempt(100))
    }

    @Test
    fun clamps_non_positive_attempts_to_first_delay() {
        val backoff = WsReconnectBackoff()

        assertEquals(3_000L, backoff.delayMillisForAttempt(0))
        assertEquals(3_000L, backoff.delayMillisForAttempt(-5))
    }

    @Test
    fun initial_attempt_is_one() {
        assertEquals(1, WsReconnectBackoff().initialAttempt())
    }

    @Test
    fun supports_custom_delays_for_fast_tests() {
        val backoff = WsReconnectBackoff(listOf(1L, 2L))

        assertEquals(1L, backoff.delayMillisForAttempt(1))
        assertEquals(2L, backoff.delayMillisForAttempt(2))
        assertEquals(2L, backoff.delayMillisForAttempt(3))
    }

    @Test
    fun default_delays_match_the_drone_session_pattern() {
        // 与 survey §4.2 记录的遥测重连节奏保持一致
        assertEquals(listOf(3_000L, 10_000L, 30_000L), WsReconnectBackoff.DEFAULT_DELAYS_MS)
    }
}
