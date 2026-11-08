@file:OptIn(ExperimentalStage1Api::class)

package com.fuwaki.djifly.platform.eventstage1

import com.fuwaki.djifly.platform.eventstream.EventDraft
import com.fuwaki.djifly.platform.eventstream.EventStreamContext
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID

/**
 * Turns already-normalized local drafts into one v2 packet.
 *
 * This class deliberately does not translate DJI units or decide which event types are valid. The
 * caller supplies the contract payload, [Stage1Codec] owns the envelope/hash, and the server owns
 * semantic event acceptance. It also does not persist or upload anything.
 */
@ExperimentalStage1Api
class EventStreamPacketEncoder(
    private val codec: Stage1Codec = Stage1Codec(),
    private val packetIdFactory: () -> String = { UUID.randomUUID().toString() }
) {
    fun encode(
        context: EventStreamContext,
        drafts: List<EventDraft>,
        previous: Stage1Link? = null
    ): Stage1Packet {
        require(drafts.isNotEmpty()) { "A packet must contain at least one event" }
        require(drafts.all { it.context == context }) { "All drafts must belong to the same recording context" }

        val identity = Stage1Identity(
            orderId = context.operation.orderId,
            recordingSourceId = context.operation.recordingSourceId,
            chainId = context.chainId,
            serialNumber = context.aircraft.serialNumber,
            serialSource = context.aircraft.serialSource,
            modelCode = context.aircraft.modelCode
        )
        val events = JsonArray()
        drafts.forEach { draft ->
            events.add(JsonObject().apply {
                addProperty("eventId", draft.eventId)
                addProperty("capturedAt", draft.capturedAt)
                addProperty("type", draft.type)
                add("payload", jsonObject(draft.payload))
            })
        }
        return codec.create(identity, packetIdFactory(), events.toString().toByteArray(Charsets.UTF_8), previous)
    }

    private fun jsonObject(source: Map<String, Any?>): JsonObject = JsonObject().apply {
        source.forEach { (key, value) -> add(key, jsonValue(value)) }
    }

    private fun jsonValue(value: Any?): JsonElement = when (value) {
        null -> JsonNull.INSTANCE
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Byte, is Short, is Int, is Long, is BigInteger -> JsonPrimitive(asSafeInteger(value))
        is BigDecimal -> JsonPrimitive(asSafeInteger(value))
        is Float -> JsonPrimitive(asSafeInteger(value.toDouble()))
        is Double -> JsonPrimitive(asSafeInteger(value))
        is Map<*, *> -> JsonObject().apply {
            value.forEach { (key, item) ->
                require(key is String) { "Event payload object keys must be strings" }
                add(key, jsonValue(item))
            }
        }
        is Iterable<*> -> JsonArray().apply { value.forEach { add(jsonValue(it)) } }
        is Array<*> -> JsonArray().apply { value.forEach { add(jsonValue(it)) } }
        else -> error("Unsupported event payload value: ${value::class.java.name}")
    }

    private fun asSafeInteger(value: Any): BigInteger {
        val integer = when (value) {
            is BigInteger -> value
            is BigDecimal -> value.toBigIntegerExact()
            is Byte -> BigInteger.valueOf(value.toLong())
            is Short -> BigInteger.valueOf(value.toLong())
            is Int -> BigInteger.valueOf(value.toLong())
            is Long -> BigInteger.valueOf(value)
            is Float, is Double -> {
                val number = (value as Number).toDouble()
                require(number.isFinite() && number == number.toLong().toDouble()) {
                    "Event payload numbers must be finite integers"
                }
                BigDecimal.valueOf(number).toBigIntegerExact()
            }
            else -> error("Unsupported event number: ${value::class.java.name}")
        }
        require(integer.abs() <= BigInteger.valueOf(Stage1Json.SAFE)) {
            "Event payload integer exceeds JSON safe range"
        }
        return integer
    }
}
