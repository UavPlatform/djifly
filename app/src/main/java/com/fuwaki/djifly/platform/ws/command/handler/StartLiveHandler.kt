package com.fuwaki.djifly.platform.ws.command.handler

import com.fuwaki.djifly.BuildConfig
import com.fuwaki.djifly.platform.ws.command.callback.WsCommandCallbackResult
import com.fuwaki.djifly.platform.ws.command.callback.WsCommandCallbacks
import com.fuwaki.djifly.platform.ws.protocol.PlatformStartLivePayload
import com.fuwaki.djifly.platform.ws.protocol.PlatformWsEnvelopeFactory
import com.fuwaki.djifly.platform.ws.protocol.PlatformWsInboundEnvelope
import com.fuwaki.djifly.platform.ws.protocol.PlatformWsName
import com.fuwaki.djifly.platform.ws.protocol.PlatformWsType
import com.fuwaki.djifly.platform.ws.transport.WsMessageSender
import com.google.gson.Gson
import java.util.UUID

class StartLiveHandler(
    private val callbacks: WsCommandCallbacks,
    private val gson: Gson
) : WsMessageHandler {

    override val type = PlatformWsType.COMMAND
    override val name = PlatformWsName.START_LIVE

    override suspend fun handle(
        message: PlatformWsInboundEnvelope,
        deviceId: String,
        sender: WsMessageSender
    ) {
        val requestId = message.id ?: UUID.randomUUID().toString()
        val payload = message.parseData(gson, PlatformStartLivePayload::class.java)

        if (payload == null) {
            sender.send(
                PlatformWsEnvelopeFactory.startLiveRejected(
                    replyTo = requestId,
                    deviceId = deviceId,
                    code = "INVALID_MESSAGE",
                    message = "START_LIVE 缺少 data",
                    timestamp = System.currentTimeMillis()
                )
            )
            return
        }

        if (payload.roomId.isBlank() || payload.userId.isBlank() || payload.userSig.isBlank()) {
            sender.send(
                PlatformWsEnvelopeFactory.startLiveRejected(
                    replyTo = requestId,
                    deviceId = deviceId,
                    code = "INVALID_MESSAGE",
                    message = "START_LIVE 参数不完整",
                    timestamp = System.currentTimeMillis()
                )
            )
            return
        }

        val sdkAppId = BuildConfig.TRTC_SDK_APP_ID
        if (sdkAppId <= 0L || sdkAppId > Int.MAX_VALUE) {
            sender.send(
                PlatformWsEnvelopeFactory.startLiveRejected(
                    replyTo = requestId,
                    deviceId = deviceId,
                    code = "TRTC_CONFIG_MISSING",
                    message = "未配置 TRTC_SDK_APP_ID，无法启动图传",
                    timestamp = System.currentTimeMillis()
                )
            )
            return
        }

        when (val callbackResult = callbacks.onStartLive(payload)) {
            is WsCommandCallbackResult.Success -> {
                sender.send(
                    PlatformWsEnvelopeFactory.startLiveAccepted(
                        replyTo = requestId,
                        deviceId = deviceId,
                        timestamp = System.currentTimeMillis()
                    )
                )
                sender.send(
                    PlatformWsEnvelopeFactory.liveStarted(
                        replyTo = requestId,
                        deviceId = deviceId,
                        roomId = callbackResult.roomId,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }

            is WsCommandCallbackResult.Error -> {
                sender.send(
                    PlatformWsEnvelopeFactory.startLiveRejected(
                        replyTo = requestId,
                        deviceId = deviceId,
                        code = callbackResult.code,
                        message = callbackResult.message,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }
}
