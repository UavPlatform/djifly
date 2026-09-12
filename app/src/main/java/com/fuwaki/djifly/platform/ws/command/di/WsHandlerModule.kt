package com.fuwaki.djifly.platform.ws.command.di

import com.fuwaki.djifly.BuildConfig
import com.fuwaki.djifly.platform.livestream.DjiTrtcLiveConfig
import com.fuwaki.djifly.platform.livestream.DjiTrtcLiveService
import com.fuwaki.djifly.platform.ws.command.callback.WsCommandCallbackResult
import com.fuwaki.djifly.platform.ws.command.callback.WsCommandCallbacks
import com.fuwaki.djifly.platform.ws.command.handler.PingHandler
import com.fuwaki.djifly.platform.ws.command.handler.PongHandler
import com.fuwaki.djifly.platform.ws.command.handler.StartLiveHandler
import com.fuwaki.djifly.platform.ws.command.handler.StopLiveHandler
import com.fuwaki.djifly.platform.ws.command.handler.WsMessageHandler
import com.fuwaki.djifly.platform.ws.protocol.PlatformStartLivePayload
import com.google.gson.Gson
import com.tencent.trtc.TRTCCloudDef
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WsHandlerModule {

    @Provides
    @Singleton
    fun provideWsCommandCallbacks(
        liveService: DjiTrtcLiveService
    ): WsCommandCallbacks = object : WsCommandCallbacks {
        override suspend fun onStartLive(payload: PlatformStartLivePayload): WsCommandCallbackResult {
            return try {
                liveService.start(
                    DjiTrtcLiveConfig(
                        sdkAppId = BuildConfig.TRTC_SDK_APP_ID.toInt(),
                        userId = payload.userId,
                        userSig = payload.userSig,
                        roomId = payload.roomId,
                        rotation = TRTCCloudDef.TRTC_VIDEO_ROTATION_270
                    )
                )
                WsCommandCallbackResult.Success(payload.roomId)
            } catch (error: Exception) {
                WsCommandCallbackResult.Error("LIVE_START_FAILED", error.message ?: "启动图传失败")
            }
        }

        override suspend fun onStopLive(): WsCommandCallbackResult {
            return try {
                val wasActive = liveService.isSessionActive
                liveService.stop()
                if (wasActive) {
                    WsCommandCallbackResult.Stopped("已停止推流并退房")
                } else {
                    // 幂等：本就未在推流，ack success=true 让后端安全进入终态
                    WsCommandCallbackResult.Stopped("直播未在运行")
                }
            } catch (error: Exception) {
                WsCommandCallbackResult.Error("LIVE_STOP_REJECTED", error.message ?: "停止推流失败")
            }
        }
    }

    @Provides
    @Singleton
    fun provideWsMessageHandlers(
        callbacks: WsCommandCallbacks,
        gson: Gson
    ): Set<WsMessageHandler> = setOf(
        PingHandler(),
        PongHandler(),
        StartLiveHandler(callbacks, gson),
        StopLiveHandler(callbacks)
    )
}
