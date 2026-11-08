package com.fuwaki.djifly.platform.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「数据交没交」的人话状态；关键是不要把小故障说成整趟作废。 */
class UploadReportStatusResolverTest {

    private fun resolve(
        hasFlyableOrder: Boolean = true,
        aircraftConfirmed: Boolean = true,
        recordingActive: Boolean = true,
        pendingPackets: Int = 0,
        lastError: String? = null,
    ) = UploadReportStatusResolver.resolve(
        hasFlyableOrder, aircraftConfirmed, recordingActive, pendingPackets, lastError
    )

    @Test
    fun single_packet_rejection_is_not_reported_as_a_whole_trip_rejection() {
        // Regression: this used to match any error containing "拒绝" and told the pilot the flight was discarded.
        val status = resolve(pendingPackets = 3, lastError = "上传被拒绝（HTTP 409）")
        assertEquals(UploadReportPhase.LOCAL_PENDING, status.phase)
        assertTrue(status.phrase.contains("3"))
        assertTrue(status.phrase.contains("HTTP 409"))
    }

    @Test
    fun chain_conflict_keeps_local_capture_visible() {
        val status = resolve(pendingPackets = 5, lastError = "事件链冲突，对账未取得可用共同前缀，上传暂停")
        assertEquals(UploadReportPhase.LOCAL_PENDING, status.phase)
        assertTrue(status.phrase.contains("5"))
    }

    @Test
    fun whole_trip_rejection_still_reports_rejected() {
        val status = resolve(pendingPackets = 0, lastError = "本趟被拒绝：调度已取消")
        assertEquals(UploadReportPhase.REJECTED, status.phase)
        assertTrue(status.phrase.contains("不会上传"))
    }

    @Test
    fun healthy_flight_reports_pending_packets() {
        assertEquals(UploadReportPhase.LOCAL_PENDING, resolve(pendingPackets = 2).phase)
        assertEquals(UploadReportPhase.LOCAL_PENDING, resolve(pendingPackets = 0).phase)
    }

    @Test
    fun missing_aircraft_or_order_reports_not_started_with_real_reasons() {
        val noAircraft = resolve(aircraftConfirmed = false)
        assertEquals(UploadReportPhase.NOT_STARTED, noAircraft.phase)
        assertTrue(noAircraft.phrase.contains("缺飞机"))

        val noOrder = resolve(hasFlyableOrder = false)
        assertEquals(UploadReportPhase.NOT_STARTED, noOrder.phase)
        assertTrue(noOrder.phrase.contains("没有可飞的单"))
    }
}
