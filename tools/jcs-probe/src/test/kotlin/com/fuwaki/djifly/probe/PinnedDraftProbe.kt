package com.fuwaki.djifly.probe

import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import org.erdtman.jcs.JsonCanonicalizer
import java.io.StringReader
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** TEST-ONLY interpretation of backend a11d931. No request builder, live API or SDK mapping. */
internal class DraftProbeRejected(val layer: String) : IllegalArgumentException(layer)
internal object PinnedDraftProbe {
    val identityFields = listOf("assignmentVersion", "chainId", "hashVersion", "modelCode", "orderId", "recordingSourceId", "schemaVersion", "serialNumber", "serialSource")
    private val rootFields = (identityFields + listOf("packetId", "prevPacketId", "height", "prevChainHash", "chainHash", "events")).toSet()
    private val eventFields = setOf("eventId", "capturedAt", "type", "payload")
    private val safeMax = BigInteger("9007199254740991")
    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    private fun insist(ok: Boolean, layer: String) { if (!ok) throw DraftProbeRejected(layer) }
    fun strict(bytes: ByteArray): JsonElement {
        insist(bytes.size <= 262144, "STRICT_JSON")
        val text = try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() }
            catch (e: Exception) { throw DraftProbeRejected("STRICT_JSON") }
        insist(!text.startsWith('\ufeff'), "STRICT_JSON")
        asciiUnicodeEscapes(text)
        try {
            return JsonReader(StringReader(text)).use { r ->
                r.isLenient = false
                val result = read(r, 0)
                insist(r.peek() == JsonToken.END_DOCUMENT, "STRICT_JSON")
                result
            }
        } catch (e: DraftProbeRejected) { throw e }
        catch (e: Exception) { throw DraftProbeRejected("STRICT_JSON") }
    }
    private fun read(r: JsonReader, depth: Int): JsonElement {
        insist(depth <= 8, "STRICT_JSON")
        return when (r.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                r.beginObject(); val obj = JsonObject(); val seen = HashSet<String>()
                while (r.hasNext()) {
                    val name = r.nextName(); unicode(name)
                    insist(seen.add(name) && seen.size <= 32, "STRICT_JSON")
                    obj.add(name, read(r, depth + 1))
                }
                r.endObject(); obj
            }
            JsonToken.BEGIN_ARRAY -> {
                r.beginArray(); val array = JsonArray()
                while (r.hasNext()) { insist(array.size() < 500, "STRICT_JSON"); array.add(read(r, depth + 1)) }
                r.endArray(); array
            }
            JsonToken.STRING -> JsonPrimitive(r.nextString().also { unicode(it) })
            JsonToken.NUMBER -> {
                val token = r.nextString()
                insist(token.length <= 17 && token != "-0" && token.matches(Regex("-?(0|[1-9][0-9]*)")), "STRICT_JSON")
                val n = BigInteger(token); insist(n.abs() <= safeMax, "STRICT_JSON"); JsonPrimitive(n)
            }
            JsonToken.BOOLEAN -> JsonPrimitive(r.nextBoolean())
            JsonToken.NULL -> { r.nextNull(); JsonNull.INSTANCE }
            else -> throw DraftProbeRejected("STRICT_JSON")
        }
    }
    private fun unicode(text: String) {
        insist(text.length <= 1024, "STRICT_JSON")
        var i = 0
        while (i < text.length) {
            val c = text[i++]
            if (Character.isHighSurrogate(c)) {
                insist(i < text.length && Character.isLowSurrogate(text[i++]), "STRICT_JSON")
            } else insist(!Character.isLowSurrogate(c), "STRICT_JSON")
        }
    }
    private fun asciiUnicodeEscapes(text: String) {
        var quoted = false; var i = 0
        while (i < text.length) {
            val c = text[i++]
            if (c == '"') quoted = !quoted
            else if (quoted && c == '\\') {
                insist(i < text.length, "STRICT_JSON")
                if (text[i++] == 'u') {
                    insist(i + 4 <= text.length, "STRICT_JSON")
                    repeat(4) { insist(text[i++] in "0123456789abcdefABCDEF", "STRICT_JSON") }
                }
            }
        }
    }
    fun canonical(element: JsonElement): ByteArray = JsonCanonicalizer(element.toString()).encodedUTF8
    fun identity(packet: JsonObject) = JsonObject().also { result -> identityFields.forEach { result.add(it, packet[it].deepCopy()) } }
    fun withoutHash(packet: JsonObject) = packet.deepCopy().also { it.remove("chainHash") }
    fun sha(vararg parts: ByteArray): String = MessageDigest.getInstance("SHA-256").let { digest ->
        parts.forEach { digest.update(it) }; digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    fun rawHex(hex: String): ByteArray {
        insist(hex.length % 2 == 0 && hex.all { it in "0123456789abcdef" }, "SCHEMA")
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    fun anchor(packet: JsonObject) = sha("UAV-EVENT-ANCHOR-v1\n".toByteArray(), canonical(identity(packet)))
    fun chain(packet: JsonObject) = sha("UAV-EVENT-PACKET-v1\n".toByteArray(), rawHex(packet["prevChainHash"].asString), canonical(withoutHash(packet)))
    fun envelope(bytes: ByteArray): JsonObject {
        val parsed = strict(bytes); insist(parsed.isJsonObject, "SCHEMA"); val p = parsed.asJsonObject
        insist(p.keySet() == rootFields, "SCHEMA")
        listOf("schemaVersion", "hashVersion", "assignmentVersion", "height").forEach { integer(p[it]) }
        insist(p["schemaVersion"].asLong == 1L && p["hashVersion"].asLong == 1L && p["assignmentVersion"].asLong > 0, "SCHEMA")
        insist(p["height"].asLong in 1..1000000, "SCHEMA")
        listOf("orderId", "modelCode", "serialNumber", "serialSource").forEach { string(p[it]) }
        listOf("recordingSourceId", "chainId", "packetId").forEach { uuid(p[it]) }
        if (p["height"].asLong == 1L) insist(p["prevPacketId"].isJsonNull, "SCHEMA") else uuid(p["prevPacketId"])
        listOf("prevChainHash", "chainHash").forEach { key ->
            string(p[key]); insist(p[key].asString.matches(Regex("[0-9a-f]{64}")), "SCHEMA")
        }
        insist(p["events"].isJsonArray && p["events"].asJsonArray.size() in 1..500, "SCHEMA")
        val ids = HashSet<String>()
        p["events"].asJsonArray.forEach { event ->
            insist(event.isJsonObject, "SCHEMA"); val e = event.asJsonObject
            insist(e.keySet() == eventFields, "SCHEMA"); uuid(e["eventId"])
            insist(ids.add(e["eventId"].asString), "SCHEMA")
            integer(e["capturedAt"]); string(e["type"]); insist(e["payload"].isJsonObject, "SCHEMA")
            // Payload type/range/time/authorization is event semantics, NOT decided by this probe.
        }
        return p
    }
    fun binding(packet: JsonObject, pathOrderId: String, knownIdentity: JsonObject) {
        insist(packet["orderId"].asString == pathOrderId && identity(packet) == knownIdentity, "BINDING")
    }
    fun selfHash(packet: JsonObject) {
        if (packet["height"].asLong == 1L) insist(packet["prevChainHash"].asString == anchor(packet), "SELF_HASH")
        insist(packet["chainHash"].asString == chain(packet), "SELF_HASH")
        // A later packet's missing predecessor is not a rejection; continuity is server state.
    }
    private fun string(e: JsonElement?) { insist(e != null && e.isJsonPrimitive && e.asJsonPrimitive.isString && e.asString.isNotEmpty(), "SCHEMA") }
    private fun integer(e: JsonElement?) { insist(e != null && e.isJsonPrimitive && e.asJsonPrimitive.isNumber, "SCHEMA") }
    private fun uuid(e: JsonElement?) { string(e); insist(uuid.matches(e!!.asString), "SCHEMA") }
}
