package com.fuwaki.djifly.platform.ws.report

import com.fuwaki.djifly.platform.registration.RegisteredDroneSnapshot
import com.fuwaki.djifly.platform.ws.protocol.PlatformWsEnvelopeFactory
import com.fuwaki.djifly.platform.ws.protocol.PlatformWsMessageCodec
import com.fuwaki.djifly.sdk.FlightDataState
import com.fuwaki.djifly.sdk.FlightTelemetry
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UAV_STATUS 上报载荷构造的纯单元测试（不加载任何 DJI 类，见 QA 测试策略 §2.4 P0）。
 *
 * 覆盖验收标准「模拟坐标注入下 UAV_STATUS 载荷数值随输入变化」：
 * 真实链路中 DjiSdkManager 监听 KeyAircraftLocation / KeyAltitude / KeyAircraftVelocity
 * 并经 FlightTelemetry.apply* 写入 FlightDataState；真机不可用期间走同一份
 * FlightTelemetry.applySimulated 注入模拟值（DjiSdkManager.injectSimulatedTelemetry）。
 * 本测试用 FlightTelemetry.applySimulated 生成与注入完全相同的 FlightDataState，
 * 断言上报载荷精确跟随输入变化，并经 PlatformWsMessageCodec 序列化验证线上协议载荷。
 *
 * PlatformWsReportService 每 5s 的 UAV_STATUS 上报循环即调用 UavStatusPayloadFactory.create，
 * 因此对工厂输出的断言等价于对实际上报载荷的断言。
 */
class UavStatusPayloadFactoryTest {

    private val snapshot = RegisteredDroneSnapshot(
        serialNumber = "TEST-SN-001",
        droneId = 42L,
        droneName = "测试无人机",
        controllerModel = "RC Plus",
        createdOnServer = true,
        lastRegisteredAtMillis = 1_700_000_000_000L
    )

    private fun buildPayload(
        flightData: FlightDataState,
        isProductConnected: Boolean = false,
        timestamp: Long = 1_700_000_000_123L
    ) = UavStatusPayloadFactory.create(
        deviceId = "TEST-SN-001",
        snapshot = snapshot,
        flightData = flightData,
        isProductConnected = isProductConnected,
        operation = null,
        timestamp = timestamp
    )

    @Test
    fun telemetry_missing_falls_back_to_zero_and_keeps_contract() {
        val payload = buildPayload(FlightDataState())

        // 未获取到遥测（null）时回退 0.0，保持既有上报契约字段非空
        assertEquals(0.0, payload.latitude, 0.0)
        assertEquals(0.0, payload.longitude, 0.0)
        assertEquals(0.0, payload.altitude, 0.0)
        assertEquals(0.0, payload.speed, 0.0)
        assertEquals("TEST-SN-001", payload.deviceId)
        assertEquals(42L, payload.uavId)
        assertEquals("测试无人机", payload.uavName)
    }

    @Test
    fun simulated_telemetry_flows_into_uav_status_payload() {
        val baseline = buildPayload(FlightDataState())
        assertEquals(0.0, baseline.latitude, 0.0)
        assertEquals(0.0, baseline.longitude, 0.0)

        // 第一次模拟坐标注入（深圳宝安附近），与 DjiSdkManager.injectSimulatedTelemetry 同一合并逻辑
        val firstFlightData = FlightTelemetry.applySimulated(
            FlightDataState(),
            latitude = 22.5789,
            longitude = 113.9471,
            altitude = 87.5,
            speed = 6.4
        )
        val first = buildPayload(firstFlightData, timestamp = 2L)
        assertEquals(22.5789, first.latitude, 1e-9)
        assertEquals(113.9471, first.longitude, 1e-9)
        assertEquals(87.5, first.altitude, 1e-9)
        assertEquals(6.4, first.speed, 1e-9)

        // 输入变化 → 载荷数值随之变化（悬停：speed=0 是真实值而非未获取）
        val secondFlightData = FlightTelemetry.applySimulated(
            FlightDataState(),
            latitude = 31.2304,
            longitude = 121.4737,
            altitude = 120.0,
            speed = 0.0
        )
        val second = buildPayload(secondFlightData, timestamp = 3L)
        assertEquals(31.2304, second.latitude, 1e-9)
        assertEquals(121.4737, second.longitude, 1e-9)
        assertEquals(120.0, second.altitude, 1e-9)
        assertEquals(0.0, second.speed, 1e-9)

        assertTrue(second.latitude != first.latitude)
        assertTrue(second.longitude != first.longitude)
        assertTrue(second.altitude != first.altitude)
        assertTrue(second.speed != first.speed)
    }

    @Test
    fun uav_status_wire_payload_carries_simulated_coordinates() {
        val flightData = FlightTelemetry.applySimulated(
            FlightDataState(),
            latitude = 22.5789,
            longitude = 113.9471,
            altitude = 87.5,
            speed = 6.4
        )
        val payload = buildPayload(flightData, timestamp = 42L)

        val envelope = PlatformWsEnvelopeFactory.uavStatus(
            messageId = "msg-1",
            payload = payload,
            timestamp = 42L
        )
        val json = PlatformWsMessageCodec(Gson()).encode(envelope)

        assertTrue(json.contains("\"name\":\"UAV_STATUS\""))
        assertTrue(json.contains("\"deviceId\":\"TEST-SN-001\""))
        assertTrue(json.contains("22.5789"))
        assertTrue(json.contains("113.9471"))
        assertTrue(json.contains("87.5"))
        assertTrue(json.contains("6.4"))
    }

    @Test
    fun battery_clamped_and_flight_status_follows_connection_and_remaining_time() {
        // 已连接且剩余飞行时间 > 0 → flightStatus=1；电量超界钳制到 100
        val flying = buildPayload(
            FlightDataState(batteryPercentage = 150, flightTimeRemaining = 600),
            isProductConnected = true
        )
        assertEquals(100, flying.battery)
        assertEquals(1, flying.flightStatus)

        // 未连接时即便剩余时间 > 0 也为 0
        val disconnected = buildPayload(
            FlightDataState(batteryPercentage = 50, flightTimeRemaining = 600),
            isProductConnected = false
        )
        assertEquals(50, disconnected.battery)
        assertEquals(0, disconnected.flightStatus)

        // 已连接但剩余飞行时间为 0 → 0
        val noTime = buildPayload(
            FlightDataState(batteryPercentage = 50, flightTimeRemaining = 0),
            isProductConnected = true
        )
        assertEquals(0, noTime.flightStatus)
    }
}
