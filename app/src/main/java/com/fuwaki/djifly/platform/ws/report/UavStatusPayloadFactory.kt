package com.fuwaki.djifly.platform.ws.report

import com.fuwaki.djifly.platform.registration.RegisteredDroneSnapshot
import com.fuwaki.djifly.platform.ws.protocol.PlatformUavStatusPayload
import com.fuwaki.djifly.sdk.FlightDataState

/**
 * UAV_STATUS 上报载荷构造（纯 Kotlin，可 JVM 单测，不引用 DJI 类型）。
 *
 * 坐标/高度/速度唯一来源：[flightData]（DjiSdkManager 对
 * FlightControllerKey.KeyAircraftLocation / KeyAltitude / KeyAircraftVelocity 的监听，
 * 或 DjiSdkManager.injectSimulatedTelemetry 注入的模拟值）。
 *
 * 遥测字段未获取到（null，例如飞行器尚未定位）时回退 0.0，保持既有上报契约字段非空。
 */
object UavStatusPayloadFactory {

    fun create(
        deviceId: String,
        snapshot: RegisteredDroneSnapshot,
        flightData: FlightDataState,
        isProductConnected: Boolean,
        operation: String?,
        timestamp: Long
    ): PlatformUavStatusPayload = PlatformUavStatusPayload(
        deviceId = deviceId,
        uavId = snapshot.droneId,
        uavName = snapshot.droneName,
        longitude = flightData.longitude ?: 0.0,
        latitude = flightData.latitude ?: 0.0,
        altitude = flightData.altitude ?: 0.0,
        speed = flightData.speed ?: 0.0,
        battery = flightData.batteryPercentage.coerceIn(0, 100),
        flightStatus = if (isProductConnected && flightData.flightTimeRemaining > 0) 1 else 0,
        operation = operation,
        timestamp = timestamp
    )
}
