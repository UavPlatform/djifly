@file:OptIn(ExperimentalStage1Api::class)
package com.fuwaki.djifly.platform.eventstage1

import com.google.gson.*
import com.fuwaki.djifly.platform.eventstore.core.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit

/** Real loopback TCP + OkHttp + SQLite. Responses are local simulations, NOT live backend interoperability. */
class Stage1HttpTest {
    @get:Rule val temp=TemporaryFolder()
    private lateinit var server:MockWebServer
    private lateinit var store:EventPacketStore
    private lateinit var path:String
    private lateinit var uploader:Stage1Uploader
    private val auth=TestAuthentication()
    private var time=0L
    private val first get()=Fixture.packet(1) // noncanonical original bytes exercise exact retransmission
    @Before fun setup() {
        server=MockWebServer();server.start()
        path=temp.newFile("journal.sqlite").absolutePath
        store=EventPacketStore.initializeNew(JdbcSession(path),first.link.identity.recordingSourceId)
        store.beginOperation("work",first.link.identity.orderId)
        uploader=client()
    }
    @After fun teardown() { store.close();server.shutdown() }
    private fun client(pilot:Long=7, authentication:Stage1Authentication=auth)=Stage1Uploader(server.url("/api/"),
        OkHttpClient.Builder().connectTimeout(2,TimeUnit.SECONDS).readTimeout(2,TimeUnit.SECONDS).callTimeout(3,TimeUnit.SECONDS).build(),store,pilot,authentication,wallMillis={1794048000000L}){time}
    private fun respond(ack:JsonObject) { server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody(ack.toString())) }
    private fun row(p:Stage1Packet=first)=store.packet("work",p.link.packetId)
    private fun recorded()=server.takeRequest(2,TimeUnit.SECONDS)!!
    private fun reject(block:()->Unit) { try { block();fail("Expected binding rejection") } catch(_:Exception){} }
    private fun receipt(ack:JsonObject)=ack.getAsJsonObject("data").getAsJsonObject("receipt")

    @Test fun rawPostUsesActualPathBearerAndOriginalNoncanonicalBytesThenReleasesOnlyBody() {
        uploader.enqueue("work",first);respond(Fixture.ack(first))
        assertTrue(uploader.attempt("work",first.link.packetId) is Stage1UploadResult.Durable)
        val request=recorded()
        assertEquals("POST",request.method);assertEquals("/api/dispatch/pilot/orders/${first.link.identity.orderId}/event-packets",request.path)
        assertEquals("Bearer fixture-expired",request.getHeader("Authorization"));assertTrue(request.getHeader("Content-Type")!!.contains("application/json"))
        assertArrayEquals(first.originalBytes(),request.body.readByteArray())
        assertNull(row().body);assertNotNull(row().durableReceipt);assertTrue(row().summary.isNotEmpty())
        assertEquals(Stage1UploadResult.AlreadyDurable,uploader.attempt("work",first.link.packetId));assertEquals(1,server.requestCount)
    }
    @Test fun partialRejectionResultsAreFixedAndSurviveReleaseAndRestart() {
        val p=first;val events=Stage1Json.obj(Stage1Json.parse(p.originalBytes()))["events"].asJsonArray
        val other=events[0].deepCopy().asJsonObject;other.addProperty("eventId","bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
        other.addProperty("type","UNKNOWN_TYPE");other.add("payload",JsonObject());events.add(other)
        val packet=Stage1Codec().create(p.link.identity,p.link.packetId,Fixture.wire(events))
        uploader.enqueue("work",packet)
        val ack=Fixture.ack(packet);val rejected=receipt(ack).getAsJsonArray("results")[1].asJsonObject
        rejected.addProperty("outcome","REJECTED");rejected.addProperty("reason","UNKNOWN_EVENT_TYPE")
        respond(ack);assertTrue(uploader.attempt("work",packet.link.packetId) is Stage1UploadResult.Durable)
        val fixed=row(packet).durableReceipt!!
        store.close();store=EventPacketStore(JdbcSession(path),packet.link.identity.recordingSourceId);uploader=client()
        assertEquals(Stage1UploadResult.AlreadyDurable,uploader.attempt("work",packet.link.packetId))
        assertNull(row(packet).body);assertArrayEquals(fixed,row(packet).durableReceipt)
        assertTrue(fixed.toString(Charsets.UTF_8).contains("UNKNOWN_EVENT_TYPE"));assertEquals(1,server.requestCount)
    }
    @Test fun entirelyRejectedButFullyDurablePacketCanReleaseWithoutClaimingAcceptedFacts() {
        uploader.enqueue("work",first);respond(Fixture.ack(first,"REJECTED"))
        assertTrue(uploader.attempt("work",first.link.packetId) is Stage1UploadResult.Durable)
        assertNull(row().body);assertTrue(row().durableReceipt!!.toString(Charsets.UTF_8).contains("REJECTED"))
    }
    @Test fun wrongReceiptDigestsDurabilityIdsAndIncompleteResultsNeverReleaseOriginal() {
        uploader.enqueue("work",first)
        val mutations=listOf<(JsonObject)->Unit>(
            {receipt(it).addProperty("contentDigest","0".repeat(64))},
            {receipt(it).addProperty("chainHash","0".repeat(64))},
            {receipt(it).addProperty("packetId","aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")},
            {receipt(it).addProperty("durability","RECEIVED")},
            {receipt(it).add("results",JsonArray())},
            {receipt(it).getAsJsonArray("results")[0].asJsonObject.addProperty("eventId","aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")},
            {it.addProperty("success",false)},
            {receipt(it).remove("decidedAt")}
        )
        mutations.forEach { mutate ->
            val ack=Fixture.ack(first);mutate(ack);respond(ack)
            assertFalse(client().attempt("work",first.link.packetId) is Stage1UploadResult.Durable)
            assertArrayEquals(first.originalBytes(),row().body);assertNull(row().durableReceipt)
        }
    }
    @Test fun eventResultsCannotBeMissingDuplicatedReorderedUnknownOrInconsistentlyAttributed() {
        val events=Stage1Json.obj(Stage1Json.parse(first.originalBytes()))["events"].asJsonArray
        events.add(events[0].deepCopy().asJsonObject.also { it.addProperty("eventId","bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb") })
        val packet=Stage1Codec().create(first.link.identity,first.link.packetId,Fixture.wire(events));uploader.enqueue("work",packet)
        val mutations=listOf<(JsonArray)->Unit>(
            {it.remove(0)}, {it.set(1,it[0].deepCopy())}, {val a=it[0];it.set(0,it[1]);it.set(1,a)},
            {it[0].asJsonObject.addProperty("outcome","UNKNOWN")},
            {it[0].asJsonObject.addProperty("reason","INVALID_ON_ACCEPTED")},
            {it[0].asJsonObject.apply { addProperty("outcome","DUPLICATE");addProperty("reason","DUPLICATE");addProperty("sourcePacketId",packet.link.packetId) }}
        )
        mutations.forEach { mutate ->
            val ack=Fixture.ack(packet);mutate(receipt(ack).getAsJsonArray("results"));respond(ack)
            assertFalse(client().attempt("work",packet.link.packetId) is Stage1UploadResult.Durable)
            assertNull(row(packet).durableReceipt);assertArrayEquals(packet.originalBytes(),row(packet).body)
        }
    }
    @Test fun duplicateResultRetainsFixedOriginalSourceReferenceWithoutChangingRawHistory() {
        uploader.enqueue("work",first);val ack=Fixture.ack(first)
        receipt(ack).getAsJsonArray("results")[0].asJsonObject.apply {
            addProperty("outcome","DUPLICATE");addProperty("reason","DUPLICATE");addProperty("sourcePacketId","aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
        }
        respond(ack);assertTrue(uploader.attempt("work",first.link.packetId) is Stage1UploadResult.Durable)
        assertNull(row().body);assertTrue(row().durableReceipt!!.toString(Charsets.UTF_8).contains("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"))
    }
    @Test fun boundedOversizeMalformedAndInvalidUtf8SuccessBodiesCannotRelease() {
        uploader.enqueue("work",first)
        listOf("{\"large\":\"${"x".repeat(262145)}\"}".toByteArray(), "{not json".toByteArray(),
            byteArrayOf(0xc3.toByte(),0x28)).forEach { body ->
            server.enqueue(MockResponse().setBody(okio.Buffer().write(body)))
            assertFalse(client().attempt("work",first.link.packetId) is Stage1UploadResult.Durable)
            assertNull(row().durableReceipt);assertArrayEquals(first.originalBytes(),row().body)
        }
    }
    @Test fun sameHeightProgressHashMismatchPausesChainButKeepsValidFixedDurability() {
        uploader.enqueue("work",first);val ack=Fixture.ack(first)
        ack.getAsJsonObject("data").getAsJsonObject("progress").addProperty("contiguousHash","0".repeat(64))
        respond(ack);val result=uploader.attempt("work",first.link.packetId) as Stage1UploadResult.Durable
        assertTrue(result.chainConflict);assertNull(result.progress);assertNull(uploader.latestProgress(first.link.identity.chainId))
        assertNull(row().body);val next=Fixture.packet(2);uploader.enqueue("work",next)
        assertEquals(Stage1UploadResult.ChainForked,uploader.attempt("work",next.link.packetId));assertEquals(1,server.requestCount)
    }
    @Test fun noReceiptOrPlainHttp204CannotReleaseAndDoesNotBusyLoop() {
        uploader.enqueue("work",first);server.enqueue(MockResponse().setResponseCode(204))
        assertFalse(uploader.attempt("work",first.link.packetId) is Stage1UploadResult.Durable)
        assertArrayEquals(first.originalBytes(),row().body);assertNull(row().durableReceipt)
        uploader.attempt("work",first.link.packetId);assertEquals(1,server.requestCount)
    }
    @Test fun fixedReceiptIsCanonicalizedSeparatelyFromDynamicProgress() {
        val expected=Stage1Receipt.expected(first)
        val a=Stage1Receipt.acknowledgement(Fixture.wire(Fixture.ack(first,version=1)),expected)
        val b=Stage1Receipt.acknowledgement(Fixture.wire(Fixture.ack(first,version=2)),expected)
        assertArrayEquals(a.fixedReceipt,b.fixedReceipt);assertNotEquals(a.progress!!.validationVersion,b.progress!!.validationVersion)
    }
    @Test fun invalidDynamicProgressIsNotSynchronizationProofAndDoesNotRewriteDurableReceipt() {
        uploader.enqueue("work",first);val ack=Fixture.ack(first);ack.getAsJsonObject("data").add("progress",JsonObject())
        respond(ack);val result=uploader.attempt("work",first.link.packetId) as Stage1UploadResult.Durable
        assertNull(result.progress);assertNull(uploader.latestProgress(first.link.identity.chainId));assertNull(row().body);assertNotNull(row().durableReceipt)
    }
    @Test fun omittedNullableResultFieldsNormalizeLocallyWithoutMutatingPacketDigest() {
        val ack=Fixture.ack(first);val result=receipt(ack).getAsJsonArray("results")[0].asJsonObject
        result.remove("reason");result.remove("sourcePacketId")
        uploader.enqueue("work",first);respond(ack)
        assertTrue(uploader.attempt("work",first.link.packetId) is Stage1UploadResult.Durable)
        val fixed=Stage1Json.obj(Stage1Json.parse(row().durableReceipt!!))
        assertEquals(first.contentDigest,fixed["contentDigest"].asString)
        assertTrue(fixed["results"].asJsonArray[0].asJsonObject["reason"].isJsonNull)
    }
    @Test fun auth401CallsRecoveryOnceThenWaitsAndRetriesExactBytesWithNewToken() {
        uploader.enqueue("work",first);server.enqueue(MockResponse().setResponseCode(401));respond(Fixture.ack(first))
        val failed=uploader.attempt("work",first.link.packetId) as Stage1UploadResult.RetryAfter
        assertEquals(401,failed.status);assertEquals(1,auth.recoveries);assertNotNull(row().body)
        assertTrue(uploader.attempt("work",first.link.packetId) is Stage1UploadResult.NotDue)
        assertEquals(1,server.requestCount);assertEquals(1,auth.recoveries)
        time=failed.delayMillis;assertTrue(uploader.attempt("work",first.link.packetId) is Stage1UploadResult.Durable)
        val a=recorded();val b=recorded();assertArrayEquals(a.body.readByteArray(),b.body.readByteArray())
        assertEquals("Bearer fixture-expired",a.getHeader("Authorization"));assertEquals("Bearer fixture-refreshed",b.getHeader("Authorization"))
    }
    @Test fun failedAuthenticationRecoveryStaysPausedUntilExplicitAuthEvent() {
        uploader.enqueue("work",first);auth.recoveryWorks=false;server.enqueue(MockResponse().setResponseCode(401))
        uploader.attempt("work",first.link.packetId);time=1000000
        repeat(3){assertEquals(Stage1UploadResult.AuthenticationRequired,uploader.attempt("work",first.link.packetId))}
        assertEquals(1,server.requestCount);assertEquals(1,auth.recoveries);assertNotNull(row().body)
        auth.token="fixture-new";uploader.authenticationChanged();respond(Fixture.ack(first))
        assertTrue(uploader.attempt("work",first.link.packetId) is Stage1UploadResult.Durable)
    }
    @Test fun throwingAuthRecoveryFailsPausedInsteadOfCreatingAnUnboundedResendLoop() {
        val broken=object:Stage1Authentication by auth { override fun recover():Boolean=throw IllegalStateException("Simulated auth failure") }
        uploader=client(authentication=broken);uploader.enqueue("work",first)
        server.enqueue(MockResponse().setResponseCode(401))
        assertTrue(uploader.attempt("work",first.link.packetId) is Stage1UploadResult.RetryAfter)
        time=10000;assertEquals(Stage1UploadResult.AuthenticationRequired,uploader.attempt("work",first.link.packetId))
        assertNotNull(row().body);assertEquals(1,server.requestCount)
    }
    @Test fun slowHttpResponseCannotBlockConcurrentLocalPacketCollection() {
        val release=java.util.concurrent.CountDownLatch(1)
        server.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:okhttp3.mockwebserver.RecordedRequest):MockResponse {
                check(release.await(2,TimeUnit.SECONDS));return MockResponse().setBody(Fixture.ack(first).toString())
            }
        }
        val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
        uploader.enqueue("work",first)
        try {
            val upload=pool.submit<Stage1UploadResult>{uploader.attempt("work",first.link.packetId)}
            recorded()
            pool.submit { uploader.enqueue("work",Fixture.packet(2)) }.get(1,TimeUnit.SECONDS)
            assertNotNull(row(Fixture.packet(2)).body)
            release.countDown();assertTrue(upload.get(3,TimeUnit.SECONDS) is Stage1UploadResult.Durable)
        } finally {release.countDown();pool.shutdownNow()}
    }
    @Test fun timeoutServerAndRateLimitErrorsKeepBytesAndEnforceBackoff() {
        uploader.enqueue("work",first)
        listOf(408,500,502,503,429).forEach { status ->
            server.enqueue(MockResponse().setResponseCode(status).also { if(status==429)it.setHeader("Retry-After","7") })
            val result=uploader.attempt("work",first.link.packetId) as Stage1UploadResult.RetryAfter
            if(status==429)assertTrue(result.delayMillis>=7000)
            assertArrayEquals(first.originalBytes(),row().body);assertNull(row().durableReceipt)
            val count=server.requestCount;assertTrue(uploader.attempt("work",first.link.packetId) is Stage1UploadResult.NotDue)
            assertEquals(count,server.requestCount);time+=result.delayMillis
        }
        repeat(5){assertArrayEquals(first.originalBytes(),recorded().body.readByteArray())}
    }
    @Test fun retryAfterAppliesToEndpointNotOnlyTheOldPacketAndHugeValuesDoNotOverflow() {
        val next=Fixture.packet(2);uploader.enqueue("work",first);uploader.enqueue("work",next)
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After","999999999999999999999999999999"))
        val result=uploader.attempt("work",first.link.packetId) as Stage1UploadResult.RetryAfter
        // A huge Retry-After is clamped: an overflow sentinel would pause this endpoint forever with no recovery path.
        assertEquals(3_600_000L,result.delayMillis)
        assertTrue(uploader.attempt("work",next.link.packetId) is Stage1UploadResult.NotDue);assertEquals(1,server.requestCount)
        assertNotNull(row(next).body)
    }
    @Test fun httpDateRetryAfterIsConvertedToMonotonicDeadlineWithoutWaitingInTest() {
        uploader.enqueue("work",first)
        val date=java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'",java.util.Locale.US).apply { timeZone=java.util.TimeZone.getTimeZone("GMT") }
            .format(java.util.Date(1794048007000L))
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After",date))
        assertEquals(7000L,(uploader.attempt("work",first.link.packetId) as Stage1UploadResult.RetryAfter).delayMillis)
        time=6999;assertEquals(Stage1UploadResult.NotDue(1),uploader.attempt("work",first.link.packetId))
        time=7000;respond(Fixture.ack(first));assertTrue(uploader.attempt("work",first.link.packetId) is Stage1UploadResult.Durable)
    }
    @Test fun invalidRetryAfterFallsBackToNonzeroExponentialDelay() {
        uploader.enqueue("work",first);server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After","-1"))
        assertEquals(1000L,(uploader.attempt("work",first.link.packetId) as Stage1UploadResult.RetryAfter).delayMillis)
        assertTrue(uploader.attempt("work",first.link.packetId) is Stage1UploadResult.NotDue)
    }
    @Test fun retryAfterZero503CannotTriggerHiddenOkhttpFollowup() {
        uploader.enqueue("work",first)
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After","0"))
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After","0"))
        val result=uploader.attempt("work",first.link.packetId) as Stage1UploadResult.RetryAfter
        assertEquals(503,result.status);assertEquals(1,server.requestCount)
        assertArrayEquals(first.originalBytes(),row().body)
    }
    @Test fun networkFailurePreservesOriginalWithoutAutomaticOkhttpResendLoop() {
        uploader.enqueue("work",first);server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        val result=uploader.attempt("work",first.link.packetId)
        assertTrue(result is Stage1UploadResult.RetryAfter);assertNotNull(row().body);assertNull(row().durableReceipt)
        assertEquals(1,server.requestCount);assertTrue(uploader.attempt("work",first.link.packetId) is Stage1UploadResult.NotDue)
    }
    @Test fun redirectCannotSendBearerOrOriginalPacketToAnotherHost() {
        uploader.enqueue("work",first);server.enqueue(MockResponse().setResponseCode(307).setHeader("Location","https://example.invalid/steal"))
        assertEquals(Stage1UploadResult.HttpRejected(307),uploader.attempt("work",first.link.packetId))
        assertEquals(1,server.requestCount);assertNotNull(row().body)
    }
    @Test fun laterPacketAcknowledgesBeforeOldHoleAndOldRetryDoesNotBlockNewPacket() {
        val next=Fixture.packet(2);uploader.enqueue("work",first);uploader.enqueue("work",next)
        server.enqueue(MockResponse().setResponseCode(500));uploader.attempt("work",first.link.packetId)
        respond(Fixture.ack(next,version=1,contiguous=0,highest=2))
        assertTrue(uploader.attempt("work",next.link.packetId) is Stage1UploadResult.Durable)
        assertNull(row(next).body);assertNotNull(row().body);assertEquals(listOf(first.link.packetId),store.pending("work").map { it.packetId })
        val progress=uploader.latestProgress(next.link.identity.chainId)!!;assertEquals(0L,progress.contiguousHeight);assertEquals(2L,progress.highestReceivedHeight)
        assertEquals(listOf(Stage1MissingRange(1,1)),progress.missingRanges)
        time=1000;val ack=Fixture.ack(first,version=2,contiguous=2,highest=2)
        ack.getAsJsonObject("data").getAsJsonObject("progress").addProperty("contiguousHash",next.link.chainHash)
        respond(ack);assertTrue(uploader.attempt("work",first.link.packetId) is Stage1UploadResult.Durable)
        assertEquals(2L,uploader.latestProgress(first.link.identity.chainId)!!.contiguousHeight)
        assertTrue(store.pending("work").isEmpty());assertEquals(3,server.requestCount)
    }
    @Test fun staleDynamicProgressNeverReplacesNewerVersionWhileEachReceiptRemainsIndependentlyDurable() {
        val next=Fixture.packet(2);uploader.enqueue("work",first);uploader.enqueue("work",next)
        respond(Fixture.ack(next,version=5,contiguous=0,highest=2))
        assertTrue(uploader.attempt("work",next.link.packetId) is Stage1UploadResult.Durable)
        respond(Fixture.ack(first,version=4,contiguous=0,highest=2))
        assertTrue(uploader.attempt("work",first.link.packetId) is Stage1UploadResult.Durable)
        assertEquals(5L,uploader.latestProgress(first.link.identity.chainId)!!.validationVersion)
        assertNull(row().body);assertNull(row(next).body)
    }
    @Test fun conflictStopsOnlyAffectedChainWhileCollectionAndOtherAirframeRemainPossible() {
        val next=Fixture.packet(2);uploader.enqueue("work",first);uploader.enqueue("work",next)
        server.enqueue(MockResponse().setResponseCode(409).setBody("{\"success\":false,\"code\":409,\"errorCode\":\"EVENT_PACKET_CONFLICT\",\"message\":\"HEIGHT_FORK\"}"))
        assertEquals(Stage1UploadResult.HttpRejected(409),uploader.attempt("work",first.link.packetId))
        assertEquals(Stage1UploadResult.ChainForked,uploader.attempt("work",next.link.packetId));assertNotNull(row(next).body)
        val other=Fixture.packet(3);uploader.enqueue("work",other);respond(Fixture.ack(other))
        assertTrue(uploader.attempt("work",other.link.packetId) is Stage1UploadResult.Durable);assertEquals(2,server.requestCount)
    }
    @Test fun crashAfterReceiptCommitBeforeReleaseRecoversWithoutSendingAgain() {
        uploader.enqueue("work",first)
        val fixed=Stage1Receipt.acknowledgement(Fixture.wire(Fixture.ack(first)),Stage1Receipt.expected(first)).fixedReceipt
        store.acknowledgeDurable(first.link.packetId,fixed);assertNotNull(row().body)
        store.close();store=EventPacketStore(JdbcSession(path),first.link.identity.recordingSourceId);uploader=client()
        assertEquals(Stage1UploadResult.AlreadyDurable,uploader.attempt("work",first.link.packetId))
        assertNull(row().body);assertEquals(0,server.requestCount)
    }
    @Test fun pendingBytesSurviveRestartAndRetainRawLocalFingerprintDistinctFromContentDigest() {
        uploader.enqueue("work",first);val summary=row().summary
        store.close();store=EventPacketStore(JdbcSession(path),first.link.identity.recordingSourceId);uploader=client()
        assertArrayEquals(first.originalBytes(),row().body);assertArrayEquals(summary,row().summary)
        respond(Fixture.ack(first));assertTrue(uploader.attempt("work",first.link.packetId) is Stage1UploadResult.Durable)
        assertArrayEquals(first.originalBytes(),recorded().body.readByteArray())
    }
    @Test fun accountAssignmentSourceAndOperationBoundariesCannotBeSilentlyRebound() {
        uploader.enqueue("work",first);auth.pilot=8
        reject { uploader.attempt("work",first.link.packetId) }
        reject { client(pilot=8).enqueue("work",Fixture.packet(2)) }
        auth.pilot=7
        val original=first.link.identity
        val otherSource=Stage1Codec().create(original.copy(recordingSourceId="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
            "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",Fixture.wire(Stage1Json.obj(Stage1Json.parse(first.originalBytes()))["events"]))
        reject { uploader.enqueue("work",otherSource) }
        store.beginOperation("other",original.orderId)
        reject { uploader.attempt("other",first.link.packetId) };reject { uploader.attempt("missing",first.link.packetId) }
        assertEquals(0,server.requestCount);assertNotNull(row().body)
    }
    @Test fun firstWriterGuardIsAtomicAndCannotOverwriteExistingOperationAttribution() {
        val empty=store.firstSummary("work");assertNull(empty);uploader.enqueue("work",first)
        val next=Fixture.packet(2)
        reject { store.appendGuarded("work",PacketInput(next.link.packetId,next.link.identity.serialNumber,next.originalBytes(),byteArrayOf(1)),empty) }
        assertEquals(1,store.pending("work").size)
    }
    @Test fun receiptCommitFailureRollsBackAndRetriesOriginalWithoutMarkingValidResponseMalformed() {
        uploader.enqueue("work",first);store.close()
        val sql=FailingSql(JdbcSession(path));store=EventPacketStore(sql,first.link.identity.recordingSourceId);uploader=client()
        sql.failPrefix="UPDATE packet SET receipt=";respond(Fixture.ack(first))
        reject { uploader.attempt("work",first.link.packetId) }
        assertNull(row().durableReceipt);assertArrayEquals(first.originalBytes(),row().body)
        sql.failPrefix=null;respond(Fixture.ack(first));assertTrue(uploader.attempt("work",first.link.packetId) is Stage1UploadResult.Durable)
        assertArrayEquals(recorded().body.readByteArray(),recorded().body.readByteArray());assertNull(row().body)
    }
    @Test fun releaseTransactionFailureKeepsBodyAndCommittedReceiptThenRecoversWithoutHttp() {
        uploader.enqueue("work",first);store.close()
        val sql=FailingSql(JdbcSession(path));store=EventPacketStore(sql,first.link.identity.recordingSourceId);uploader=client()
        sql.failPrefix="UPDATE packet SET body=";respond(Fixture.ack(first))
        reject { uploader.attempt("work",first.link.packetId) }
        assertNotNull(row().durableReceipt);assertArrayEquals(first.originalBytes(),row().body)
        sql.failPrefix=null;assertEquals(Stage1UploadResult.AlreadyDurable,uploader.attempt("work",first.link.packetId))
        assertNull(row().body);assertEquals(1,server.requestCount)
    }
    @Test fun forkedDynamicProgressPausesUploadsButNotLocalCollection() {
        uploader.enqueue("work",first);respond(Fixture.ack(first,integrity="FORKED"))
        assertTrue(uploader.attempt("work",first.link.packetId) is Stage1UploadResult.Durable)
        val next=Fixture.packet(2);uploader.enqueue("work",next)
        assertEquals(Stage1UploadResult.ChainForked,uploader.attempt("work",next.link.packetId));assertNotNull(row(next).body)
        assertEquals(1,server.requestCount)
    }
    @Test fun endpointAndDedicatedClientRestrictionsCannotBeBypassedAccidentally() {
        val http=OkHttpClient()
        reject { Stage1Uploader("http://example.invalid/api/".toHttpUrl(),http,store,7,auth) }
        reject { Stage1Uploader(server.url("/api/?query=yes"),http,store,7,auth) }
        reject { Stage1Uploader(server.url("/api"),http,store,7,auth) }
        reject { Stage1Uploader(server.url("/api/"),http.newBuilder().addInterceptor { it.proceed(it.request()) }.build(),store,7,auth) }
        assertEquals(0,server.requestCount)
    }
    @Test fun cleartextRequiresExplicitOptInAndDefaultStaysHttps() {
        // The production debug endpoint shape: non-loopback plain HTTP, no TLS listener on the backend.
        val cleartext="http://120.77.170.101:8888/api/".toHttpUrl()
        // Default stays fail-closed: construction alone must reject it (this is the constructor that killed the process).
        reject { Stage1Uploader(cleartext,OkHttpClient(),store,7,auth) }
        // opt-in accepts the same URL; no request is issued, so this stays offline and deterministic.
        val optedIn=Stage1Uploader(cleartext,OkHttpClient(),store,7,auth,allowCleartextHttp=true)
        optedIn.enqueue("work",first)
        assertNotNull(row().body)
        assertEquals(0,server.requestCount)
    }
    @Test fun inFlightRequestDoesNotBlockProgressReadsOrOtherPackets() {
        val next=Fixture.packet(2);uploader.enqueue("work",first);uploader.enqueue("work",next)
        val release=java.util.concurrent.CountDownLatch(1)
        val seen=java.util.concurrent.atomic.AtomicInteger(0)
        server.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:okhttp3.mockwebserver.RecordedRequest):MockResponse {
                if(seen.incrementAndGet()==1) { check(release.await(5,TimeUnit.SECONDS));return MockResponse().setBody(Fixture.ack(first).toString()) }
                return MockResponse().setBody(Fixture.ack(next).toString())
            }
        }
        val pool=java.util.concurrent.Executors.newFixedThreadPool(3)
        try {
            val slow=pool.submit<Stage1UploadResult>{uploader.attempt("work",first.link.packetId)}
            while(seen.get()==0) Thread.sleep(5)
            // These two local-only paths must not wait for the in-flight response.
            assertNull(pool.submit<Stage1Progress?>{uploader.latestProgress(first.link.identity.chainId)}.get(500,TimeUnit.MILLISECONDS))
            assertTrue(pool.submit<Stage1UploadResult>{uploader.attempt("work",next.link.packetId)}.get(2,TimeUnit.SECONDS) is Stage1UploadResult.Durable)
            assertNotNull(uploader.latestProgress(next.link.identity.chainId))
            release.countDown();assertTrue(slow.get(3,TimeUnit.SECONDS) is Stage1UploadResult.Durable)
        } finally {release.countDown();pool.shutdownNow()}
    }
    @Test fun changeAssignmentConflictParksOnlyThatPacketAndCanBeClearedExplicitly() {
        val next=Fixture.packet(2);uploader.enqueue("work",first);uploader.enqueue("work",next)
        server.enqueue(MockResponse().setResponseCode(409).setBody("{\"success\":false,\"code\":409,\"errorCode\":\"DISPATCH_ASSIGNMENT_CHANGED\",\"message\":\"reassign\"}"))
        assertEquals(Stage1UploadResult.HttpRejected(409),uploader.attempt("work",first.link.packetId))
        // A 409 that is not a chain conflict must not freeze the rest of the operation.
        respond(Fixture.ack(next));assertTrue(uploader.attempt("work",next.link.packetId) is Stage1UploadResult.Durable)
        // The parked packet reports an explicit intervention instead of pretending to be a malformed response.
        val parked=uploader.attempt("work",first.link.packetId) as Stage1UploadResult.InterventionRequired
        assertEquals(409,parked.status);assertEquals("DISPATCH_ASSIGNMENT_CHANGED",parked.errorCode)
        assertEquals(2,server.requestCount)
        uploader.clearIntervention(first.link.packetId)
        respond(Fixture.ack(first));assertTrue(uploader.attempt("work",first.link.packetId) is Stage1UploadResult.Durable)
        assertEquals(3,server.requestCount)
        assertEquals(listOf(1,2),listOf(1,2))
    }
    @Test fun unusableSuccessBodyParksOnlyThatPacketUntilExplicitIntervention() {
        uploader.enqueue("work",first)
        server.enqueue(MockResponse().setBody("<html>captive portal</html>"))
        assertTrue(uploader.attempt("work",first.link.packetId) is Stage1UploadResult.InvalidResponse)
        assertArrayEquals(first.originalBytes(),row().body);assertEquals(1,server.requestCount)
        // No silent resend loop, and the reason is preserved rather than reported as a transport retry.
        val parked=uploader.attempt("work",first.link.packetId) as Stage1UploadResult.InterventionRequired
        assertTrue(parked.reason.isNotBlank());assertEquals(1,server.requestCount)
        respond(Fixture.ack(first));uploader.clearIntervention(first.link.packetId)
        assertTrue(uploader.attempt("work",first.link.packetId) is Stage1UploadResult.Durable)
        assertEquals(2,server.requestCount)
    }
    @Test fun chainForkPauseIsLiftedOnlyByExplicitAction() {
        val next=Fixture.packet(2);uploader.enqueue("work",first);uploader.enqueue("work",next)
        respond(Fixture.ack(first,integrity="FORKED"))
        assertTrue(uploader.attempt("work",first.link.packetId) is Stage1UploadResult.Durable)
        assertEquals(Stage1UploadResult.ChainForked,uploader.attempt("work",next.link.packetId))
        uploader.clearChainFork(next.link.identity.chainId)
        respond(Fixture.ack(next));assertTrue(uploader.attempt("work",next.link.packetId) is Stage1UploadResult.Durable)
        assertEquals(2,server.requestCount)
    }
    @Test fun multipleAirframeChainsShareOneOperationWithoutRebinding() {
        val other=Stage1Codec().create(first.link.identity.copy(chainId="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",serialNumber="OTHERSN",serialSource="PRODUCT"),
            "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",Fixture.wire(Stage1Json.obj(Stage1Json.parse(first.originalBytes()))["events"]))
        uploader.enqueue("work",first);uploader.enqueue("work",other)
        assertEquals(listOf("FC30SN1","OTHERSN"),store.pending("work").map{it.serialNumber})
        respond(Fixture.ack(other));assertTrue(uploader.attempt("work",other.link.packetId) is Stage1UploadResult.Durable)
        respond(Fixture.ack(first));assertTrue(uploader.attempt("work",first.link.packetId) is Stage1UploadResult.Durable)
        assertTrue(store.pending("work").isEmpty())
    }
    @Test fun unusableProgressStillProvesDurabilityButIsFlagged() {
        uploader.enqueue("work",first);val ack=Fixture.ack(first)
        ack.getAsJsonObject("data").addProperty("progress","not-an-object")
        respond(ack);val result=uploader.attempt("work",first.link.packetId) as Stage1UploadResult.Durable
        assertTrue(result.progressInvalid);assertNull(result.progress);assertNull(row().body)
        assertNotNull(row().durableReceipt);assertNull(uploader.latestProgress(first.link.identity.chainId))
    }
    @Test fun fixedReceiptCannotBeOverwrittenAfterDurableRelease() {
        uploader.enqueue("work",first);respond(Fixture.ack(first));uploader.attempt("work",first.link.packetId)
        val old=row().durableReceipt!!
        reject { store.acknowledgeDurable(first.link.packetId,"different fixed result".toByteArray()) }
        assertArrayEquals(old,row().durableReceipt);assertNull(row().body)
    }
}
