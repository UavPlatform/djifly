package com.fuwaki.djifly.platform.ws.report

import com.fuwaki.djifly.platform.ws.transport.WsMessageSender
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设备→平台主动事件的发布通道（WS 会话外发件的临时挂点）。
 *
 * WS handler 的回执天然持有当次消息的 sender；但 UI 生命周期触发的主动上报
 * （如退出飞行页停止推流后回执 LIVE_STOPPED，追溯 FLY 高-3）没有 inbound 上下文，
 * 由 [PlatformWsSessionService] 在会话建立时 attach sender、断开时 detach，
 * 本类提供"当前连接上发一封事件信封"的能力。
 *
 * 仅保存最近一次 attach 的 sender（单设备单会话语义），线程安全。
 */
@Singleton
class WsEventPublisher @Inject constructor() {

    private val senderRef = AtomicReference<WsMessageSender?>(null)

    @Volatile
    private var attachedDeviceId: String? = null

    /** 会话建立时挂载 sender 与 deviceId */
    fun attach(sender: WsMessageSender, deviceId: String) {
        attachedDeviceId = deviceId
        senderRef.set(sender)
    }

    /** 会话断开时卸载（只清自己挂的那一份，防旧会话误清新会话） */
    fun detach(sender: WsMessageSender) {
        senderRef.compareAndSet(sender, null)?.let { detached ->
            if (senderRef.get() == null) {
                attachedDeviceId = null
            }
        }
    }

    /** 当前挂载的 deviceId（未连接时 null） */
    val currentDeviceId: String?
        get() = attachedDeviceId

    /** 连接是否可用（有挂载的 sender） */
    val isAttached: Boolean
        get() = senderRef.get() != null

    /** 在当前连接上发送一封事件信封；未连接返回 false */
    fun publish(envelope: Any): Boolean {
        val sender = senderRef.get() ?: return false
        return sender.send(envelope)
    }
}
