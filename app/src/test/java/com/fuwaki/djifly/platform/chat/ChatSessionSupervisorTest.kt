package com.fuwaki.djifly.platform.chat

import com.fuwaki.djifly.platform.ws.WsReconnectBackoff
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 聊天 WS 会话监督循环纯单元测试（不依赖 Android/OkHttp）。
 *
 * 覆盖验收标准「断网恢复聊天 WS 自动重连且补拉消息」的编排语义与 t16 新增的鉴权分类：
 * - 首次连接成功不补拉；断开后按退避自动重连；重连成功补拉一次 sync；
 * - 被新登录顶掉（close 1000 → CONNECTED）与网络断连同节奏重试；
 * - 鉴权失败（握手 403）：本地恢复成功 → 立即重试（上限 2 次）；否则 5 分钟级长退避；
 * - stop()（登出/用户缺失）后不再重试；连接成功后退避档位重置。
 */
class ChatSessionSupervisorTest {

    @Test
    fun reconnect_success_triggers_exactly_one_sync_after_first_connection() {
        val supervisorHolder = arrayOfNulls<ChatSessionSupervisor>(1)
        var connectCalls = 0
        var syncCalls = 0

        val supervisor = ChatSessionSupervisor(
            backoff = WsReconnectBackoff(listOf(1L, 1L, 1L)),
            connectSession = {
                connectCalls += 1
                when (connectCalls) {
                    1 -> WsSessionOutcome.NETWORK_FAILURE          // 首次建连失败
                    2 -> WsSessionOutcome.CONNECTED                // 首次连接成功（不补拉）
                    3 -> {                                          // 断开重连成功 → 补拉
                        supervisorHolder[0]!!.stop()
                        WsSessionOutcome.CONNECTED
                    }
                    else -> WsSessionOutcome.CONNECTED
                }
            },
            onReconnected = { syncCalls += 1 }
        )
        supervisorHolder[0] = supervisor

        runBlocking { supervisor.run() }

        assertEquals(3, connectCalls)
        assertEquals(1, syncCalls)
    }

    @Test
    fun kicked_by_new_login_close_1000_retries_with_normal_rhythm() {
        // close 1000（被顶掉）归 CONNECTED：与网络断连同节奏快重试（dev-backend 修正版分类）。
        // 每次 connectSession = 一个完整会话周期（建连→挂起→结束）：第一轮会话被顶掉结束，
        // 第二轮重连成功 → 补拉。
        val supervisorHolder = arrayOfNulls<ChatSessionSupervisor>(1)
        var connectCalls = 0
        var syncCalls = 0

        val supervisor = ChatSessionSupervisor(
            backoff = WsReconnectBackoff(listOf(1L)),
            connectSession = {
                connectCalls += 1
                when (connectCalls) {
                    1 -> WsSessionOutcome.CONNECTED                 // 首次会话成功后被顶掉（1000）
                    2 -> {                                          // 重连成功 → 补拉
                        supervisorHolder[0]!!.stop()
                        WsSessionOutcome.CONNECTED
                    }
                    else -> WsSessionOutcome.CONNECTED
                }
            },
            onReconnected = { syncCalls += 1 }
        )
        supervisorHolder[0] = supervisor

        runBlocking { supervisor.run() }

        assertEquals(2, connectCalls)
        assertEquals(1, syncCalls)
    }

    @Test
    fun auth_failure_recovers_immediately_up_to_cap_then_long_backoff() {
        // 403 + 本地 exp 已过期 → refresh 成功立即重试；连续恢复达上限 2 次后强制长退避
        val supervisorHolder = arrayOfNulls<ChatSessionSupervisor>(1)
        var connectCalls = 0
        var authCalls = 0

        val supervisor = ChatSessionSupervisor(
            backoff = WsReconnectBackoff(listOf(1L)),
            authBackoffMillis = 1L,
            maxConsecutiveAuthRecoveries = 2,
            connectSession = {
                connectCalls += 1
                when (connectCalls) {
                    1 -> WsSessionOutcome.AUTH_FAILURE
                    2 -> WsSessionOutcome.AUTH_FAILURE
                    3 -> WsSessionOutcome.AUTH_FAILURE   // 恢复已达上限 → 长退避后再试
                    4 -> {
                        supervisorHolder[0]!!.stop()
                        WsSessionOutcome.CONNECTED
                    }
                    else -> WsSessionOutcome.CONNECTED
                }
            },
            onReconnected = { },
            onAuthFailure = {
                authCalls += 1
                true   // 每次都"恢复成功"
            }
        )
        supervisorHolder[0] = supervisor

        runBlocking { supervisor.run() }

        // AUTH×3（前两次立即重试，第三次因上限走长退避）→ CONNECTED+stop
        assertEquals(4, connectCalls)
        assertEquals(3, authCalls)
    }

    @Test
    fun auth_failure_without_recovery_uses_long_backoff() {
        // 403 + token 未过期/刷新失败 → 不立即重试，直接长退避（本轮仅 1 次 auth 回调）
        val supervisorHolder = arrayOfNulls<ChatSessionSupervisor>(1)
        var connectCalls = 0
        var authCalls = 0

        val supervisor = ChatSessionSupervisor(
            backoff = WsReconnectBackoff(listOf(1L)),
            authBackoffMillis = 1L,
            connectSession = {
                connectCalls += 1
                when (connectCalls) {
                    1 -> WsSessionOutcome.AUTH_FAILURE
                    2 -> {
                        supervisorHolder[0]!!.stop()
                        WsSessionOutcome.CONNECTED
                    }
                    else -> WsSessionOutcome.CONNECTED
                }
            },
            onAuthFailure = {
                authCalls += 1
                false   // 无法恢复 → 长退避
            }
        )
        supervisorHolder[0] = supervisor

        runBlocking { supervisor.run() }

        assertEquals(2, connectCalls)
        assertEquals(1, authCalls)
    }

