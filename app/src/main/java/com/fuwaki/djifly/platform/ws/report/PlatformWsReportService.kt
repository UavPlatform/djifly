package com.fuwaki.djifly.platform.ws.report

import com.fuwaki.djifly.platform.registration.RegisteredDroneSnapshot
import com.fuwaki.djifly.platform.ws.protocol.PlatformUavStatusPayload
import com.fuwaki.djifly.platform.ws.protocol.PlatformWsEnvelopeFactory
import com.fuwaki.djifly.platform.ws.transport.WsMessageSender
import com.fuwaki.djifly.sdk.DjiSdkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlatformWsReportService @Inject constructor(
    private val sdkManager: DjiSdkManager
) {

    private val pendingOperation = AtomicReference<String?>(null)

    fun publishOperation(operation: String) {
        pendingOperation.set(operation)
    }

    fun clearPendingOperation() {
        pendingOperation.set(null)
    }

    fun launchReporting(
        scope: CoroutineScope,
        deviceId: String,
        snapshot: RegisteredDroneSnapshot,
        sender: WsMessageSender
    ): PlatformWsReportingJobs {
        val heartbeatJob = scope.launch {
            while (currentCoroutineContext().isActive) {
                delay(WEBSOCKET_HEARTBEAT_INTERVAL_MS)
                sender.send(
                    PlatformWsEnvelopeFactory.ping(
                        messageId = UUID.randomUUID().toString(),
                        deviceId = deviceId,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }

        val statusReporterJob = scope.launch {
            while (currentCoroutineContext().isActive) {
                val sdkStatus = sdkManager.sdkStatus.value
                val now = System.currentTimeMillis()

                val payload = PlatformUavStatusPayload(
                    deviceId = deviceId,
                    uavId = snapshot.droneId,
                    uavName = snapshot.droneName,
                    longitude = 0.0,
                    latitude = 0.0,
                    altitude = 0.0,
                    speed = 0.0,
                    battery = sdkStatus.flightData.batteryPercentage.coerceIn(0, 100),
                    flightStatus = if (sdkStatus.isProductConnected && sdkStatus.flightData.flightTimeRemaining > 0) {
                        1
                    } else {
                        0
                    },
                    operation = pendingOperation.getAndSet(null),
                    timestamp = now
                )

                sender.send(
                    PlatformWsEnvelopeFactory.uavStatus(
                        messageId = UUID.randomUUID().toString(),
                        payload = payload,
                        timestamp = now
                    )
                )
                delay(UAV_STATUS_REPORT_INTERVAL_MS)
            }
        }

        return PlatformWsReportingJobs(
            heartbeatJob = heartbeatJob,
            statusReporterJob = statusReporterJob
        )
    }

    companion object {
        const val WEBSOCKET_HEARTBEAT_INTERVAL_MS = 25_000L
        const val UAV_STATUS_REPORT_INTERVAL_MS = 5_000L
    }
}

data class PlatformWsReportingJobs(
    private val heartbeatJob: Job,
    private val statusReporterJob: Job
) {
    fun cancel() {
        heartbeatJob.cancel()
        statusReporterJob.cancel()
    }
}
