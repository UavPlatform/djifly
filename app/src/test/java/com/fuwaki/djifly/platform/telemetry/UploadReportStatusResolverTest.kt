package com.fuwaki.djifly.platform.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadReportStatusResolverTest {

    private fun connection(
        serverId: String? = null,
        rejected: String? = null,
    ) = LocalConnection(
        localId = "local-1",
        orderId = "ord-1",
        assignmentVersion = 1,
        requestKey = "rk",
        serialNumber = "SN1234567890",
        modelCode = "FC30",
        modelName = "FlyCart 30",
        startedAt = 1L,
        serverId = serverId,
        rejected = rejected,
    )

    @Test
    fun not_started_when_aircraft_missing() {
        val s = UploadReportStatusResolver.resolve(
            hasFlyableOrder = true,
            aircraftConfirmed = false,
            connection = null,
            pendingSamples = 0,
            lastError = null,
        )
        assertEquals(UploadReportPhase.NOT_STARTED, s.phase)
        assertTrue(s.phrase.contains("未开始记录"))
        assertTrue(s.phrase.contains("缺飞机") || s.phrase.contains("机身号"))
        assertFalse(s.phrase.contains("没有可飞的单"))
    }

    @Test
    fun not_started_when_order_missing_only() {
        val s = UploadReportStatusResolver.resolve(
            hasFlyableOrder = false,
            aircraftConfirmed = true,
            connection = null,
            pendingSamples = 0,
            lastError = null,
        )
        assertEquals(UploadReportPhase.NOT_STARTED, s.phase)
        assertTrue(s.phrase.contains("没有可飞的单"))
        assertFalse(s.phrase.contains("缺飞机"))
    }

    @Test
    fun not_started_when_confirmed_with_order_but_no_connection() {
        val s = UploadReportStatusResolver.resolve(
            hasFlyableOrder = true,
            aircraftConfirmed = true,
            connection = null,
            pendingSamples = 0,
            lastError = null,
        )
        assertEquals(UploadReportPhase.NOT_STARTED, s.phase)
        assertEquals("未开始记录", s.phrase)
        assertFalse(s.phrase.contains("没有可飞的单"))
    }

    @Test
    fun not_started_when_connection_exists_but_no_samples_and_no_server_id() {
        val s = UploadReportStatusResolver.resolve(
            hasFlyableOrder = true,
            aircraftConfirmed = true,
            connection = connection(serverId = null),
            pendingSamples = 0,
            lastError = null,
        )
        assertEquals(UploadReportPhase.NOT_STARTED, s.phase)
        assertEquals("未开始记录", s.phrase)
        assertFalse(s.phrase.contains("已在本机记录"))
    }

    @Test
    fun local_pending_only_when_pending_samples_positive() {
        val s = UploadReportStatusResolver.resolve(
            hasFlyableOrder = true,
            aircraftConfirmed = true,
            connection = connection(serverId = null),
            pendingSamples = 3,
            lastError = null,
        )
        assertEquals(UploadReportPhase.LOCAL_PENDING, s.phase)
        assertTrue(s.phrase.contains("已在本机记录"))
        assertTrue(s.phrase.contains("3"))
    }

    @Test
    fun accepted_when_server_has_connection_and_queue_empty() {
        val s = UploadReportStatusResolver.resolve(
            hasFlyableOrder = true,
            aircraftConfirmed = true,
            connection = connection(serverId = "srv-1"),
            pendingSamples = 0,
            lastError = null,
        )
        assertEquals(UploadReportPhase.ACCEPTED, s.phase)
        assertEquals("平台已收下", s.phrase)
    }

    @Test
    fun rejected_uses_human_phrase() {
        val s = UploadReportStatusResolver.resolve(
            hasFlyableOrder = true,
            aircraftConfirmed = true,
            connection = connection(serverId = null, rejected = "订单已不归本人"),
            pendingSamples = 0,
            lastError = "连机记录被平台拒绝（403），本次飞行的遥测不会上报，请检查订单状态",
        )
        assertEquals(UploadReportPhase.REJECTED, s.phase)
        assertTrue(s.phrase.contains("平台拒绝了这一趟"))
    }
}
