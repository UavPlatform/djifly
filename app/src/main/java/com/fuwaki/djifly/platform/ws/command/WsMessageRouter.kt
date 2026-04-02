package com.fuwaki.djifly.platform.ws.command

import android.util.Log
import com.fuwaki.djifly.platform.ws.command.handler.WsMessageHandler
import com.fuwaki.djifly.platform.ws.protocol.PlatformWsInboundEnvelope
import com.fuwaki.djifly.platform.ws.transport.WsMessageSender
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WsMessageRouter @Inject constructor(
    private val handlers: Set<@JvmSuppressWildcards WsMessageHandler>
) {

    private val tag = "WsMessageRouter"

    suspend fun route(
        message: PlatformWsInboundEnvelope,
        deviceId: String,
        sender: WsMessageSender
    ) {
        val type = message.type ?: run {
            Log.w(tag, "unknown type: ${message.typeRaw}")
            return
        }

        val handler = handlers.find { it.supports(type, message.name) } ?: run {
            Log.w(tag, "no handler for type=$type, name=${message.name ?: message.nameRaw}")
            return
        }

        handler.handle(
            message = message,
            deviceId = deviceId,
            sender = sender
        )
    }
}
