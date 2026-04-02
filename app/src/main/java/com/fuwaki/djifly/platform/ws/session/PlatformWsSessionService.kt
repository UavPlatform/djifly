package com.fuwaki.djifly.platform.ws.session

import com.fuwaki.djifly.platform.registration.RegisteredDroneSnapshot
import com.fuwaki.djifly.platform.ws.command.WsMessageRouter
import com.fuwaki.djifly.platform.ws.protocol.PlatformWsEnvelopeFactory
import com.fuwaki.djifly.platform.ws.report.PlatformWsReportingJobs
import com.fuwaki.djifly.platform.ws.report.PlatformWsReportService
import com.fuwaki.djifly.platform.ws.transport.PlatformWsClient
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlatformWsSessionService @Inject constructor(
    private val wsClient: PlatformWsClient,
    private val messageRouter: WsMessageRouter,
    private val reportService: PlatformWsReportService
) {

    suspend fun connectAndRun(
        serialNumber: String,
        snapshot: RegisteredDroneSnapshot,
        onConnected: () -> Unit
    ): PlatformWsRunResult = coroutineScope {
        var reportingJobs: PlatformWsReportingJobs? = null

        try {
            wsClient.run(
                deviceId = serialNumber,
                onOpen = { sender ->
                    sender.send(
                        PlatformWsEnvelopeFactory.connectSuccess(
                            deviceId = serialNumber,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    reportingJobs = reportService.launchReporting(
                        scope = this,
                        deviceId = serialNumber,
                        snapshot = snapshot,
                        sender = sender
                    )
                    onConnected()
                },
                onMessage = { message, sender ->
                    messageRouter.route(
                        message = message,
                        deviceId = serialNumber,
                        sender = sender
                    )
                }
            )
        } finally {
            reportingJobs?.cancel()
        }
    }

    fun disconnect() {
        reportService.clearPendingOperation()
        wsClient.disconnect()
    }

    fun publishOperation(operation: String) {
        reportService.publishOperation(operation)
    }
}

data class PlatformWsRunResult(
    val opened: Boolean,
    val fatal: Boolean,
    val reason: String
)
