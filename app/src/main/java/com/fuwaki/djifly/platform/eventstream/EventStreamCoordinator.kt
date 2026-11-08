package com.fuwaki.djifly.platform.eventstream

import java.math.BigDecimal
import java.math.BigInteger
import java.util.Collections
import java.util.UUID

/** SDK supplied aircraft identity. It does not represent a backend registration or device lock. */
data class AircraftIdentity(
    val serialNumber: String,
    val serialSource: String,
    val modelCode: String
) {
    init {
        require(serialNumber.isNotBlank())
        require(serialSource.isNotBlank())
        require(modelCode.isNotBlank())
    }
}

/** The order whose events should be recorded on this device. */
data class RecordingOperation(val pilotId: Long, val orderId: String, val recordingSourceId: String) {
    init {
        require(pilotId > 0L)
        require(orderId.isNotBlank())
        require(recordingSourceId.isNotBlank())
    }
}

/**
 * Contract neutral event input. The generated v2 model and payload validation
 * will be added after the backend publishes the OpenAPI contract.
 */
data class EventInput(
    val type: String,
    val capturedAt: Long,
    val payload: Map<String, Any?>,
    val rateClass: EventRateClass = EventRateClass.SENSOR
) {
    init {
        require(type.isNotBlank())
        require(capturedAt >= 0L)
    }
}

/** Sensor values are sparse; actual state changes and pilot intent are never throttled. */
enum class EventRateClass { SENSOR, STATE_OR_INTENT }

/**
 * Local attribution for one order-aircraft chain. `recordingGeneration` is an
 * in-process stale-callback guard and must not be serialized into event packets.
 */
data class EventStreamContext(
    val operation: RecordingOperation,
    val chainId: String,
    val aircraft: AircraftIdentity,
    val recordingGeneration: Long
)

/** Immutable, contract-neutral draft for the local event queue. */
class EventDraft internal constructor(
    val eventId: String,
    val type: String,
    val capturedAt: Long,
    payload: Map<String, Any?>,
    val context: EventStreamContext
) {
    val payload: Map<String, Any?> = freezeObject(payload)

    init {
        require(eventId.isNotBlank())
        require(type.isNotBlank())
        require(capturedAt >= 0L)
    }

    private fun freezeObject(source: Map<String, Any?>): Map<String, Any?> {
        val copy = LinkedHashMap<String, Any?>(source.size)
        source.forEach { (key, value) -> copy[key] = freezeValue(value) }
        return Collections.unmodifiableMap(copy)
    }

    private fun freezeValue(value: Any?): Any? = when (value) {
        null, is String, is Boolean -> value
        is Byte, is Short, is Int, is Long, is BigInteger, is BigDecimal -> value
        is Float -> value.also { require(it.isFinite()) { "Event payload numbers must be finite" } }
        is Double -> value.also { require(it.isFinite()) { "Event payload numbers must be finite" } }
        is Map<*, *> -> {
            val copy = LinkedHashMap<String, Any?>(value.size)
            value.forEach { (key, item) ->
                require(key is String) { "Event payload object keys must be strings" }
                copy[key] = freezeValue(item)
            }
            Collections.unmodifiableMap(copy)
        }
        is Iterable<*> -> Collections.unmodifiableList(value.map(::freezeValue))
        is Array<*> -> Collections.unmodifiableList(value.map(::freezeValue))
        else -> error("Unsupported event payload value: ${value::class.java.name}")
    }
}

fun interface EventDraftSink {
    /** Local IO only: persist all drafts atomically, or throw without committing any of them. */
    fun append(events: List<EventDraft>)
}

sealed interface EventCaptureResult {
    data class Recorded(val events: List<EventDraft>) : EventCaptureResult
    data class Ignored(val reason: IgnoreReason) : EventCaptureResult
}

enum class IgnoreReason { NOT_LOGGED_IN, NO_OPERATION, AIRCRAFT_NOT_READY, STALE_CALLBACK, EMPTY, THROTTLED }

/**
 * Owns only local order attribution, the DJI connection generation and sparse
 * event capture (REQ-PILOT-EXEC-007). Preparation only; not installed in DI.
 * It has no network calls, server registration, lease, or WS. Selecting an order
 * locally grants no server permission.
 */
