package com.fuwaki.djifly.platform.livestream

import com.fuwaki.djifly.platform.ws.protocol.PlatformWsEnvelopeFactory
import com.fuwaki.djifly.platform.ws.report.WsEventPublisher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 直播停止协调器（追溯 FLY 高-3：退出飞行页不停止推流、无受控停止回执）。
 *
 * 供 UI 生命周期调用（退出飞行页 onDispose、主动停止入口）：
 * 存在推流会话 → 停止 TRTC 推流并退房 → 经当前 /ws/drone 连接回执
 * LIVE_STOPPED 事件（{"type":"event","name":"LIVE_STOPPED","deviceId":...}），
 * 作为后端 /live/close 等待设备确认的兜底/补发。
 *
 * 收到 STOP_LIVE 命令的路径（StopLiveHandler）走自己的同步 ack + 事件回执，不经本类。
 */
@Singleton
class LiveStopCoordinator @Inject constructor(
    private val liveService: DjiTrtcLiveService,
    private val eventPublisher: WsEventPublisher
) {

    /**
     * 停止推流并回执 LIVE_STOPPED。
     * @return true = 存在推流会话且已执行停止+回执；false = 本就未在推流（无动作）。
     */
    fun stopStreamingAndNotify(): Boolean {
        if (!liveService.isSessionActive) {
            return false
        }

        liveService.stop()

        val deviceId = eventPublisher.currentDeviceId ?: run {
            // WS 未连接：无回执通道，推流已停，后端会在设备离线分支自行收尾
            return true
        }

        eventPublisher.publish(
            PlatformWsEnvelopeFactory.liveStopped(
                deviceId = deviceId,
                timestamp = System.currentTimeMillis()
            )
        )
        return true
    }
}
