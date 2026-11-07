package com.fuwaki.djifly.probe

import com.google.gson.JsonParser
import org.erdtman.jcs.JsonCanonicalizer
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

/** Synthetic draft-formula vectors only; NOT a packet encoder, client or approved identity schema. */
class JcsHashDraftTest {
    private val identity = """{"fixtureIdentity":"not-a-production-chain"}"""
    private val c = """{"chainId":"00000000-0000-4000-8000-000000000002","events":[{"capturedAt":1794039540000,"eventId":"00000000-0000-4000-8000-000000000004","payload":{"testValue":7},"type":"FIXTURE_ONLY"}],"hashVersion":1,"height":1,"model":"FIXTURE_ONLY","orderId":"fixture-order","packetId":"00000000-0000-4000-8000-000000000003","prevChainHash":"d5d33098d023a8b12089c8ed477fa81970d2f166f60a6abca8bba7abe5ccf4c0","prevPacketId":null,"recordingSourceId":"00000000-0000-4000-8000-000000000001","schemaVersion":1,"serialNumber":"FIXTURE_ONLY","serialSource":"FIXTURE_ONLY"}"""
    private val full = """{"chainHash":"afa5af9e67c80379c07f9e0a9efb5e9e1d1fd43dd2504b798ffcafa20541ae4b","chainId":"00000000-0000-4000-8000-000000000002","events":[{"capturedAt":1794039540000,"eventId":"00000000-0000-4000-8000-000000000004","payload":{"testValue":7},"type":"FIXTURE_ONLY"}],"hashVersion":1,"height":1,"model":"FIXTURE_ONLY","orderId":"fixture-order","packetId":"00000000-0000-4000-8000-000000000003","prevChainHash":"d5d33098d023a8b12089c8ed477fa81970d2f166f60a6abca8bba7abe5ccf4c0","prevPacketId":null,"recordingSourceId":"00000000-0000-4000-8000-000000000001","schemaVersion":1,"serialNumber":"FIXTURE_ONLY","serialSource":"FIXTURE_ONLY"}"""
    private val h0 = "d5d33098d023a8b12089c8ed477fa81970d2f166f60a6abca8bba7abe5ccf4c0"
    private val h1 = "afa5af9e67c80379c07f9e0a9efb5e9e1d1fd43dd2504b798ffcafa20541ae4b"
    private val digest = "e558a5d9f246ccad66bb2e0bd207e84b167192b385ec8c0be2b5e7dbbae570ee"
    private fun sha(vararg chunks: ByteArray): String {
        val hash = MessageDigest.getInstance("SHA-256")
        chunks.forEach { hash.update(it) }
        return hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private fun utf8(s: String) = s.toByteArray(Charsets.UTF_8)
    private fun raw32(hex: String): ByteArray {
        require(hex.matches(Regex("[0-9a-f]{64}")))
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    private fun canonical(s: String) = JsonCanonicalizer(s).encodedUTF8

    @Test fun anchorAndChainHashMatchIndependentNodeOracle() {
        assertEquals(h0, sha(utf8("UAV-EVENT-ANCHOR-v1\n"), canonical(identity)))
        assertEquals(h1, sha(utf8("UAV-EVENT-PACKET-v1\n"), raw32(h0), canonical(c)))
    }
    @Test fun contentDigestIsCanonicalFullPacketIncludingChainHashNotRawBytesOrC() {
        assertEquals(digest, sha(canonical(full)))
        assertNotEquals(digest, sha(canonical(c)))
        assertNotEquals(digest, sha(utf8("  " + full + "\n")))
        assertNotEquals(digest, h1)
    }
    @Test fun objectKeyOrderDoesNotChangeContentDigestButOriginalBytesDiffer() {
        val parsed = JsonParser.parseString(full).asJsonObject
        val reversed = com.google.gson.JsonObject()
        parsed.entrySet().toList().reversed().forEach { reversed.add(it.key, it.value) }
        assertNotEquals(full, reversed.toString())
        assertEquals(digest, sha(canonical(reversed.toString())))
    }
    @Test fun changedClaimedChainHashOrPreviousHashCannotBeHiddenByContentDigest() {
        val changed = JsonParser.parseString(full).asJsonObject
        changed.addProperty("chainHash", "0".repeat(64))
        assertNotEquals(digest, sha(canonical(changed.toString())))
        changed.remove("chainHash")
        changed.addProperty("prevChainHash", "0".repeat(64))
        assertNotEquals(h1, sha(utf8("UAV-EVENT-PACKET-v1\n"), raw32("0".repeat(64)), canonical(changed.toString())))
        assertNotEquals(h1, sha(utf8("UAV-EVENT-PACKET-v1\n"), utf8(h0), canonical(c)))
    }
    @Test fun includingSelfHashOrDroppingNullPreviousIdIsNotTheSameChainFormula() {
        assertNotEquals(h1, sha(utf8("UAV-EVENT-PACKET-v1\n"), raw32(h0), canonical(full)))
        val changed = JsonParser.parseString(c).asJsonObject
        changed.remove("prevPacketId")
        assertNotEquals(h1, sha(utf8("UAV-EVENT-PACKET-v1\n"), raw32(h0), canonical(changed.toString())))
    }
}
