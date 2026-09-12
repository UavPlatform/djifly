package com.fuwaki.djifly.platform.ws.protocol

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 平台 WS 消息信封编解码纯单元测试（QA-D1 P0：platform/ws 消息信封解析）。
 *
 * codec 是入站消息的第一道入口（PlatformWsClient.onMessage → decode → WsMessageRouter），
 * 解析失败返回 null 而不是抛异常，是连接稳定性的关键行为。
 */
class PlatformWsMessageCodecTest {

    private val codec = PlatformWsMessageCodec(Gson())

    @Test
    fun decode_parses_command_envelope_with_payload_data() {
        val raw = """
            {"id":"msg-1","type":"command","name":"start_live","replyTo":"req-9",
             "deviceId":"SN-001","timestamp":1700000000123,
             "data":{"roomId":"room-1","userId":"user-1","userSig":"sig-abc"}}
        """.trimIndent()

        val envelope = codec.decode(raw)

        assertNotNull(envelope)
        envelope!!
        assertEquals("msg-1", envelope.id)
        assertEquals(PlatformWsType.COMMAND, envelope.type)
        assertEquals(PlatformWsName.START_LIVE, envelope.name)
        assertEquals("req-9", envelope.replyTo)
        assertEquals("SN-001", envelope.deviceId)
        assertEquals(1700000000123L, envelope.timestamp)
        assertNotNull(envelope.data)

        // data 载荷可反序列化为强类型 command payload
        val payload = envelope.parseData(Gson(), PlatformStartLivePayload::class.java)
        assertNotNull(payload)
        payload!!
        assertEquals("room-1", payload.roomId)
        assertEquals("user-1", payload.userId)
        assertEquals("sig-abc", payload.userSig)
    }

    @Test
    fun decode_maps_type_and_name_case_insensitively() {
        val envelope = codec.decode("""{"type":"event","name":"pong","timestamp":1}""")

        assertNotNull(envelope)
        envelope!!
        assertEquals(PlatformWsType.EVENT, envelope.type)
        assertEquals(PlatformWsName.PONG, envelope.name)
    }

    @Test
    fun decode_returns_null_for_malformed_or_empty_payload() {
        assertNull(codec.decode("not-json{{{"))
        assertNull(codec.decode(""))
        assertNull(codec.decode("   "))
        assertNull(codec.decode("[1,2,3]")) // 顶层非对象
    }

    @Test
    fun decode_tolerates_unknown_type_and_name_without_dropping_fields() {
        val envelope = codec.decode(
            """{"type":"magical","name":"nonsense","deviceId":"SN-001","timestamp":7}"""
        )

        // 未知枚举值不抛异常：保留原始串（规范化为大写），枚举位置为 null，交由路由层忽略
        assertNotNull(envelope)
        envelope!!
        assertNull(envelope.type)
        assertNull(envelope.name)
        assertEquals("MAGICAL", envelope.typeRaw)
        assertEquals("NONSENSE", envelope.nameRaw)
        assertEquals("SN-001", envelope.deviceId)
        assertEquals(7L, envelope.timestamp)
    }

    @Test
    fun encode_produces_wire_json_that_decodes_back() {
        val envelope = PlatformWsEnvelopeFactory.pong(
            replyTo = "req-1",
            deviceId = "SN-001",
            timestamp = 42L
        )

        val json = codec.encode(envelope)

        assertTrue(json.contains("\"type\":\"event\""))
        assertTrue(json.contains("\"name\":\"PONG\""))
        assertTrue(json.contains("\"deviceId\":\"SN-001\""))
        assertTrue(json.contains("\"timestamp\":42"))

        val decoded = codec.decode(json)
        assertNotNull(decoded)
        decoded!!
        // decode 侧 typeRaw/nameRaw 规范化为大写后存放
        assertEquals("EVENT", decoded.typeRaw)
        assertEquals("PONG", decoded.nameRaw)
        assertEquals("req-1", decoded.replyTo)
        assertEquals("SN-001", decoded.deviceId)
    }
}
