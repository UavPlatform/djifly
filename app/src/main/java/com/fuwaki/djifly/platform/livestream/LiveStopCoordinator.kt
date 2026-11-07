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
 * 订单直播（[DispatchLivePublisher]）有独立的服务端会话与 lease，必须走它自己的 stop 接口；
 * 只停 TRTC 会让后端侧 lease 继续有效到自然过期，货主端仍看到可观看状态。
 *
 * 收到 STOP_LIVE 命令的路径（StopLiveHandler）走自己的同步 ack + 事件回执，不经本类。
 */
@Singleton
class LiveStopCoordinator @Inject constructor(
    private val liveService: DjiTrtcLiveService,
    private val eventPublisher: WsEventPublisher,
    private val dispatchLivePublisher: DispatchLivePublisher
) {

    /**
     * 停止推流并回执 LIVE_STOPPED，同时结束订单直播会话。
     * @return true = 存在推流会话或订单直播会话且已执行停止；false = 本就未在推流（无动作）。
     */
    fun stopStreamingAndNotify(): Boolean {
        // Order live owns both the TRTC session and a server-side lease; stop it first so its own
        // heartbeat loop terminates the session through the API rather than expiring on the server.
        val orderLiveStopped = dispatchLivePublisher.stopNow()

        if (!liveService.isSessionActive) {
            return orderLiveStopped
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
