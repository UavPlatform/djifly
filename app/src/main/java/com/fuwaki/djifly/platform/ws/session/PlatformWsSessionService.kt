package com.fuwaki.djifly.platform.ws.session

import com.fuwaki.djifly.platform.registration.RegisteredDroneSnapshot
import com.fuwaki.djifly.platform.ws.command.WsMessageRouter
import com.fuwaki.djifly.platform.ws.protocol.PlatformWsEnvelopeFactory
import com.fuwaki.djifly.platform.ws.report.PlatformWsReportingJobs
import com.fuwaki.djifly.platform.ws.report.PlatformWsReportService
import com.fuwaki.djifly.platform.ws.report.WsEventPublisher
import com.fuwaki.djifly.platform.ws.transport.PlatformWsClient
import com.fuwaki.djifly.platform.ws.transport.WsMessageSender
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlatformWsSessionService @Inject constructor(
    private val wsClient: PlatformWsClient,
    private val messageRouter: WsMessageRouter,
    private val reportService: PlatformWsReportService,
    private val eventPublisher: WsEventPublisher
) {

    suspend fun connectAndRun(
        serialNumber: String,
        snapshot: RegisteredDroneSnapshot,
        onConnected: () -> Unit
    ): PlatformWsRunResult = coroutineScope {
        var reportingJobs: PlatformWsReportingJobs? = null
        var sessionSender: WsMessageSender? = null

        try {
            wsClient.run(
                deviceId = serialNumber,
                onOpen = { sender ->
                    // 挂载主动事件通道（如退出飞行页停止推流后回执 LIVE_STOPPED）
                    sessionSender = sender
                    eventPublisher.attach(sender, serialNumber)
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
            // 只卸载本次会话挂载的 sender，避免旧会话误清新会话
            sessionSender?.let { eventPublisher.detach(it) }
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
