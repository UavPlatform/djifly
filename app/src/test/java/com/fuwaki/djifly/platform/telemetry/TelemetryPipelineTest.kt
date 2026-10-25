package com.fuwaki.djifly.platform.telemetry

import com.fuwaki.djifly.platform.dispatch.DispatchStages
import com.fuwaki.djifly.platform.network.generated.ApiDispatchOrderVO
import com.fuwaki.djifly.platform.network.generated.ApiFlightSampleDTO
import com.fuwaki.djifly.sdk.FlightDataState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** ADR-0009：采样不填 0、间隔夹取、持久缓存、断网补传与去留判定、机型映射、当前订单选择。 */
class TelemetryPipelineTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun intervalDefaultsToTwoSecondsAndNeverBelowOne() {
        assertEquals(2_000L, TelemetryInterval.clamp(null))
        assertEquals(2_000L, TelemetryInterval.clamp(0))
        assertEquals(1_000L, TelemetryInterval.clamp(200))
        assertEquals(5_000L, TelemetryInterval.clamp(5_000))
    }

    @Test fun missingValuesAreNullNotZeroAndSequenceIncreases() {
        val sampler = TelemetrySampler()
        val empty = sampler.sample(FlightDataState(), 1_000L)
        assertEquals(0L, empty.sequence)
        assertNull(empty.latitude); assertNull(empty.longitude); assertNull(empty.altitude)
        assertNull(empty.speed); assertNull(empty.battery); assertNull(empty.flying); assertNull(empty.payloadKg)
        val half = sampler.sample(FlightDataState(latitude = 28.2), 2_000L)
        assertEquals(1L, half.sequence); assertNull(half.latitude)
        val full = sampler.sample(FlightDataState(latitude = 28.2, longitude = 113.0, altitude = 0.0, speed = 3.0,
            batteryPercentage = 120, batteryKnown = true, isFlying = true), 3_000L, payloadKg = 12.5)
        assertEquals(28.2, full.latitude!!, 0.0); assertEquals(0.0, full.altitude!!, 0.0)
        assertEquals(100, full.battery); assertEquals(true, full.flying); assertEquals(12.5, full.payloadKg!!, 0.0)
        assertNull(sampler.sample(FlightDataState(speed = Double.NaN), 4_000L, payloadKg = -1.0).speed)
    }

    private fun item(seq: Long, conn: String = "c1") = QueuedSample("o1", conn, ApiFlightSampleDTO(sequence = seq, capturedAt = 1_000L + seq))

    @Test fun fileQueueAppendsOnlyAndRefusesWhenFullInsteadOfDropping() {
        val file = tmp.root.resolve("q/pending.jsonl")
        val q = FileTelemetryQueue(file, capacity = 3)
        q.add(item(0))
        val firstLine = file.readLines().single()
        assertTrue(q.add(item(1))); assertTrue(q.add(item(2)))
        assertEquals(firstLine, file.readLines().first()) // 追加写：已写的行不被改写
        assertEquals(3, file.readLines().size)
        assertFalse(q.add(item(3)))                        // 满了：拒绝新条目，不丢旧数据
        assertEquals(listOf(0L, 1L, 2L), q.peek(10).map { it.sample.sequence })
        q.removeHead(2)
        assertEquals(3, file.readLines().size)              // 删除只记偏移，不重写
        val reopened = FileTelemetryQueue(file, capacity = 3)
        assertEquals(listOf(2L), reopened.peek(10).map { it.sample.sequence })
        reopened.removeHead(1)
        assertEquals(0, FileTelemetryQueue(file).size())
    }

    @Test fun uploaderKeepsSamplesOnRetryAndBatchesPerConnection() = runBlocking {
        val q = FileTelemetryQueue(tmp.root.resolve("u.jsonl"))
        listOf(item(0), item(1), item(2, "c2")).forEach { q.add(it) }
        val calls = mutableListOf<Pair<String, Int>>()
        var outcome: UploadOutcome = UploadOutcome.Retry
        val uploader = TelemetryUploader(q, BatchSender { _, conn, samples -> calls += conn to samples.size; outcome })
        assertEquals(0, uploader.flush())
        assertEquals(3, q.size())
        outcome = UploadOutcome.Sent
        assertEquals(3, uploader.flush())
        assertEquals(listOf("c1" to 2, "c1" to 2, "c2" to 1), calls)
        assertEquals(0, q.size())
        q.add(item(5)); outcome = UploadOutcome.Discard
        assertEquals(0, uploader.flush()); assertEquals(0, q.size())
    }

    @Test fun httpCodesDecideRetryOrDiscard() {
        assertEquals(UploadOutcome.Sent, TelemetryHttp.outcome(200))
        assertEquals(UploadOutcome.Retry, TelemetryHttp.outcome(503))
        assertEquals(UploadOutcome.Retry, TelemetryHttp.outcome(401))
        assertEquals(UploadOutcome.Discard, TelemetryHttp.outcome(409))
    }

    @Test fun modelMappingAndSerialValidation() {
        assertEquals("FC30", DroneModelMapper.modelCode("DJI_FLYCART_30"))
        assertEquals("MAVIC_3_ENTERPRISE", DroneModelMapper.modelCode("mavic_3_enterprise"))
        assertNull(DroneModelMapper.modelCode("UNKNOWN"))
        assertNull(DroneModelMapper.validSerial("N/A"))
        assertEquals("1581F", DroneModelMapper.validSerial(" 1581F "))
    }

    @Test fun payloadComesFromWaybillCargoWeight() {
        assertEquals(12.5, DispatchStages.cargoWeightKg(ApiDispatchOrderVO(fields = mapOf("weightMin" to "10", "weightMax" to "12.5")))!!, 0.0)
        assertEquals(10.0, DispatchStages.cargoWeightKg(ApiDispatchOrderVO(fields = mapOf("weightMin" to 10)))!!, 0.0)
        assertNull(DispatchStages.cargoWeightKg(ApiDispatchOrderVO(fields = mapOf("weightMax" to "abc"))))
        assertNull(DispatchStages.cargoWeightKg(null))
    }

    @Test fun currentOrderPrefersInTransitThenArrived() {
        val orders = listOf(ApiDispatchOrderVO(orderId = "a", stage = "COMPLETED"), ApiDispatchOrderVO(orderId = "b", stage = "PILOT_ARRIVED"),
            ApiDispatchOrderVO(orderId = "c", stage = "IN_TRANSIT"))
        assertEquals("c", DispatchStages.current(orders)?.orderId)
        assertEquals("b", DispatchStages.current(orders.take(2))?.orderId)
        assertNull(DispatchStages.current(orders.take(1)))
        assertTrue(DispatchStages.isFlyable(orders[1]))
        assertEquals("运输中", DispatchStages.label("IN_TRANSIT"))
    }
}