    @Test
    fun failure_streak_keeps_retrying_then_resets_on_success() {
        val supervisorHolder = arrayOfNulls<ChatSessionSupervisor>(1)
        var connectCalls = 0
        val outcomes = mutableListOf<WsSessionOutcome>()

        val supervisor = ChatSessionSupervisor(
            backoff = WsReconnectBackoff(listOf(1L, 1L, 1L)),
            connectSession = {
                connectCalls += 1
                val outcome = when (connectCalls) {
                    1 -> WsSessionOutcome.NETWORK_FAILURE
                    2 -> WsSessionOutcome.NETWORK_FAILURE
                    3 -> WsSessionOutcome.CONNECTED       // 连续失败两次后成功
                    4 -> {
                        supervisorHolder[0]!!.stop()
                        WsSessionOutcome.NETWORK_FAILURE
                    }
                    else -> WsSessionOutcome.NETWORK_FAILURE
                }
                outcomes.add(outcome)
                outcome
            },
            onReconnected = { }
        )
        supervisorHolder[0] = supervisor

        runBlocking { supervisor.run() }

        assertEquals(4, connectCalls)
        assertEquals(
            listOf(
                WsSessionOutcome.NETWORK_FAILURE,
                WsSessionOutcome.NETWORK_FAILURE,
                WsSessionOutcome.CONNECTED,
                WsSessionOutcome.NETWORK_FAILURE
            ),
            outcomes
        )
    }

    @Test
    fun stop_during_first_session_prevents_any_reconnect_and_sync() {
        val supervisorHolder = arrayOfNulls<ChatSessionSupervisor>(1)
        var connectCalls = 0
        var syncCalls = 0

        val supervisor = ChatSessionSupervisor(
            backoff = WsReconnectBackoff(listOf(1L)),
            connectSession = {
                connectCalls += 1
                supervisorHolder[0]!!.stop()   // 会话进行中被外部停止（如登出）
                WsSessionOutcome.CONNECTED
            },
            onReconnected = { syncCalls += 1 }
        )
        supervisorHolder[0] = supervisor

        runBlocking { supervisor.run() }

        assertEquals(1, connectCalls)
        assertEquals(0, syncCalls)
    }

    @Test
    fun repeated_reconnects_sync_each_time() {
        val supervisorHolder = arrayOfNulls<ChatSessionSupervisor>(1)
        var connectCalls = 0
        var syncCalls = 0

        val supervisor = ChatSessionSupervisor(
            backoff = WsReconnectBackoff(listOf(1L)),
            connectSession = {
                connectCalls += 1
                // 成功→失败→成功(补拉)→失败→成功(再补拉)→停止
                val shouldStopAfter = connectCalls == 5
                val outcome =
                    if (connectCalls == 2 || connectCalls == 4) WsSessionOutcome.NETWORK_FAILURE
                    else WsSessionOutcome.CONNECTED
                if (shouldStopAfter) supervisorHolder[0]!!.stop()
                outcome
            },
            onReconnected = { syncCalls += 1 }
        )
        supervisorHolder[0] = supervisor

        runBlocking { supervisor.run() }

        assertEquals(5, connectCalls)
        assertEquals(2, syncCalls)
    }

    // ── QA-D7：close 4001 顶号分支 ──

    @Test
    fun kicked_by_newer_login_stops_auto_reconnect_and_notifies() {
        // 会话成功后被 4001 顶掉 → 通知一次并停止自动重连（不进入退避，不再 connectSession）
        var connectCalls = 0
        var syncCalls = 0
        var kickedCalls = 0

        val supervisor = ChatSessionSupervisor(
            backoff = WsReconnectBackoff(listOf(1L)),
            connectSession = {
                connectCalls += 1
                if (connectCalls == 1) WsSessionOutcome.CONNECTED
                else WsSessionOutcome.KICKED_BY_NEWER_LOGIN
            },
            onReconnected = { syncCalls += 1 },
            onKickedByNewerLogin = { kickedCalls += 1 }
        )

        runBlocking { supervisor.run() }

        assertEquals(2, connectCalls)
        assertEquals(1, kickedCalls)
        assertEquals(0, syncCalls)
    }

    @Test
    fun kicked_without_prior_connection_also_stops_and_notifies() {
        var connectCalls = 0
        var kickedCalls = 0

        val supervisor = ChatSessionSupervisor(
            backoff = WsReconnectBackoff(listOf(1L)),
            connectSession = {
                connectCalls += 1
                WsSessionOutcome.KICKED_BY_NEWER_LOGIN
            },
            onKickedByNewerLogin = { kickedCalls += 1 }
        )

        runBlocking { supervisor.run() }

        assertEquals(1, connectCalls)
        assertEquals(1, kickedCalls)
    }

    @Test
    fun kicked_outcome_does_not_trigger_sync() {
        // 全程只有 KICKED：无重连成功 → 不触发补拉（KICKED 与 CONNECTED 的关键区别）
        var connectCalls = 0
        var syncCalls = 0
        var kickedCalls = 0

        val supervisor = ChatSessionSupervisor(
            backoff = WsReconnectBackoff(listOf(1L)),
            connectSession = {
                connectCalls += 1
                WsSessionOutcome.KICKED_BY_NEWER_LOGIN
            },
            onReconnected = { syncCalls += 1 },
            onKickedByNewerLogin = { kickedCalls += 1 }
        )

        runBlocking { supervisor.run() }

        assertEquals(1, connectCalls)
        assertEquals(1, kickedCalls)
        assertEquals(0, syncCalls)
    }
}
