package com.fuwaki.djifly.platform.ws.command.handler

import com.fuwaki.djifly.platform.ws.command.callback.WsCommandCallbackResult
import com.fuwaki.djifly.platform.ws.command.callback.WsCommandCallbacks
import com.fuwaki.djifly.platform.ws.protocol.PlatformWsEnvelopeFactory
import com.fuwaki.djifly.platform.ws.protocol.PlatformWsInboundEnvelope
import com.fuwaki.djifly.platform.ws.protocol.PlatformWsName
import com.fuwaki.djifly.platform.ws.protocol.PlatformWsType
import com.fuwaki.djifly.platform.ws.transport.WsMessageSender
import java.util.UUID

/**
 * STOP_LIVE 命令处理（t12 协议，追溯 FLY 高-3：直播开始后无受控停止路径）。
 *
 * 流程：
 * 1. 停止 TRTC 推流并退房（经 [WsCommandCallbacks.onStopLive]，幂等）；
 * 2. 同步回执：{"type":"response","replyTo":"<requestId>","success":...} 给 REST /live/close 的同步结果；
 * 3. 异步回执：{"type":"event","name":"LIVE_STOPPED",...} 在真正停流后必发（ACK 超时时后端唯一兜底）。
 *
 * 回执语义：
 * - Stopped（含"本就未在推流"）→ ack success=true + LIVE_STOPPED（幂等终态，后端安全收尾）；
 * - Error → ack success=false（code 如 LIVE_STOP_REJECTED → 后端 409），不发 LIVE_STOPPED（推流未停）。
 */
class StopLiveHandler(
    private val callbacks: WsCommandCallbacks
) : WsMessageHandler {

    override val type = PlatformWsType.COMMAND
    override val name = PlatformWsName.STOP_LIVE

    override suspend fun handle(
        message: PlatformWsInboundEnvelope,
        deviceId: String,
        sender: WsMessageSender
    ) {
        // 后端命令必带 id（requestId）；缺失时自兜底，保证 ack 可被 replyTo 关联
        val requestId = message.id ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        when (val result = callbacks.onStopLive()) {
            is WsCommandCallbackResult.Stopped -> {
                sender.send(
                    PlatformWsEnvelopeFactory.stopLiveAck(
                        replyTo = requestId,
                        deviceId = deviceId,
                        success = true,
                        code = "OK",
                        message = result.message,
                        timestamp = now
                    )
                )
                // 真正停流（或本就未推流）后必发事件，作为 ACK 超时兜底
                sender.send(
                    PlatformWsEnvelopeFactory.liveStopped(
                        deviceId = deviceId,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }

            is WsCommandCallbackResult.Error -> {
                sender.send(
                    PlatformWsEnvelopeFactory.stopLiveAck(
                        replyTo = requestId,
                        deviceId = deviceId,
                        success = false,
                        code = result.code,
                        message = result.message,
                        timestamp = now
                    )
                )
            }

            is WsCommandCallbackResult.Success -> {
                // STOP_LIVE 不产生开播语义，Success 不应出现；按拒绝处理防御
                sender.send(
                    PlatformWsEnvelopeFactory.stopLiveAck(
                        replyTo = requestId,
                        deviceId = deviceId,
                        success = false,
                        code = "LIVE_STOP_REJECTED",
                        message = "STOP_LIVE 收到非法回调结果",
                        timestamp = now
                    )
                )
            }
        }
    }
}
