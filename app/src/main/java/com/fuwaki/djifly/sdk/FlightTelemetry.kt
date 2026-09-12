package com.fuwaki.djifly.sdk

import kotlin.math.sqrt

/**
 * 飞行器遥测计算与合并逻辑（纯 Kotlin，可 JVM 单测，不引用 DJI 类型）。
 *
 * 遥测数据本体保存在 [FlightDataState]（latitude/longitude/altitude/speed，可空表示未获取）。
 * 数据写入方为 [DjiSdkManager]：
 * - 真实链路：监听 DJI Mobile SDK V5 的 `FlightControllerKey.KeyAircraftLocation` /
 *   `KeyAltitude` / `KeyAircraftVelocity`，回调里调用本对象 apply* 方法；
 * - 模拟链路：真机不可用期间可用 [DjiSdkManager.injectSimulatedTelemetry] 注入模拟值，
 *   验证 UAV_STATUS 上报协议与数据流（打磨路线图 1A-4 风险表：真机验收挂联调窗口）。
 */
object FlightTelemetry {

    /**
     * 水平速度（米/秒）= √(vx² + vy²)。
     * KeyAircraftVelocity 返回 Velocity3D 三维速度分量，本端只上报水平合速度。
     */
    fun horizontalSpeed(velocityX: Double, velocityY: Double): Double =
        sqrt(velocityX * velocityX + velocityY * velocityY)

    /**
     * 合并经纬度（度，KeyAircraftLocation → LocationCoordinate2D）。
     * 任一分量缺失时保持原状态（不产生半更新）。
     */
    fun applyLocation(
        state: FlightDataState,
        latitude: Double?,
        longitude: Double?
    ): FlightDataState =
        if (latitude != null && longitude != null) {
            state.copy(latitude = latitude, longitude = longitude)
        } else {
            state
        }

    /**
     * 合并相对起飞点高度（米，KeyAltitude）。0.0 是真实值（地面待飞），null 表示未获取。
     */
    fun applyAltitude(state: FlightDataState, altitude: Double?): FlightDataState =
        if (altitude != null) {
            state.copy(altitude = altitude)
        } else {
            state
        }

    /**
     * 合并水平速度（米/秒，KeyAircraftVelocity → Velocity3D 的 X/Y 分量求合速度）。
     * 任一分量缺失时保持原状态。
     */
    fun applyVelocity(
        state: FlightDataState,
        velocityX: Double?,
        velocityY: Double?
    ): FlightDataState =
        if (velocityX != null && velocityY != null) {
            state.copy(speed = horizontalSpeed(velocityX, velocityY))
        } else {
            state
        }

    /**
     * 注入模拟遥测（仅调试/测试场景，会覆盖真实 Key 监听写入的值）。
     * 高度单位米，速度单位米/秒，经纬度单位度（WGS84）。
     */
    fun applySimulated(
        state: FlightDataState,
        latitude: Double,
        longitude: Double,
        altitude: Double,
        speed: Double
    ): FlightDataState = state.copy(
        latitude = latitude,
        longitude = longitude,
        altitude = altitude,
        speed = speed
    )
}
