package com.fuwaki.djifly.ui.screen

import com.fuwaki.djifly.data.models.ChatEnvelope
import com.fuwaki.djifly.data.models.MessageVO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 系统消息载荷解析与事件映射纯单元测试（1B-8 收敛包，t23 载荷契约）。
 * 覆盖验收标准①④：六类事件图标/标题映射、未知事件回退、系统消息判定、
 * ChatEnvelope 载荷 → MessageVO 的 payloadName/text 解析。
 */
class SystemMessageMetaTest {

    @Test
    fun six_events_map_to_app_aligned_titles() {
        // 与 app 侧 SystemMessageCard 的 _eventMeta 一一对应
        val expected = mapOf(
            "ORDER_PAID" to "支付成功",
            "TASK_ACCEPTED" to "任务已被接单",
            "TASK_CANCELLED" to "接单已取消",
            "TASK_COMPLETED" to "任务已完成",
            "ORDER_WAITING_CONFIRM" to "订单待验收",
            "ORDER_CONFIRMED" to "订单已确认完成"
        )
        for ((name, title) in expected) {
            val meta = SystemMessageMetaMapper.from(name, msgType = 2)
            assertEquals(name, title, meta.title)
        }
    }

    @Test
    fun unknown_event_falls_back_by_msg_type() {
        assertEquals("订单动态", SystemMessageMetaMapper.from("SOMETHING_ELSE", msgType = 2).title)
        assertEquals("系统通知", SystemMessageMetaMapper.from("SOMETHING_ELSE", msgType = 1).title)
        // payload.name 缺失同样回退
        assertEquals("订单动态", SystemMessageMetaMapper.from(null, msgType = 2).title)
        assertEquals("系统通知", SystemMessageMetaMapper.from(null, msgType = 1).title)
    }

    @Test
    fun system_message_detection_by_msg_type_and_sender() {
        assertTrue(SystemMessageMetaMapper.isSystemMessage(MessageVO(msgType = 1)))
        assertTrue(SystemMessageMetaMapper.isSystemMessage(MessageVO(msgType = 2)))
        // fromUserId=0 视为系统消息（t23：fromUserId=0 表示系统）
        assertTrue(SystemMessageMetaMapper.isSystemMessage(MessageVO(msgType = 0, fromUserId = 0L)))
        // 普通聊天消息不是系统消息
        assertFalse(SystemMessageMetaMapper.isSystemMessage(MessageVO(msgType = 0, fromUserId = 7L)))
    }

    @Test
    fun system_envelope_detection_handles_string_and_numeric_msg_type() {
        assertTrue(SystemMessageMetaMapper.isSystemEnvelope(ChatEnvelope(msgType = 1)))
        assertTrue(SystemMessageMetaMapper.isSystemEnvelope(ChatEnvelope(msgType = "ORDER")))
        assertTrue(SystemMessageMetaMapper.isSystemEnvelope(ChatEnvelope(msgType = "NOTICE")))
        assertTrue(SystemMessageMetaMapper.isSystemEnvelope(ChatEnvelope(msgType = 0, fromUserId = 0L)))
        assertFalse(SystemMessageMetaMapper.isSystemEnvelope(ChatEnvelope(msgType = "CHAT", fromUserId = 7L)))
    }

    @Test
    fun envelope_payload_name_and_text_are_parsed_into_message() {
        // t23 载荷：payload{type,name,data,text}
        val envelope = ChatEnvelope(
            msgId = "m-1",
            sessionId = 9L,
            msgType = 2,
            fromUserId = 0L,
            payload = mapOf(
                "type" to "ORDER",
                "name" to "ORDER_CONFIRMED",
                "text" to "订单已确认完成"
            ),
            isOffline = true
        )
        val message = envelope.toMessageVO()

        assertEquals("m-1", message.id)
        assertEquals(2, message.msgType)
        assertEquals("ORDER_CONFIRMED", message.payloadName)
        assertEquals("订单已确认完成", message.content)
        assertTrue(SystemMessageMetaMapper.isSystemMessage(message))
    }

    @Test
    fun chat_envelope_without_payload_name_maps_to_null() {
        val message = ChatEnvelope(
            msgId = "m-2",
            msgType = 0,
            fromUserId = 7L,
            payload = mapOf("text" to "你好")
        ).toMessageVO()

        assertNull(message.payloadName)
        assertEquals("你好", message.content)
        assertFalse(SystemMessageMetaMapper.isSystemMessage(message))
    }
}
