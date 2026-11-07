@file:OptIn(ExperimentalStage1Api::class)
package com.fuwaki.djifly.platform.eventstage1

import com.google.gson.*
import org.junit.Assert.*
import org.junit.Test

class Stage1CodecTest {
    private val codec=Stage1Codec()
    private fun reject(block:()->Unit) { try { block();fail("Expected strict rejection") } catch(_:Exception) { } }
    @Test fun allCurrentSharedGoldenPacketsAndCanonicalBytesMatch() {
        repeat(4) { index ->
            val v=Fixture.vector(index);val p=Fixture.packet(index)
            assertEquals(v["anchorHex"].asString,codec.anchor(p.link.identity))
            assertEquals(v["chainHashHex"].asString,p.link.chainHash)
            assertEquals(v["contentDigestHex"].asString,p.contentDigest)
            assertArrayEquals(Fixture.unhex(v["canonicalUtf8Hex"].asString),Stage1Json.canonical(Stage1Json.parse(p.originalBytes())))
            val without=Stage1Json.obj(Stage1Json.parse(p.originalBytes())).also { it.remove("chainHash") }
            assertArrayEquals(Fixture.unhex(v["withoutChainHashHex"].asString),Stage1Json.canonical(without))
            assertEquals(v["rawSha256Hex"].asString,Stage1Json.sha(p.originalBytes()))
        }
    }
    @Test fun createFirstAndNextPacketAreCanonicalAndKeepExplicitNull() {
        val first=Fixture.packet();val root=JsonParser.parseString(first.originalBytes().toString(Charsets.UTF_8)).asJsonObject
        val made=codec.create(first.link.identity,first.link.packetId,Fixture.wire(root["events"]))
        assertArrayEquals(first.originalBytes(),made.originalBytes())
        assertTrue(Stage1Json.obj(Stage1Json.parse(made.originalBytes()))["prevPacketId"].isJsonNull)
        val next=Fixture.packet(2);val nextEvents=Stage1Json.obj(Stage1Json.parse(next.originalBytes()))["events"]
        assertArrayEquals(next.originalBytes(),codec.create(first.link.identity,next.link.packetId,Fixture.wire(nextEvents),first.link).originalBytes())
    }
    @Test fun originalNoncanonicalBytesAreImmutableAndNotTheContentDigest() {
        val p=Fixture.packet(1);val original=p.originalBytes();val changed=p.originalBytes();changed[0]=0
        assertArrayEquals(original,p.originalBytes());assertNotEquals(Stage1Json.sha(original),p.contentDigest)
        assertEquals(Fixture.packet().contentDigest,p.contentDigest)
        reject { (p.eventIds as MutableList<String>).clear() }
    }
    @Test fun strictMalformedUtf8UnicodeAndAsciiHexRejections() {
        listOf(byteArrayOf(0xc3.toByte(),0x28),byteArrayOf(0xed.toByte(),0xa0.toByte(),0x80.toByte())).forEach {
            reject { Stage1Json.parse("{\"s\":\"".toByteArray()+it+"\"}".toByteArray()) }
        }
        listOf("{\"s\":\"\\ud800\"}","{\"s\":\"\\udc00\"}","{\"s\":\"\\uＦＦＦＦ\"}","{\"s\":\"\\u０000\"}","{\"s\":\"x\ny\"}").forEach { reject { Stage1Json.parse(it.toByteArray()) } }
        reject { Stage1Json.parse(byteArrayOf(0xef.toByte(),0xbb.toByte(),0xbf.toByte())+"{}".toByteArray()) }
        Stage1Json.parse("{\"s\":\"\\ud83d\\ude00\"}".toByteArray())
    }
    @Test fun duplicateNullKeysAndIntegerTokensCannotReachJcsPermissiveParser() {
        listOf("{\"a\":null,\"a\":1}","{\"a\":null,\"\\u0061\":1}","{\"a\":01}","{\"a\":-0}","{\"a\":1.0}","{\"a\":1e3}","{\"a\":NaN}","{\"a\":9007199254740992}","{\"a\":9007199254740993}").forEach { reject { Stage1Json.parse(it.toByteArray()) } }
        listOf("9007199254740991","-9007199254740991").forEach { assertEquals("["+it+"]",Stage1Json.canonical(Stage1Json.parse(("["+it+"]").toByteArray())).toString(Charsets.UTF_8)) }
    }
    @Test fun actualNonAsciiFixtureAndArrayOrderArePreservedWithoutNormalization() {
        val f=Fixture.doc.getAsJsonArray("canonicalFixtures")[0].asJsonObject
        assertArrayEquals(Fixture.unhex(f["canonicalUtf8Hex"].asString),Stage1Json.canonical(Stage1Json.parse(Fixture.unhex(f["inputUtf8Hex"].asString))))
        assertEquals("[2,1]",Stage1Json.canonical(Stage1Json.parse("[2,1]".toByteArray())).toString(Charsets.UTF_8))
        assertNotEquals(Stage1Json.sha(Stage1Json.canonical(Stage1Json.parse("{\"s\":\"é\"}".toByteArray()))),Stage1Json.sha(Stage1Json.canonical(Stage1Json.parse("{\"s\":\"e\\u0301\"}".toByteArray()))))
    }
    @Test fun identityAndAssignmentCannotDefaultOrChangeInsideChain() {
        val p=Fixture.packet();reject { p.link.identity.copy(assignmentVersion=0) };reject { p.link.identity.copy(assignmentVersion=Stage1Json.SAFE+1) }
        reject { codec.create(p.link.identity.copy(serialSource="PRODUCT"),"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","[]".toByteArray(),p.link) }
        reject { p.link.identity.copy(serialNumber=" SN") };reject { p.link.identity.copy(serialSource="product") }
        reject { codec.decode(p.originalBytes(),"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa") }
    }
    @Test fun missingNullUnknownFieldsTamperedHashesAndInvalidEventEnvelopeFailClosed() {
        val root=Stage1Json.obj(Stage1Json.parse(Fixture.raw()))
        listOf<(JsonObject)->Unit>({it.remove("prevPacketId")},{it.addProperty("displayName","bad")},{it.addProperty("chainHash","A".repeat(64))},
            {it.addProperty("chainHash","a".repeat(63)+"ａ")},{it.addProperty("prevChainHash","0".repeat(64))},
            {it["events"].asJsonArray[0].asJsonObject.addProperty("capturedAt",-1)},
            {it["events"].asJsonArray.add(it["events"].asJsonArray[0].deepCopy())}).forEach { mutate ->
            val copy=root.deepCopy();mutate(copy);reject { codec.decode(Fixture.wire(copy),Fixture.packet().link.identity.orderId) }
        }
        val next=Stage1Json.obj(Stage1Json.parse(Fixture.raw(2)));next.add("prevPacketId",JsonNull.INSTANCE)
        reject { codec.decode(Fixture.wire(next),Fixture.packet().link.identity.orderId) }
    }
    @Test fun semanticPayloadRejectionRemainsInImmutablePacketRatherThanFilledOrRemoved() {
        val p=Fixture.packet();val events=JsonArray().also { it.add(JsonObject().also { e ->
            e.addProperty("eventId",p.eventIds[0]);e.addProperty("capturedAt",1710000000000L);e.addProperty("type","UNKNOWN_EVENT");e.add("payload",JsonObject())
        }) }
        val encoded=codec.create(p.link.identity,p.link.packetId,Fixture.wire(events))
        assertEquals("UNKNOWN_EVENT",Stage1Json.obj(Stage1Json.parse(encoded.originalBytes()))["events"].asJsonArray[0].asJsonObject["type"].asString)
    }
    @Test fun fixtureSourceIsPinnedToActualBackendNotAnotherAuthority() {
        val source=javaClass.getResourceAsStream("/backend-d10fc1b/SOURCE.json")!!.use { JsonParser.parseString(it.readBytes().toString(Charsets.UTF_8)).asJsonObject }
        assertEquals("d10fc1bc116a6b952312beced2fd4fd8aa5a1d1b",source["commit"].asString)
        val bytes=javaClass.getResourceAsStream("/backend-d10fc1b/golden-vectors.json")!!.use { it.readBytes() }
        assertEquals(source["sha256"].asString,Stage1Json.sha(bytes))
        val gitBlob=java.security.MessageDigest.getInstance("SHA-1").digest(("blob ${bytes.size}\u0000").toByteArray()+bytes)
            .joinToString(""){"%02x".format(it.toInt() and 255)}
        assertEquals(source["gitBlobSha"].asString,gitBlob)
    }
}
