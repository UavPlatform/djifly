package com.fuwaki.djifly.platform.ws.command.handler

import com.fuwaki.djifly.platform.ws.command.callback.WsCommandCallbackResult
import com.fuwaki.djifly.platform.ws.command.callback.WsCommandCallbacks
import com.fuwaki.djifly.platform.ws.protocol.PlatformWsEnvelope
import com.fuwaki.djifly.platform.ws.protocol.PlatformWsName
import com.fuwaki.djifly.platform.ws.protocol.PlatformWsType
import com.fuwaki.djifly.platform.ws.transport.WsMessageSender
import com.fuwaki.djifly.platform.ws.protocol.PlatformWsInboundEnvelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * STOP_LIVE 命令处理纯单元测试（t12 协议）。
 * 覆盖验收标准「收到 STOP_LIVE 停止推流并回执」的回执编排：
 * 同步 ack（response/replyTo）+ 异步 LIVE_STOPPED 事件（ACK 超时兜底）。
 */
class StopLiveHandlerTest {

    private class FakeCallbacks(var result: WsCommandCallbackResult) : WsCommandCallbacks {
        var startCalls = 0
        var stopCalls = 0

        override suspend fun onStartLive(payload: com.fuwaki.djifly.platform.ws.protocol.PlatformStartLivePayload): WsCommandCallbackResult {
            startCalls++
            return WsCommandCallbackResult.Success("room")
        }

        override suspend fun onStopLive(): WsCommandCallbackResult {
            stopCalls++
            return result
        }
    }

    private class RecordingSender : WsMessageSender {
        val sent = mutableListOf<Any>()
        override fun send(message: Any): Boolean {
            sent.add(message)
            return true
        }
    }

    private fun inbound(id: String?) = PlatformWsInboundEnvelope(
        rawText = "{}",
        id = id,
        type = PlatformWsType.COMMAND,
        name = PlatformWsName.STOP_LIVE
    )

    private fun envelopes(sent: List<Any>) = sent.map { it as PlatformWsEnvelope<*> }

    @Test
    fun stop_success_sends_ack_then_live_stopped_event() {
        val callbacks = FakeCallbacks(WsCommandCallbackResult.Stopped("已停止推流并退房"))
        val sender = RecordingSender()

        runBlocking { StopLiveHandler(callbacks).handle(inbound(id = "req-42"), deviceId = "SN-001", sender = sender) }

        assertEquals(1, callbacks.stopCalls)
        assertEquals(2, sender.sent.size)

        val ack = envelopes(sender.sent)[0]
        assertEquals(PlatformWsType.RESPONSE, ack.type)
        assertEquals(PlatformWsName.STOP_LIVE, ack.name)
        assertEquals("req-42", ack.replyTo)
        assertEquals("SN-001", ack.deviceId)
        assertEquals(true, ack.success)

        val event = envelopes(sender.sent)[1]
        assertEquals(PlatformWsType.EVENT, event.type)
        assertEquals(PlatformWsName.LIVE_STOPPED, event.name)
        assertEquals("SN-001", event.deviceId)
    }

    @Test
    fun idempotent_stop_when_not_streaming_still_acks_success_and_reports_event() {
        // 本就未在推流 → Stopped（幂等成功）→ ack success=true + LIVE_STOPPED，后端安全进入终态
        val callbacks = FakeCallbacks(WsCommandCallbackResult.Stopped("直播未在运行"))
        val sender = RecordingSender()

        runBlocking { StopLiveHandler(callbacks).handle(inbound(id = "req-43"), deviceId = "SN-001", sender = sender) }

        val ack = envelopes(sender.sent)[0]
        assertEquals(true, ack.success)
        val event = envelopes(sender.sent)[1]
        assertEquals(PlatformWsName.LIVE_STOPPED, event.name)
    }

    @Test
    fun stop_failure_acks_failure_without_live_stopped_event() {
        // 停流被拒 → ack success=false（后端 409 LIVE_STOP_REJECTED），不发事件（推流未停）
        val callbacks = FakeCallbacks(WsCommandCallbackResult.Error("LIVE_STOP_REJECTED", "停止推流失败"))
        val sender = RecordingSender()

        runBlocking { StopLiveHandler(callbacks).handle(inbound(id = "req-44"), deviceId = "SN-001", sender = sender) }

        assertEquals(1, sender.sent.size)
        val ack = envelopes(sender.sent)[0]
        assertEquals(false, ack.success)
        assertEquals("LIVE_STOP_REJECTED", ack.code)
        assertEquals("req-44", ack.replyTo)
    }

    @Test
    fun missing_command_id_still_yields_replyable_ack() {
        val callbacks = FakeCallbacks(WsCommandCallbackResult.Stopped("已停止推流并退房"))
        val sender = RecordingSender()

        runBlocking { StopLiveHandler(callbacks).handle(inbound(id = null), deviceId = "SN-001", sender = sender) }

        val ack = envelopes(sender.sent)[0]
        assertTrue("缺 id 时应自生成 requestId", !ack.replyTo.isNullOrBlank())
        assertEquals(true, ack.success)
    }
}
