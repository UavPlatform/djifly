package com.fuwaki.djifly.platform.telemetry

import com.fuwaki.djifly.platform.dispatch.FlightSample
import com.fuwaki.djifly.sdk.FlightDataState

/** 遥测采样间隔（ADR-0009）：默认 2 秒，可配置，最短 1 秒。 */
object TelemetryInterval {
    const val DEFAULT_MS = 2_000L
    const val MIN_MS = 1_000L

    fun clamp(configuredMs: Long?): Long = when {
        configuredMs == null || configuredMs <= 0 -> DEFAULT_MS
        else -> maxOf(MIN_MS, configuredMs)
    }
}

/**
 * 把 SDK 遥测状态转成一条上报采样。纯 Kotlin，可 JVM 单测。
 * 拿不到的值保持 null（不回退 0）；经纬度必须成对出现，否则都为 null。
 * 载重：当前 MSDK 没有可直接读取的实时挂载重量 Key，默认 null；拿到后通过 [payloadKg] 传入。
 */
class TelemetrySampler(startSequence: Long = 0) {
    private var next = startSequence

    fun sample(state: FlightDataState, capturedAt: Long, payloadKg: Double? = null): FlightSample {
        val hasPosition = state.latitude != null && state.longitude != null &&
            state.latitude.isFinite() && state.longitude.isFinite()
        return FlightSample(
            sequence = next++,
            capturedAt = capturedAt,
            latitude = if (hasPosition) state.latitude else null,
            longitude = if (hasPosition) state.longitude else null,
            altitude = state.altitude?.takeIf { it.isFinite() },
            speed = state.speed?.takeIf { it.isFinite() && it >= 0 },
            battery = if (state.batteryKnown) state.batteryPercentage.coerceIn(0, 100) else null,
            flying = state.isFlying,
            payloadKg = payloadKg?.takeIf { it.isFinite() && it >= 0 }
        )
    }
}
