package com.fuwaki.djifly.platform.ws.transport

import android.util.Log
import com.fuwaki.djifly.platform.ws.WsCommunicationState
import com.fuwaki.djifly.platform.ws.protocol.PlatformWsEnvelope
import com.fuwaki.djifly.platform.ws.protocol.PlatformWsMessageCodec
import okhttp3.WebSocket

fun interface WsMessageSender {
    fun send(message: Any): Boolean
}

class JsonWsMessageSender(
    private val codec: PlatformWsMessageCodec,
    private val communicationState: WsCommunicationState,
    private val webSocket: WebSocket
) : WsMessageSender {

    override fun send(message: Any): Boolean {
        val payload = codec.encode(message)
        val sent = runCatching { webSocket.send(payload) }
            .onFailure { error -> Log.w("WsMessageSender", "send failed: ${error.message}") }
            .getOrDefault(false)

        if (sent) {
            val envelope = message as? PlatformWsEnvelope<*>
            communicationState.logSent(
                type = envelope?.type?.name ?: "UNKNOWN",
                name = envelope?.name?.name,
                content = payload
            )
        }

        return sent
    }
}