class EventStreamCoordinator(
    private val sink: EventDraftSink,
    private val eventIdFactory: () -> String = { UUID.randomUUID().toString() },
    private val chainIdFactory: () -> String = { UUID.randomUUID().toString() },
    private val minimumSensorIntervalMs: Long = DEFAULT_MINIMUM_SENSOR_INTERVAL_MS,
    private val elapsedRealtimeMs: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    init {
        require(minimumSensorIntervalMs >= DEFAULT_MINIMUM_SENSOR_INTERVAL_MS)
    }

    private data class ConnectedAircraft(val identity: AircraftIdentity, val generation: Long)

    private var pilotId: Long? = null
    private var operation: RecordingOperation? = null
    private var connectedAircraft: ConnectedAircraft? = null
    private var nextConnectionGeneration = 0L
    private var nextRecordingGeneration = 0L
    private var context: EventStreamContext? = null
    private val latestSensorAtByType = mutableMapOf<String, Long>()

    @get:Synchronized
    val currentContext: EventStreamContext?
        get() = context

    /** Account changes stop attribution. Persisted drafts retain their original owner. */
    @Synchronized
    fun setPilot(nextPilotId: Long?) {
        require(nextPilotId == null || nextPilotId > 0L)
        if (pilotId == nextPilotId) return
        pilotId = nextPilotId
        endOperation()
    }

    /** Repeating the same selection is harmless; changing orders starts a new chain. */
    @Synchronized
    fun beginOperation(next: RecordingOperation) {
        check(pilotId == next.pilotId) { "Cannot attribute events to another account" }
        if (operation == next) return
        operation = next
        latestSensorAtByType.clear()
        rebuildContext()
    }

    /** Stops attribution but leaves DJI connection state untouched. */
    @Synchronized
    fun endOperation() {
        operation = null
        context = null
        latestSensorAtByType.clear()
    }

    /** Returns a local connection token, used only to reject delayed disconnect callbacks. */
    @Synchronized
    fun aircraftConnected(identity: AircraftIdentity): Long {
        val current = connectedAircraft
        if (current?.identity == identity) {
            return current.generation
        }

        nextConnectionGeneration++
        connectedAircraft = ConnectedAircraft(identity, nextConnectionGeneration)
        latestSensorAtByType.clear()
        rebuildContext()
        return nextConnectionGeneration
    }

    /** An old disconnect callback cannot clear a newer aircraft connection. */
    @Synchronized
    fun aircraftDisconnected(expectedGeneration: Long): Boolean {
        val current = connectedAircraft ?: return false
        if (current.generation != expectedGeneration) return false
        connectedAircraft = null
        context = null
        latestSensorAtByType.clear()
        return true
    }

    /** Invoke on a serial background IO worker; the sink must finish local persistence before returning. */
    @Synchronized
    fun record(events: List<EventInput>, recordingGeneration: Long): EventCaptureResult {
        if (pilotId == null) return EventCaptureResult.Ignored(IgnoreReason.NOT_LOGGED_IN)
        val currentContext = context ?: return EventCaptureResult.Ignored(
            if (operation == null) IgnoreReason.NO_OPERATION else IgnoreReason.AIRCRAFT_NOT_READY
        )
        if (currentContext.recordingGeneration != recordingGeneration) {
            return EventCaptureResult.Ignored(IgnoreReason.STALE_CALLBACK)
        }
        if (events.isEmpty()) return EventCaptureResult.Ignored(IgnoreReason.EMPTY)

        val now = elapsedRealtimeMs()
        val nextSensorTimes = latestSensorAtByType.toMutableMap()
        val drafts = events.mapNotNull { event ->
            if (event.rateClass == EventRateClass.SENSOR &&
                !isSensorEventDue(event.type, now, nextSensorTimes)
            ) return@mapNotNull null
            EventDraft(eventIdFactory(), event.type, event.capturedAt, event.payload, currentContext)
        }
        if (drafts.isEmpty()) return EventCaptureResult.Ignored(IgnoreReason.THROTTLED)

        // Commit throttle state only after the sink confirms persistence.
        sink.append(Collections.unmodifiableList(drafts))
        latestSensorAtByType.clear()
        latestSensorAtByType.putAll(nextSensorTimes)
        return EventCaptureResult.Recorded(Collections.unmodifiableList(drafts))
    }

    private fun isSensorEventDue(
        type: String,
        elapsedMs: Long,
        timestamps: MutableMap<String, Long>
    ): Boolean {
        val previous = timestamps[type]
        val due = previous == null || elapsedMs - previous >= minimumSensorIntervalMs
        if (due) timestamps[type] = elapsedMs
        return due
    }

    private fun rebuildContext() {
        val selected = operation
        val aircraft = connectedAircraft
        context = if (selected == null || aircraft == null) {
            null
        } else {
            nextRecordingGeneration++
            EventStreamContext(selected, chainIdFactory(), aircraft.identity, nextRecordingGeneration)
        }
    }

    private companion object {
        const val DEFAULT_MINIMUM_SENSOR_INTERVAL_MS = 2_000L
    }
}
