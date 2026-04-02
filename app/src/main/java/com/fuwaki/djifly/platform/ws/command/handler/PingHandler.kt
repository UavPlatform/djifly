package com.fuwaki.djifly.platform.ws.command.handler

import com.fuwaki.djifly.platform.ws.protocol.PlatformWsEnvelopeFactory
import com.fuwaki.djifly.platform.ws.protocol.PlatformWsInboundEnvelope
import com.fuwaki.djifly.platform.ws.protocol.PlatformWsName
import com.fuwaki.djifly.platform.ws.protocol.PlatformWsType
import com.fuwaki.djifly.platform.ws.transport.WsMessageSender

class PingHandler : WsMessageHandler {
    override val type = PlatformWsType.EVENT
    override val name = PlatformWsName.PING

    override suspend fun handle(
        message: PlatformWsInboundEnvelope,
        deviceId: String,
        sender: WsMessageSender
    ) {
        sender.send(
            PlatformWsEnvelopeFactory.pong(
                replyTo = message.id,
                deviceId = deviceId,
                timestamp = System.currentTimeMillis()
            )
        )
    }
}
