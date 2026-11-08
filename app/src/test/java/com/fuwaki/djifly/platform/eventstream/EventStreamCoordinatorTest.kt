package com.fuwaki.djifly.platform.eventstream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Local capture preparation for REQ-PILOT-EXEC-007; no SDK or backend simulation. */
class EventStreamCoordinatorTest {
    private val aircraft = AircraftIdentity("SN-001", "DJI_SDK", "M30")
    private val operation = RecordingOperation(1L, "order-1", "source-1")

    @Test
    fun operation_and_aircraft_can_arrive_in_either_order() {
        for (aircraftFirst in listOf(true, false)) {
            val f = Fixture()
            if (aircraftFirst) {
                f.coordinator.aircraftConnected(aircraft)
                assertEquals(null, f.coordinator.currentContext)
                f.coordinator.beginOperation(operation)
            } else {
                f.coordinator.beginOperation(operation)
                assertEquals(null, f.coordinator.currentContext)
                f.coordinator.aircraftConnected(aircraft)
            }
            assertTrue(f.record(position()) is EventCaptureResult.Recorded)
            assertEquals(operation, f.persisted.single().context.operation)
            assertEquals(aircraft, f.persisted.single().context.aircraft)
        }
    }

    @Test
    fun missing_account_order_or_aircraft_does_not_capture() {
        val f = Fixture()
        f.coordinator.setPilot(null)
        assertEquals(ignored(IgnoreReason.NOT_LOGGED_IN), f.coordinator.record(listOf(position()), 1L))
        f.coordinator.setPilot(1L)
        assertEquals(ignored(IgnoreReason.NO_OPERATION), f.coordinator.record(listOf(position()), 1L))
        f.coordinator.beginOperation(operation)
        assertEquals(ignored(IgnoreReason.AIRCRAFT_NOT_READY), f.coordinator.record(listOf(position()), 1L))
        assertTrue(f.persisted.isEmpty())
    }

    @Test
    fun repeated_selections_do_not_restart_the_chain_or_reset_throttling() {
        val f = Fixture()
        val connection = f.start()
        val context = f.context
        f.record(position())
        f.coordinator.setPilot(1L)
        f.coordinator.beginOperation(operation)
        assertEquals(connection, f.coordinator.aircraftConnected(aircraft))
        assertSame(context, f.context)
        assertEquals(ignored(IgnoreReason.THROTTLED), f.record(position()))
    }

    @Test
    fun switching_orders_rejects_old_callbacks_but_keeps_physical_connection() {
        val f = Fixture()
        val connection = f.start()
        val old = f.context
        f.coordinator.beginOperation(operation.copy(orderId = "order-2"))
        assertEquals(connection, f.coordinator.aircraftConnected(aircraft))
        assertNotEquals(old.chainId, f.context.chainId)
        assertEquals(ignored(IgnoreReason.STALE_CALLBACK), f.coordinator.record(listOf(position()), old.recordingGeneration))
        f.record(position())
        assertEquals("order-2", f.persisted.single().context.operation.orderId)
    }

    @Test
    fun old_callbacks_and_disconnects_cannot_change_a_replacement_aircraft() {
        val f = Fixture()
        val oldConnection = f.start()
        val old = f.context
        val replacement = aircraft.copy(serialNumber = "SN-002")
        val nextConnection = f.coordinator.aircraftConnected(replacement)
        assertNotEquals(oldConnection, nextConnection)
        assertNotEquals(old.chainId, f.context.chainId)
        assertFalse(f.coordinator.aircraftDisconnected(oldConnection))
        assertEquals(ignored(IgnoreReason.STALE_CALLBACK), f.coordinator.record(listOf(position()), old.recordingGeneration))
        f.record(position())
        assertEquals(replacement, f.persisted.single().context.aircraft)
    }

    @Test
    fun reconnecting_the_same_aircraft_invalidates_old_callbacks() {
        val f = Fixture()
        val oldConnection = f.start()
        val old = f.context
        assertTrue(f.coordinator.aircraftDisconnected(oldConnection))
        assertEquals(ignored(IgnoreReason.AIRCRAFT_NOT_READY), f.coordinator.record(listOf(position()), old.recordingGeneration))
        val nextConnection = f.coordinator.aircraftConnected(aircraft)
        assertNotEquals(oldConnection, nextConnection)
        assertFalse(f.coordinator.aircraftDisconnected(oldConnection))
        assertEquals(ignored(IgnoreReason.STALE_CALLBACK), f.coordinator.record(listOf(position()), old.recordingGeneration))
        assertTrue(f.record(position()) is EventCaptureResult.Recorded)
    }

    @Test
    fun account_changes_preserve_saved_events_and_prevent_cross_account_attribution() {
        val f = Fixture()
        val connection = f.start()
        val old = f.context
        f.record(position())
        f.coordinator.setPilot(null)
        assertEquals(ignored(IgnoreReason.NOT_LOGGED_IN), f.coordinator.record(listOf(position()), old.recordingGeneration))
        f.coordinator.setPilot(2L)
        assertThrows(IllegalStateException::class.java) { f.coordinator.beginOperation(operation) }
        assertEquals(ignored(IgnoreReason.NO_OPERATION), f.coordinator.record(listOf(position()), old.recordingGeneration))
        f.coordinator.beginOperation(operation.copy(pilotId = 2L, orderId = "order-2"))
        assertEquals(connection, f.coordinator.aircraftConnected(aircraft))
        assertEquals(ignored(IgnoreReason.STALE_CALLBACK), f.coordinator.record(listOf(position()), old.recordingGeneration))
        f.record(position())
        assertEquals(listOf(1L, 2L), f.persisted.map { it.context.operation.pilotId })
        assertEquals(listOf("order-1", "order-2"), f.persisted.map { it.context.operation.orderId })
    }

