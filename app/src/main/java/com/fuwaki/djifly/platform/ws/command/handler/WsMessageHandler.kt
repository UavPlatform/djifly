package com.fuwaki.djifly.platform.ws.command.handler

import com.fuwaki.djifly.platform.ws.protocol.PlatformWsInboundEnvelope
import com.fuwaki.djifly.platform.ws.protocol.PlatformWsName
import com.fuwaki.djifly.platform.ws.protocol.PlatformWsType
import com.fuwaki.djifly.platform.ws.transport.WsMessageSender

sealed interface WsMessageHandler {
    val type: PlatformWsType
    val name: PlatformWsName?

    fun supports(type: PlatformWsType, name: PlatformWsName?): Boolean {
        return type == this.type && (this.name == null || name == this.name)
    }

    suspend fun handle(
        message: PlatformWsInboundEnvelope,
        deviceId: String,
        sender: WsMessageSender
    )
}
