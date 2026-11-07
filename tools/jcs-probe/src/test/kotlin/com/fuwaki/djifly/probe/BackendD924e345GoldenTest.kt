package com.fuwaki.djifly.probe

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

/** Run the independent golden/strict corpus against CURRENT pinned main, not just old a11d931. */
class BackendD924e345GoldenTest : BackendA11d931GoldenTest() {
    override val fixtureRoot = "/backend-main-stage1"
    override val fingerprints = mapOf(
        "DRAFT.md" to "08526bd0396c1fbb50ba314f4e83415bda57d6579df2b586bddb28f7ef479358",
        "golden-vectors.json" to "08228ecea348d292db2cdf688112b0d2aae8ade3f1ac6daaaccf4e29e1b2a9b4",
        "verify-golden.mjs" to "8257bbc1aeb5f1b7fd3b7204f07024f21330a1e2d154551e4399dd65387d7e66"
    )
    private fun bytes(path: String) = javaClass.getResourceAsStream(path)!!.use { it.readBytes() }
    private fun json(path: String) = JsonParser.parseString(bytes(path).toString(Charsets.UTF_8)).asJsonObject

    @Test fun sourceManifestPinsActualMainAndExactRemoteGitBlobs() {
        val source = json("$fixtureRoot/SOURCE.json")
        assertEquals("d924e34522119ae220b48dee03669752f3d8e331", source["commit"].asString)
        assertEquals("8d3c10ad3393306506ec57254306dc2c6ff8b156", source["clientBase"].asString)
        val files = source.getAsJsonObject("files")
        assertEquals(4, files.size())
        files.entrySet().forEach { (name, descriptor) ->
            val metadata = descriptor.asJsonObject
            val local = if (name.startsWith("spec/event-contract/")) "$fixtureRoot/${name.substringAfterLast('/')}"
                else "/stage1-openapi/drone-backend.openapi.json"
            val raw = bytes(local)
            assertEquals(metadata["bytes"].asInt, raw.size)
            assertEquals(metadata["sha256"].asString, PinnedDraftProbe.sha(raw))
            val gitBlob = MessageDigest.getInstance("SHA-1").digest("blob ${raw.size}\u0000".toByteArray() + raw)
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals(name, metadata["gitBlobSha"].asString, gitBlob)
        }
    }
    @Test fun currentOpenApiHasBothRealOperationsButDoesNotEncodeStrictRequiredNullRules() {
        val spec = json("/stage1-openapi/drone-backend.openapi.json")
        val schemas = spec.getAsJsonObject("components").getAsJsonObject("schemas")
        mapOf("event-packets" to "EventPacketDraftDTO", "event-reconciliation" to "EventReconciliationDraftDTO").forEach { (path, model) ->
            val post = spec.getAsJsonObject("paths").getAsJsonObject("/dispatch/pilot/orders/{orderId}/$path").getAsJsonObject("post")
            val body = post.getAsJsonObject("requestBody")
            assertTrue(body["required"].asBoolean)
            assertEquals("#/components/schemas/$model", body.getAsJsonObject("content").getAsJsonObject("application/json")
                .getAsJsonObject("schema")["\$ref"].asString)
            assertTrue(post.getAsJsonObject("responses").has("200"))
        }
        val packet = schemas.getAsJsonObject("EventPacketDraftDTO")
        assertEquals(15, packet.getAsJsonObject("properties").size())
        assertFalse(packet.has("required")) // Known upstream gap, NOT permission to omit fields.
        val prev = packet.getAsJsonObject("properties").getAsJsonObject("prevPacketId")
        assertEquals("string", prev["type"].asString)
        assertFalse(prev.has("nullable")) // Yet actual schema requires explicit null on first packet.
        val recon = schemas.getAsJsonObject("EventReconciliationDraftDTO")
        assertEquals(9, recon.getAsJsonObject("properties").size())
        assertFalse(recon.has("required"))
        assertEquals(JsonObject(), recon.getAsJsonObject("properties")["prefix"])
    }
    @Test fun actualImplementationIdentityRestrictionsAreNotJustGenericNonemptyStrings() {
        val vectors = json("$fixtureRoot/golden-vectors.json").getAsJsonArray("vectors")
        val original = PinnedDraftProbe.rawHex(vectors[0].asJsonObject["inputUtf8Hex"].asString)
        listOf("orderId" to "not-a-uuid", "serialNumber" to " SN1", "serialNumber" to "a".repeat(65),
            "modelCode" to "é", "modelCode" to "a".repeat(129), "serialSource" to "flight_controller").forEach { (key, value) ->
            val packet = PinnedDraftProbe.envelope(original); packet.addProperty(key, value)
            try { PinnedDraftProbe.envelope(packet.toString().toByteArray()); fail("Expected SCHEMA for $key") }
            catch (e: DraftProbeRejected) { assertEquals("SCHEMA", e.layer) }
        }
    }
}
