@file:OptIn(ExperimentalStage1Api::class)

package com.fuwaki.djifly.platform.eventstage1

import com.fuwaki.djifly.platform.eventstream.AircraftIdentity
import com.fuwaki.djifly.platform.eventstream.EventDraft
import com.fuwaki.djifly.platform.eventstream.EventInput
import com.fuwaki.djifly.platform.eventstream.EventRateClass
import com.fuwaki.djifly.platform.eventstream.EventStreamContext
import com.fuwaki.djifly.platform.eventstream.RecordingOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventStreamPacketEncoderTest {
    private val context = EventStreamContext(
        operation = RecordingOperation(
            7L,
            "11111111-1111-4111-8111-111111111111",
            "22222222-2222-4222-8222-222222222222"
        ),
        chainId = "33333333-3333-4333-8333-333333333333",
        aircraft = AircraftIdentity("SN-001", "DJI_FC", "M30"),
        recordingGeneration = 1L
    )

    @Test
    fun encodes_a_packet_that_the_strict_v2_codec_can_read() {
        val encoder = EventStreamPacketEncoder(packetIdFactory = { "44444444-4444-4444-8444-444444444444" })
        val packet = encoder.encode(context, listOf(
            draft("POSITION", 1L, mapOf("latitudeDegE7" to 123456789L, "longitudeDegE7" to -987654321L)),
            draft("ACTUAL_FLYING_STATE", 2L, mapOf("flying" to true), EventRateClass.STATE_OR_INTENT)
        ))

        val decoded = Stage1Codec().decode(packet.originalBytes(), context.operation.orderId)
        assertEquals(1L, decoded.link.height)
        assertEquals(listOf("66666666-6666-4666-8666-666666666666", "77777777-7777-4777-8777-777777777777"), decoded.eventIds)
        assertEquals("SN-001", decoded.link.identity.serialNumber)
        assertTrue(decoded.originalBytes().contentEquals(packet.originalBytes()))
    }

    @Test
    fun next_packet_keeps_the_same_identity_and_links_to_the_previous_packet() {
        var next = 0
        val encoder = EventStreamPacketEncoder(packetIdFactory = {
            if (next++ == 0) "44444444-4444-4444-8444-444444444444"
            else "55555555-5555-4555-8555-555555555555"
        })
        val first = encoder.encode(context, listOf(draft("BATTERY", 1L, mapOf("chargePermille" to 900L))))
        val second = encoder.encode(context, listOf(draft("ALTITUDE", 2L, mapOf("altitudeMm" to 1200L))), first.link)

        assertEquals(1L, first.link.height)
        assertEquals(2L, second.link.height)
        assertEquals(first.link.packetId, second.prevPacketId)
        assertEquals(first.link.identity, second.link.identity)
        assertNotEquals(first.link.chainHash, second.link.chainHash)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejects_drafts_from_a_different_context() {
        val other = context.copy(chainId = "66666666-6666-4666-8666-666666666666")
        EventStreamPacketEncoder().encode(context, listOf(draft("BATTERY", 1L, mapOf("chargePermille" to 900L), context = other)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejects_non_integer_payload_values_instead_of_guessing_a_unit_conversion() {
        EventStreamPacketEncoder().encode(context, listOf(draft("BATTERY", 1L, mapOf("chargePermille" to 90.5))))
    }

    private fun draft(
        type: String,
        at: Long,
        payload: Map<String, Any?>,
        rateClass: EventRateClass = EventRateClass.SENSOR,
        context: EventStreamContext = this.context
    ) = EventDraft(
        eventId = if (at == 1L) "66666666-6666-4666-8666-666666666666" else "77777777-7777-4777-8777-777777777777",
        type = type,
        capturedAt = at,
        payload = payload,
        context = context
    )
}