    @Test
    fun ending_an_operation_keeps_connection_and_invalidates_callbacks_when_reselected() {
        val f = Fixture()
        val connection = f.start()
        val old = f.context
        f.coordinator.endOperation()
        assertEquals(ignored(IgnoreReason.NO_OPERATION), f.coordinator.record(listOf(position()), old.recordingGeneration))
        f.coordinator.beginOperation(operation)
        assertEquals(connection, f.coordinator.aircraftConnected(aircraft))
        assertNotEquals(old.chainId, f.context.chainId)
        assertEquals(ignored(IgnoreReason.STALE_CALLBACK), f.coordinator.record(listOf(position()), old.recordingGeneration))
    }

    @Test
    fun sensor_throttle_is_per_type_and_uses_elapsed_time_while_state_edges_are_unthrottled() {
        val f = Fixture()
        f.start()
        f.record(position(10_000L))
        f.elapsedMs = 1_999L
        assertEquals(ignored(IgnoreReason.THROTTLED), f.record(position(100_000L)))
        assertTrue(f.record(EventInput("BATTERY", 11_000L, mapOf("raw" to 80))) is EventCaptureResult.Recorded)
        f.elapsedMs = 2_000L
        assertTrue(f.record(position(1L)) is EventCaptureResult.Recorded)
        f.record(state(false), state(true))
        assertEquals(listOf(10_000L, 1L), f.persisted.filter { it.type == "POSITION" }.map { it.capturedAt })
        assertEquals(listOf(false, true), f.persisted.filter { it.type == "FLIGHT_STATE" }.map { it.payload["isFlying"] })
        assertEquals(5, f.persisted.size)
    }

    @Test
    fun persistence_failure_is_reported_and_does_not_advance_throttle() {
        val f = Fixture()
        f.start()
        f.failPersistence = true
        val error = assertThrows(IllegalStateException::class.java) { f.record(position(), state(true)) }
        assertEquals("disk full", error.message)
        assertTrue(f.persisted.isEmpty())
        f.failPersistence = false
        assertTrue(f.record(position(), state(true)) is EventCaptureResult.Recorded)
        assertEquals(2, f.persisted.size)
    }

    @Test
    fun malformed_batch_is_not_partially_saved_or_throttled() {
        val f = Fixture()
        f.start()
        assertThrows(IllegalArgumentException::class.java) {
            f.record(position(), EventInput("BATTERY", 1L, mapOf("raw" to Double.NaN)))
        }
        assertTrue(f.persisted.isEmpty())
        assertTrue(f.record(position()) is EventCaptureResult.Recorded)
        assertEquals(1, f.persisted.size)
    }

    @Test
    fun event_payload_and_batch_are_immutable_snapshots() {
        val f = Fixture()
        f.start()
        val nested = mutableListOf<Any?>(1L)
        val child = mutableMapOf<String, Any?>("samples" to nested)
        val input = mutableMapOf<String, Any?>("child" to child)
        val result = f.record(EventInput("CUSTOM", 1L, input)) as EventCaptureResult.Recorded
        nested += 2L
        child["later"] = true
        input.clear()
        val payload = f.persisted.single().payload
        assertEquals(mapOf("child" to mapOf("samples" to listOf(1L))), payload)
        assertThrows(UnsupportedOperationException::class.java) { (payload as MutableMap)["later"] = true }
        @Suppress("UNCHECKED_CAST")
        val savedChild = payload["child"] as MutableMap<String, Any?>
        assertThrows(UnsupportedOperationException::class.java) { savedChild.clear() }
        @Suppress("UNCHECKED_CAST")
        val samples = savedChild["samples"] as MutableList<Any?>
        assertThrows(UnsupportedOperationException::class.java) { samples.clear() }
        assertThrows(UnsupportedOperationException::class.java) { (result.events as MutableList).clear() }
    }

    private fun ignored(reason: IgnoreReason) = EventCaptureResult.Ignored(reason)

    // Illustrative local payload only; these do not define the v2 wire schema.
    private fun position(at: Long = 1_000L) = EventInput("POSITION", at, mapOf("raw" to 1L))
    private fun state(flying: Boolean) = EventInput(
        "FLIGHT_STATE", 1_000L, mapOf("isFlying" to flying), EventRateClass.STATE_OR_INTENT
    )

    private inner class Fixture {
        var elapsedMs = 0L
        var failPersistence = false
        val persisted = mutableListOf<EventDraft>()
        private var nextId = 0
        val coordinator = EventStreamCoordinator(
            sink = EventDraftSink { events ->
                check(!failPersistence) { "disk full" }
                persisted += events
            },
            eventIdFactory = { "event-" + ++nextId },
            chainIdFactory = { "chain-" + ++nextId },
            elapsedRealtimeMs = { elapsedMs }
        ).also { it.setPilot(1L) }

        val context: EventStreamContext get() = requireNotNull(coordinator.currentContext)

        fun start(): Long {
            coordinator.beginOperation(operation)
            return coordinator.aircraftConnected(aircraft)
        }

        fun record(vararg events: EventInput): EventCaptureResult =
            coordinator.record(events.toList(), context.recordingGeneration)
    }
}
