package com.fuwaki.djifly.platform.ws.command.callback

import com.fuwaki.djifly.platform.ws.protocol.PlatformStartLivePayload

interface WsCommandCallbacks {
    suspend fun onStartLive(payload: PlatformStartLivePayload): WsCommandCallbackResult

    /**
     * 响应 STOP_LIVE（t12 协议）：停止 TRTC 推流并退房。
     * 幂等：未在推流时也返回 [WsCommandCallbackResult.Stopped]（视为已停止，后端可安全进入终态）。
     */
    suspend fun onStopLive(): WsCommandCallbackResult
}

sealed class WsCommandCallbackResult {
    data class Success(val roomId: String) : WsCommandCallbackResult()

    /** 停止动作完成（含"本就未在推流"的幂等成功），message 为人类可读说明 */
    data class Stopped(val message: String) : WsCommandCallbackResult()

    data class Error(val code: String, val message: String) : WsCommandCallbackResult()
}
