package com.fuwaki.djifly.platform.ws.command.callback

import com.fuwaki.djifly.platform.ws.protocol.PlatformStartLivePayload

interface WsCommandCallbacks {
    suspend fun onStartLive(payload: PlatformStartLivePayload): WsCommandCallbackResult
}

sealed class WsCommandCallbackResult {
    data class Success(val roomId: String) : WsCommandCallbackResult()
    data class Error(val code: String, val message: String) : WsCommandCallbackResult()
}
