package com.fuwaki.djifly.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 飞行器遥测计算与合并逻辑纯单元测试（不加载任何 DJI 类）。
 *
 * DjiSdkManager 的坐标类 Key 监听（KeyAircraftLocation / KeyAltitude / KeyAircraftVelocity）
 * 与 injectSimulatedTelemetry 注入入口均委托本对象的 apply* 方法，
 * 因此对 apply* 的断言即等价于对真实监听回调内合并逻辑的断言。
 */
class FlightTelemetryTest {

    @Test
    fun horizontal_speed_is_magnitude_of_two_components() {
        assertEquals(5.0, FlightTelemetry.horizontalSpeed(3.0, 4.0), 1e-9)
    }

    @Test
    fun horizontal_speed_handles_negative_components() {
        assertEquals(5.0, FlightTelemetry.horizontalSpeed(-3.0, 4.0), 1e-9)
        assertEquals(5.0, FlightTelemetry.horizontalSpeed(-3.0, -4.0), 1e-9)
    }

    @Test
    fun horizontal_speed_zero_when_hovering() {
        assertEquals(0.0, FlightTelemetry.horizontalSpeed(0.0, 0.0), 1e-9)
    }

    @Test
    fun horizontal_speed_single_axis() {
        assertEquals(6.4, FlightTelemetry.horizontalSpeed(6.4, 0.0), 1e-9)
        assertEquals(6.4, FlightTelemetry.horizontalSpeed(0.0, -6.4), 1e-9)
    }

    @Test
    fun apply_location_writes_both_coordinates() {
        val merged = FlightTelemetry.applyLocation(
            FlightDataState(), latitude = 22.5789, longitude = 113.9471
        )
        assertEquals(22.5789, merged.latitude!!, 1e-9)
        assertEquals(113.9471, merged.longitude!!, 1e-9)
    }

    @Test
    fun apply_location_ignores_partial_or_missing_fix() {
        val merged = FlightTelemetry.applyLocation(FlightDataState(), latitude = 22.5789, longitude = null)
        assertNull(merged.latitude)
        assertNull(merged.longitude)

        val stillEmpty = FlightTelemetry.applyLocation(FlightDataState(), latitude = null, longitude = null)
        assertNull(stillEmpty.latitude)
        assertNull(stillEmpty.longitude)
    }

    @Test
    fun apply_altitude_keeps_real_zero_and_ignores_missing() {
        // 地面待飞：高度 0.0 是真实值，必须写入
        val onGround = FlightTelemetry.applyAltitude(FlightDataState(), altitude = 0.0)
        assertEquals(0.0, onGround.altitude!!, 1e-9)

        val missing = FlightTelemetry.applyAltitude(FlightDataState(), altitude = null)
        assertNull(missing.altitude)
    }

    @Test
    fun apply_velocity_computes_horizontal_speed_from_components() {
        val merged = FlightTelemetry.applyVelocity(FlightDataState(), velocityX = 3.0, velocityY = 4.0)
        assertEquals(5.0, merged.speed!!, 1e-9)
    }

    @Test
    fun apply_velocity_ignores_partial_components() {
        val merged = FlightTelemetry.applyVelocity(FlightDataState(), velocityX = 3.0, velocityY = null)
        assertNull(merged.speed)
    }

    @Test
    fun apply_simulated_overwrites_all_telemetry_fields() {
        val seeded = FlightTelemetry.applyLocation(FlightDataState(), latitude = 22.5, longitude = 113.9)
        val simulated = FlightTelemetry.applySimulated(
            seeded, latitude = 31.2304, longitude = 121.4737, altitude = 120.0, speed = 0.0
        )
        assertEquals(31.2304, simulated.latitude!!, 1e-9)
        assertEquals(121.4737, simulated.longitude!!, 1e-9)
        assertEquals(120.0, simulated.altitude!!, 1e-9)
        assertEquals(0.0, simulated.speed!!, 1e-9)
    }

    @Test
    fun apply_functions_keep_other_fields_intact() {
        val seeded = FlightDataState(batteryPercentage = 80, gpsSatelliteCount = 18)
        val merged = FlightTelemetry.applyLocation(seeded, latitude = 22.5, longitude = 113.9)
        assertEquals(80, merged.batteryPercentage)
        assertEquals(18, merged.gpsSatelliteCount)
    }
}
