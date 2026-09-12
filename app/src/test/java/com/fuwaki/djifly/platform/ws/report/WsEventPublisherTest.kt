package com.fuwaki.djifly.platform.ws.report

import com.fuwaki.djifly.platform.ws.transport.WsMessageSender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WsEventPublisher（主动事件通道）纯单元测试。
 * 覆盖验收标准「退出飞行页停止推流并回执」的回执通道语义：
 * 连接建立 attach → 可发布；断开 detach → 不可发布；旧会话 detach 不误伤新会话。
 */
class WsEventPublisherTest {

    private class RecordingSender : WsMessageSender {
        val sent = mutableListOf<Any>()
        var accept = true
        override fun send(message: Any): Boolean {
            sent.add(message)
            return accept
        }
    }

    @Test
    fun publish_fails_when_not_attached() {
        val publisher = WsEventPublisher()

        assertFalse(publisher.isAttached)
        assertFalse(publisher.publish("envelope"))
        assertNull(publisher.currentDeviceId)
    }

    @Test
    fun publish_after_attach_delivers_to_current_sender() {
        val publisher = WsEventPublisher()
        val sender = RecordingSender()

        publisher.attach(sender, "SN-001")
        val delivered = publisher.publish("LIVE_STOPPED-envelope")

        assertTrue(delivered)
        assertTrue(publisher.isAttached)
        assertEquals("SN-001", publisher.currentDeviceId)
        assertEquals(listOf<Any>("LIVE_STOPPED-envelope"), sender.sent)
    }

    @Test
    fun publish_fails_after_detach() {
        val publisher = WsEventPublisher()
        val sender = RecordingSender()
        publisher.attach(sender, "SN-001")

        publisher.detach(sender)

        assertFalse(publisher.isAttached)
        assertFalse(publisher.publish("envelope"))
        assertNull(publisher.currentDeviceId)
    }

    @Test
    fun old_session_detach_does_not_clear_new_session() {
        // 会话重连时序：旧连接 finally detach 晚于新连接 onOpen attach → 不得误清新 sender
        val publisher = WsEventPublisher()
        val oldSender = RecordingSender()
        val newSender = RecordingSender()

        publisher.attach(oldSender, "SN-001")
        publisher.attach(newSender, "SN-001")   // 新会话已建立（覆盖挂载）
        publisher.detach(oldSender)             // 旧会话销毁时才 detach

        assertTrue(publisher.isAttached)
        assertEquals("SN-001", publisher.currentDeviceId)
        publisher.publish("envelope")
        assertEquals(0, oldSender.sent.size)
        assertEquals(listOf<Any>("envelope"), newSender.sent)
    }

    @Test
    fun publisher_propagates_sender_rejection() {
        val publisher = WsEventPublisher()
        val sender = RecordingSender().apply { accept = false }
        publisher.attach(sender, "SN-001")

        assertFalse(publisher.publish("envelope"))
    }
}
