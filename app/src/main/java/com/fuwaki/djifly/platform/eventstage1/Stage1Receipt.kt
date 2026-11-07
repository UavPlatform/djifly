@file:OptIn(ExperimentalStage1Api::class)
package com.fuwaki.djifly.platform.eventstage1

import com.google.gson.*
import java.util.Collections

internal data class ExpectedPacket(val link: Stage1Link, val digest: String, val eventIds: List<String>)
@ExperimentalStage1Api
data class Stage1MissingRange(val fromHeight: Long, val toHeight: Long)
@ExperimentalStage1Api
data class Stage1Progress(val validationVersion: Long, val contiguousHeight: Long, val contiguousHash: String?,
    val highestReceivedHeight: Long, val missingRanges: List<Stage1MissingRange>, val missingCursor: Long?,
    val integrity: String, val projectionRevision: Long)
internal data class ValidatedAck(val fixedReceipt: ByteArray, val progress: Stage1Progress?, val chainConflict: Boolean = false)

internal object Stage1Receipt {
    fun expected(packet: Stage1Packet) = ExpectedPacket(packet.link, packet.contentDigest, packet.eventIds)
    fun summary(packet: ExpectedPacket, pilotId: Long): ByteArray = Stage1Json.canonical(packet.link.identity.json().also { o ->
        o.addProperty("localSummaryVersion", 1); o.addProperty("pilotId", pilotId.toString())
        o.addProperty("packetId", packet.link.packetId); o.addProperty("height", packet.link.height)
        o.addProperty("chainHash", packet.link.chainHash); o.addProperty("contentDigest", packet.digest)
        o.add("eventIds", JsonArray().also { ids -> packet.eventIds.forEach(ids::add) })
    })
    fun readSummary(bytes: ByteArray, pilotId: Long): ExpectedPacket {
        val o = Stage1Json.obj(Stage1Json.parse(bytes))
        require(Stage1Json.long(o["localSummaryVersion"]) == 1L && Stage1Json.text(o["pilotId"]) == pilotId.toString()) { "Operation belongs to another pilot" }
        require(Stage1Json.long(o["schemaVersion"]) == 1L && Stage1Json.long(o["hashVersion"]) == 1L)
        val identity = Stage1Codec().identity(o)
        val id = Stage1Json.uuid(Stage1Json.text(o["packetId"]))
        val height = Stage1Json.long(o["height"]); require(height in 1..1000000)
        val hash = Stage1Json.text(o["chainHash"]); Stage1Json.unhex(hash)
        val digest = Stage1Json.text(o["contentDigest"]); Stage1Json.unhex(digest)
        require(o["eventIds"].isJsonArray)
        val ids = o["eventIds"].asJsonArray.map { Stage1Json.uuid(Stage1Json.text(it)) }
        require(ids.isNotEmpty() && ids.distinct().size == ids.size)
        return ExpectedPacket(Stage1Link(identity, height, id, hash), digest, Collections.unmodifiableList(ids))
    }
    fun acknowledgement(bytes: ByteArray, expected: ExpectedPacket): ValidatedAck {
        val envelope = Stage1Json.obj(Stage1Json.parse(bytes))
        require(envelope["success"]?.isJsonPrimitive == true && envelope["success"].asJsonPrimitive.isBoolean && envelope["success"].asBoolean)
        require(Stage1Json.long(envelope["code"]) == 200L)
        require(Stage1Json.nullableText(envelope["errorCode"]) == null)
        val data = Stage1Json.obj(envelope["data"]); require(Stage1Json.long(data["schemaVersion"]) == 1L)
        val receipt = validate(Stage1Json.obj(data["receipt"]), expected)
        // Fixed durability does not depend on dynamic progress. Malformed/missing progress is not synchronization proof.
        val progress = try { progress(Stage1Json.obj(data["progress"]), expected) } catch (_: RuntimeException) { null }
        val conflict = progress != null && ((progress.contiguousHeight == expected.link.height && progress.contiguousHash != expected.link.chainHash) ||
            (progress.contiguousHeight == 0L && progress.contiguousHash != null && progress.contiguousHash != Stage1Codec().anchor(expected.link.identity)))
        return ValidatedAck(receipt, if (conflict) null else progress, conflict)
    }
    fun stored(bytes: ByteArray, expected: ExpectedPacket): ByteArray = validate(Stage1Json.obj(Stage1Json.parse(bytes)), expected)
    private fun validate(receipt: JsonObject, expected: ExpectedPacket): ByteArray {
        require(receipt.keySet() == setOf("receiptVersion", "packetId", "contentDigest", "chainHash", "durability", "decidedAt", "results"))
        require(Stage1Json.long(receipt["receiptVersion"]) == 1L)
        require(Stage1Json.text(receipt["packetId"]) == expected.link.packetId)
        require(Stage1Json.text(receipt["contentDigest"]) == expected.digest)
        require(Stage1Json.text(receipt["chainHash"]) == expected.link.chainHash)
        require(Stage1Json.text(receipt["durability"]) == "RAW_AND_RESULTS_COMMITTED")
        require(Stage1Json.long(receipt["decidedAt"]) >= 0)
        val results = receipt["results"]; require(results.isJsonArray && results.asJsonArray.size() == expected.eventIds.size)
        results.asJsonArray.forEachIndexed { index, item ->
            val result = Stage1Json.obj(item)
            require(result.keySet().containsAll(setOf("eventId", "outcome")) &&
                setOf("eventId", "outcome", "reason", "sourcePacketId").containsAll(result.keySet()))
            require(Stage1Json.text(result["eventId"]) == expected.eventIds[index]) { "Missing, reordered or duplicate event result" }
            val reason = Stage1Json.nullableText(result["reason"])
            val source = Stage1Json.nullableText(result["sourcePacketId"])
            when (Stage1Json.text(result["outcome"])) {
                "ACCEPTED" -> require(reason == null && source == null)
                "DUPLICATE" -> { require(reason == "DUPLICATE"); Stage1Json.uuid(requireNotNull(source)); require(source != expected.link.packetId) }
                "REJECTED" -> require(!reason.isNullOrBlank() && source == null)
                else -> error("Unknown event outcome")
            }
            // Nullable VO fields may be omitted by response serialization; normalize only local receipt representation.
            result.add("reason", reason?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
            result.add("sourcePacketId", source?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
        }
        return Stage1Json.canonical(receipt)
    }
    private fun progress(o: JsonObject, expected: ExpectedPacket): Stage1Progress {
        val version = Stage1Json.long(o["validationVersion"]); require(version > 0)
        val contiguous = Stage1Json.long(o["contiguousHeight"]); require(contiguous in 0..1000000)
        val highest = Stage1Json.long(o["highestReceivedHeight"]); require(highest in contiguous..1000000 && highest >= expected.link.height)
        val hash = Stage1Json.nullableText(o["contiguousHash"])
        if (hash != null) Stage1Json.unhex(hash) else require(contiguous == 0L)
        val integrity = Stage1Json.text(o["integrity"]); require(integrity in setOf("OPEN", "FORKED"))
        val revision = Stage1Json.long(o["projectionRevision"]); require(revision >= 0)
        val cursor = Stage1Json.nullableLong(o["missingCursor"]); require(cursor == null || cursor > 0)
        require(o["missingRanges"].isJsonArray && o["missingRanges"].asJsonArray.size() <= 50)
        var previousEnd = contiguous
        val ranges = o["missingRanges"].asJsonArray.map { value ->
            val range = Stage1Json.obj(value); require(range.keySet() == setOf("fromHeight", "toHeight"))
            val from = Stage1Json.long(range["fromHeight"]); val to = Stage1Json.long(range["toHeight"])
            require(from > previousEnd && to in from..highest); previousEnd = to
            Stage1MissingRange(from, to)
        }
        return Stage1Progress(version, contiguous, hash, highest, Collections.unmodifiableList(ranges), cursor, integrity, revision)
    }
}
