package com.fuwaki.djifly.platform.ws.command.di

import com.fuwaki.djifly.BuildConfig
import com.fuwaki.djifly.platform.livestream.DjiTrtcLiveConfig
import com.fuwaki.djifly.platform.livestream.DjiTrtcLiveService
import com.fuwaki.djifly.platform.ws.command.callback.WsCommandCallbackResult
import com.fuwaki.djifly.platform.ws.command.callback.WsCommandCallbacks
import com.fuwaki.djifly.platform.ws.command.handler.PingHandler
import com.fuwaki.djifly.platform.ws.command.handler.StartLiveHandler
import com.fuwaki.djifly.platform.ws.command.handler.WsMessageHandler
import com.fuwaki.djifly.platform.ws.protocol.PlatformStartLivePayload
import com.google.gson.Gson
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
                        roomId = payload.roomId
                    )
                )
                WsCommandCallbackResult.Success(payload.roomId)
            } catch (error: Exception) {
                WsCommandCallbackResult.Error("LIVE_START_FAILED", error.message ?: "启动图传失败")
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
        StartLiveHandler(callbacks, gson)
    )
}
