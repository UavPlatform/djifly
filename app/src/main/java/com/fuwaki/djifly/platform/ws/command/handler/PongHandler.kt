package com.fuwaki.djifly.platform.ws.command.handler

import com.fuwaki.djifly.platform.ws.protocol.PlatformWsInboundEnvelope
import com.fuwaki.djifly.platform.ws.protocol.PlatformWsName
import com.fuwaki.djifly.platform.ws.protocol.PlatformWsType
import com.fuwaki.djifly.platform.ws.transport.WsMessageSender

/**
 * 服务端对客户端 PING 的应用层回执（t12/心跳约定：{"type":"event","name":"PONG","replyTo":...}）。
 * 客户端无需处理 PONG 内容（服务端按任意入站消息刷新活跃），注册 no-op handler
 * 仅为了让路由器不把它当未知消息打告警日志。
 */
class PongHandler : WsMessageHandler {
    override val type = PlatformWsType.EVENT
    override val name = PlatformWsName.PONG

    override suspend fun handle(
        message: PlatformWsInboundEnvelope,
        deviceId: String,
        sender: WsMessageSender
    ) {
        // no-op：PONG 仅用于服务端判活/客户端确认链路，无需业务处理
    }
}
