package com.fuwaki.djifly.platform.telemetry

import com.fuwaki.djifly.platform.network.generated.ApiDispatchOrderVO
import com.fuwaki.djifly.platform.network.generated.ApiFlightConnectDTO
import com.fuwaki.djifly.platform.network.generated.ApiFlightConnectionVO
import com.fuwaki.djifly.platform.network.generated.ApiFlightSampleBatchDTO
import com.fuwaki.djifly.sdk.FlightDataState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** ADR-0009：离线先记连机、采样不碰网络、联网后登记并补传、结束失败重试并提示、缓存满提示。 */
class FlightRecorderTest {
    @get:Rule val tmp = TemporaryFolder()

    private class FakeApi : FlightApi {
        var online = false
        var closeCode: Int? = 200
        var connectCode = 200
        val log = mutableListOf<String>()
        val connects = mutableListOf<ApiFlightConnectDTO>()
        val uploads = mutableListOf<Pair<String, Int>>()
        val closes = mutableListOf<String>()
        var calls = 0
        override suspend fun connect(orderId: String, request: ApiFlightConnectDTO): ApiReply<ApiFlightConnectionVO> {
            calls++; if (!online) return ApiReply(null)
            log += "connect:${request.serialNumber}"
            if (connectCode != 200) return ApiReply(connectCode)
            connects += request; return ApiReply(200, ApiFlightConnectionVO(connectionId = "srv-${connects.size}"))
        }
        override suspend fun upload(orderId: String, connectionId: String, batch: ApiFlightSampleBatchDTO): Int? {
            calls++; if (!online) return null
            uploads += connectionId to batch.samples.size; return 200
        }
        override suspend fun close(orderId: String, connectionId: String): Int? {
            calls++; if (!online) return null
            log += "close:$connectionId"
            closes += connectionId; return closeCode
        }
    }

    private val order = ApiDispatchOrderVO(orderId = "o1", stage = "IN_TRANSIT", assignmentVersion = 3,
        fields = mapOf("weightMax" to "12.5"))
    private fun snap(connected: Boolean = true, serial: String = "SN1") =
        FlightSnapshot(connected, order, serial, "FC30", "DJI FlyCart 30", FlightDataState(latitude = 28.2, longitude = 113.0))

    private fun recorder(capacity: Int = 100) = FlightRecorder(
        FileTelemetryQueue(tmp.root.resolve("q.jsonl"), capacity), FileConnectionJournal(tmp.root.resolve("c.jsonl")))

    @Test fun offlineTakeoffRecordsConnectionLocallyThenUploadsWithStartTime() = runBlocking {
        val api = FakeApi(); val r = recorder()
        r.record(10_000, snap()); r.record(12_000, snap())
        assertEquals(0, api.calls)                       // 采样从不访问网络
        val local = r.active()!!
        assertEquals("SN1", local.serialNumber); assertEquals(10_000L, local.startedAt); assertNull(local.serverId)
        r.sync(api)                                      // 断网：保留，不报错丢弃
        assertEquals(2, r.status().pendingSamples)
        assertNotNull(r.status().error)
        api.online = true
        r.sync(api)
        assertEquals(10_000L, api.connects.single().startedAt)
        assertEquals(listOf("srv-1" to 2), api.uploads)
        assertEquals(0, r.status().pendingSamples); assertNull(r.status().error)
        r.record(14_000, snap(connected = false))       // 断开 → 本机记结束，下一轮上报
        r.sync(api)
        assertEquals(listOf("srv-1"), api.closes)
    }

    @Test fun closeFailureIsRetriedAndSurfaced() = runBlocking {
        val api = FakeApi().apply { online = true; closeCode = 503 }; val r = recorder()
        r.record(1_000, snap()); r.sync(api)
        r.record(3_000, snap(serial = "SN2"))            // 换机：旧连接结束，新连接开始
        repeat(FlightRecorder.CLOSE_WARN_AFTER) { r.sync(api) }
        assertEquals(FlightRecorder.CLOSE_WARN_AFTER, api.closes.count { it == "srv-1" })
        assertTrue(r.status().error!!.contains("结束连机上报失败"))
        api.closeCode = 200; r.sync(api)
        assertNull(r.status().error)
        r.sync(api)
        assertEquals(FlightRecorder.CLOSE_WARN_AFTER + 1, api.closes.count { it == "srv-1" }) // 成功后不再重复
    }

