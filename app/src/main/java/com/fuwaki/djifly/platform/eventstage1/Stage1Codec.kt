package com.fuwaki.djifly.platform.eventstage1

import com.google.gson.*
import java.util.Collections

@ExperimentalStage1Api
data class Stage1Identity(val orderId: String, val recordingSourceId: String,
    val chainId: String, val serialNumber: String, val serialSource: String, val modelCode: String) {
    init {
        Stage1Json.uuid(orderId); Stage1Json.uuid(recordingSourceId); Stage1Json.uuid(chainId)
        require(serialNumber.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}")))
        require(modelCode.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")))
        require(serialSource.matches(Regex("[A-Z][A-Z0-9_]{0,63}")))
    }
    internal fun json() = JsonObject().also {
        it.addProperty("schemaVersion", 2); it.addProperty("hashVersion", 2)
        it.addProperty("orderId", orderId)
        it.addProperty("recordingSourceId", recordingSourceId); it.addProperty("chainId", chainId)
        it.addProperty("serialNumber", serialNumber); it.addProperty("serialSource", serialSource); it.addProperty("modelCode", modelCode)
    }
}

@ExperimentalStage1Api
data class Stage1Link(val identity: Stage1Identity, val height: Long, val packetId: String, val chainHash: String)

@ExperimentalStage1Api
class Stage1Packet internal constructor(val link: Stage1Link, val prevPacketId: String?, val prevChainHash: String,
    val contentDigest: String, eventIds: List<String>, bytes: ByteArray) {
    private val fixedBytes = bytes.copyOf()
    val eventIds: List<String> = Collections.unmodifiableList(ArrayList(eventIds))
    fun originalBytes(): ByteArray = fixedBytes.copyOf()
}

/** Implemented Stage1 only. No SDK scaling, assignment synthesis, cutoff workflow or production DI. */
@ExperimentalStage1Api
class Stage1Codec {
    private val rootFields = setOf("schemaVersion", "hashVersion", "orderId", "recordingSourceId", "chainId",
        "serialNumber", "serialSource", "modelCode", "packetId", "prevPacketId", "height", "prevChainHash", "chainHash", "events")
    fun create(identity: Stage1Identity, packetId: String, eventsJson: ByteArray, previous: Stage1Link? = null): Stage1Packet {
        Stage1Json.uuid(packetId)
        if (previous != null) {
            require(previous.identity == identity) { "Cannot change identity within a chain" }
            require(previous.height in 1 until 1000000); Stage1Json.uuid(previous.packetId); Stage1Json.unhex(previous.chainHash)
        }
        val root = identity.json()
        root.addProperty("packetId", packetId); root.addProperty("height", previous?.height?.plus(1) ?: 1L)
        root.add("prevPacketId", previous?.packetId?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
        root.addProperty("prevChainHash", previous?.chainHash ?: anchor(identity))
        root.add("events", Stage1Json.parse(eventsJson))
        val hash = chain(root)
        root.addProperty("chainHash", hash)
        return decode(Stage1Json.canonical(root), identity.orderId)
    }
    fun decode(original: ByteArray, pathOrderId: String): Stage1Packet {
        val bytes = original.copyOf(); val root = Stage1Json.obj(Stage1Json.parse(bytes))
        require(root.keySet() == rootFields) { "Packet fields" }
        require(Stage1Json.long(root["schemaVersion"]) == 2L && Stage1Json.long(root["hashVersion"]) == 2L)
        val identity = identity(root); require(identity.orderId == pathOrderId) { "SCHEMA_ORDER" }
        val packetId = Stage1Json.uuid(Stage1Json.text(root["packetId"]))
        val height = Stage1Json.long(root["height"]); require(height in 1..1000000)
        val prevId = Stage1Json.nullableText(root["prevPacketId"])
        if (height == 1L) require(prevId == null) else Stage1Json.uuid(requireNotNull(prevId))
        val prevHash = Stage1Json.text(root["prevChainHash"]); Stage1Json.unhex(prevHash)
        val claimed = Stage1Json.text(root["chainHash"]); Stage1Json.unhex(claimed)
        if (height == 1L) require(prevHash == anchor(identity)) { "ANCHOR" }
        require(claimed == chain(root.deepCopy().also { it.remove("chainHash") })) { "HASH" }
        val events = root["events"]; require(events.isJsonArray && events.asJsonArray.size() in 1..500)
        val ids = ArrayList<String>()
        events.asJsonArray.forEach {
            val event = Stage1Json.obj(it); require(event.keySet() == setOf("eventId", "capturedAt", "type", "payload"))
            val id = Stage1Json.uuid(Stage1Json.text(event["eventId"])); require(id !in ids) { "Duplicate eventId" }; ids.add(id)
            require(Stage1Json.long(event["capturedAt"]) >= 0)
            Stage1Json.text(event["type"]); Stage1Json.obj(event["payload"])
            // Per-event payload ranges/type rejection belong to the server; no invented SDK units/defaults.
        }
        return Stage1Packet(Stage1Link(identity, height, packetId, claimed), prevId, prevHash,
            Stage1Json.sha(Stage1Json.canonical(root)), ids, bytes)
    }
    fun anchor(identity: Stage1Identity) = Stage1Json.sha("UAV-EVENT-ANCHOR-v2\n".toByteArray(Charsets.UTF_8), Stage1Json.canonical(identity.json()))
    private fun chain(withoutHash: JsonObject) = Stage1Json.sha("UAV-EVENT-PACKET-v2\n".toByteArray(Charsets.UTF_8),
        Stage1Json.unhex(Stage1Json.text(withoutHash["prevChainHash"])), Stage1Json.canonical(withoutHash))
    internal fun identity(root: JsonObject) = Stage1Identity(Stage1Json.text(root["orderId"]),
        Stage1Json.text(root["recordingSourceId"]), Stage1Json.text(root["chainId"]), Stage1Json.text(root["serialNumber"]),
        Stage1Json.text(root["serialSource"]), Stage1Json.text(root["modelCode"]))
}