    @Test fun samplesCarryWaybillPayloadAndFullCacheWarnsPilot() = runBlocking {
        val q = FileTelemetryQueue(tmp.root.resolve("full.jsonl"), capacity = 2)
        val r = FlightRecorder(q, FileConnectionJournal(tmp.root.resolve("j.jsonl")))
        r.record(1_000, snap()); r.record(3_000, snap())
        assertEquals(12.5, q.peek(1).single().sample.payloadKg!!, 0.0)
        assertTrue(r.status().cacheWarning!!.contains("即将用满"))
        r.record(5_000, snap())
        assertTrue(r.status().cacheWarning!!.contains("已满"))
        assertEquals(2, q.size())
    }

    @Test fun restartEndsUnfinishedConnectionSoSequencesNeverCollide() {
        val journalFile = tmp.root.resolve("r.jsonl")
        FlightRecorder(FileTelemetryQueue(tmp.root.resolve("rq.jsonl")), FileConnectionJournal(journalFile)).record(1_000, snap())
        val again = FlightRecorder(FileTelemetryQueue(tmp.root.resolve("rq.jsonl")), FileConnectionJournal(journalFile))
        assertNull(again.active())
        again.record(5_000, snap())
        assertEquals(2, FileConnectionJournal(journalFile).all().size)
    }

    @Test fun lateRegistrationConflictKeepsOfflineSamplesAndRetries() = runBlocking {
        val api = FakeApi(); val r = recorder()
        r.record(1_000, snap()); r.record(3_000, snap()); r.record(5_000, snap(connected = false)) // 整趟离线
        api.online = true; api.connectCode = 409
        r.sync(api)
        assertEquals(2, r.status().pendingSamples)          // 409 不丢本机采样
        assertNull(r.journalFor("SN1").rejected)
        assertTrue(r.status().error!!.contains("409"))
        api.connectCode = 200
        r.sync(api)
        assertEquals(1_000L, api.connects.single().startedAt)
        assertEquals(listOf("srv-1" to 2), api.uploads)
        assertEquals(0, r.status().pendingSamples)
    }

    @Test fun persistentConflictIsGivenUpOnlyAfterLimit() = runBlocking {
        val api = FakeApi().apply { online = true; connectCode = 409 }; val r = recorder()
        r.record(1_000, snap())
        repeat(FlightRecorder.CONNECT_CONFLICT_LIMIT - 1) { r.sync(api) }
        assertNull(r.journalFor("SN1").rejected)
        r.sync(api)
        assertNotNull(r.journalFor("SN1").rejected)
    }

    @Test fun syncEndsOldConnectionBeforeRegisteringNewOne() = runBlocking {
        val api = FakeApi().apply { online = true }; val r = recorder()
        r.record(1_000, snap()); r.sync(api)              // srv-1 for SN1
        api.online = false
        r.record(3_000, snap(serial = "SN2"))             // 换机：SN1 本机结束，SN2 本机开始（离线）
        api.online = true; api.log.clear()
        r.sync(api)
        assertEquals(listOf("close:srv-1", "connect:SN2"), api.log)
    }

    @Test fun restartClosesDanglingConnectionAtLastSampleTime() {
        val journalFile = tmp.root.resolve("d.jsonl"); val queueFile = tmp.root.resolve("dq.jsonl")
        FlightRecorder(FileTelemetryQueue(queueFile), FileConnectionJournal(journalFile)).apply {
            record(1_000, snap()); record(3_000, snap()); record(5_000, snap())
        }
        FlightRecorder(FileTelemetryQueue(queueFile), FileConnectionJournal(journalFile))   // 进程重启（几小时后）
        assertEquals(5_000L, FileConnectionJournal(journalFile).all().single().endedAt)
    }

    @Test fun restartUsesLastSampleTimeEvenAfterSamplesWereUploaded() = runBlocking {
        val journalFile = tmp.root.resolve("u.jsonl"); val queueFile = tmp.root.resolve("uq.jsonl")
        val api = FakeApi().apply { online = true }
        FlightRecorder(FileTelemetryQueue(queueFile), FileConnectionJournal(journalFile)).apply {
            record(1_000, snap()); record(7_000, snap()); sync(api)
        }
        FlightRecorder(FileTelemetryQueue(queueFile), FileConnectionJournal(journalFile))
        assertEquals(7_000L, FileConnectionJournal(journalFile).all().single().endedAt)
    }

    private fun FlightRecorder.journalFor(serial: String) = journalForTest().all().single { it.serialNumber == serial }
}
