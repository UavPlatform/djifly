package com.fuwaki.djifly.platform.livestream

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import com.tencent.trtc.TRTCCloud
import com.tencent.trtc.TRTCCloudDef
import com.tencent.trtc.TRTCCloudListener
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.manager.datacenter.MediaDataCenter
import dji.v5.manager.interfaces.ICameraStreamManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class DjiTrtcLivePhase {
    Idle,
    Starting,
    Streaming,
    Error
}

data class DjiTrtcLiveStatus(
    val phase: DjiTrtcLivePhase = DjiTrtcLivePhase.Idle,
    val roomId: String = "",
    val userId: String = "",
    val cameraIndex: ComponentIndexType = ComponentIndexType.UNKNOWN,
    val frameFormat: ICameraStreamManager.FrameFormat? = null,
    val streamType: Int = TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_BIG,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val firstVideoFrameSent: Boolean = false,
    val enterRoomResult: Long? = null,
    val lastErrorCode: Int? = null,
    val lastMessage: String = ""
)

data class DjiTrtcLiveConfig(
    val sdkAppId: Int,
    val userId: String,
    val userSig: String,
    val roomId: String,
    val cameraIndex: ComponentIndexType = ComponentIndexType.LEFT_OR_MAIN,
    val frameFormat: ICameraStreamManager.FrameFormat = ICameraStreamManager.FrameFormat.NV21,
    val streamType: Int = TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_BIG,
    val appScene: Int = TRTCCloudDef.TRTC_APP_SCENE_LIVE,
    val role: Int = TRTCCloudDef.TRTCRoleAnchor,
    val rotation: Int = TRTCCloudDef.TRTC_VIDEO_ROTATION_270,
    val enableMicrophoneAudio: Boolean = false,
    val audioQuality: Int = TRTCCloudDef.TRTC_AUDIO_QUALITY_DEFAULT,
    val keepAliveDecoding: Boolean = true,
    val privateMapKey: String? = null,
    val streamId: String? = null,
    val businessInfo: String? = null,
)

@Singleton
class DjiTrtcLiveService @Inject constructor(
    @ApplicationContext context: Context
) {

    private val tag = "DjiTrtcLiveService"
    private val appContext = context.applicationContext
    private val cameraStreamManager: ICameraStreamManager = MediaDataCenter.getInstance().cameraStreamManager
    private val _status = MutableStateFlow(DjiTrtcLiveStatus())

    val status: StateFlow<DjiTrtcLiveStatus> = _status.asStateFlow()

    @Volatile
    private var trtcCloud: TRTCCloud? = null

    @Volatile
    private var currentConfig: DjiTrtcLiveConfig? = null

    private val trtcListener = object : TRTCCloudListener() {
        override fun onEnterRoom(result: Long) {
            if (currentConfig == null) {
                return
            }
            updateStatus {
                copy(
                    enterRoomResult = result,
                    lastErrorCode = null,
                    lastMessage = "Entered TRTC room"
                )
            }
        }

        override fun onSendFirstLocalVideoFrame(streamType: Int) {
            val config = currentConfig ?: return
            if (streamType != config.streamType) {
                return
            }
            updateStatus {
                copy(
                    phase = DjiTrtcLivePhase.Streaming,
                    firstVideoFrameSent = true,
                    lastErrorCode = null,
                    lastMessage = "First DJI video frame sent to TRTC"
                )
            }
        }

        override fun onExitRoom(reason: Int) {
            Log.i(tag, "Exited TRTC room, reason=$reason")
        }

        override fun onWarning(code: Int, message: String?, extraInfo: Bundle?) {
            if (currentConfig == null) {
                return
            }
            val warningMessage = buildMessage("TRTC warning", code, message)
            Log.w(tag, warningMessage)
            updateStatus { copy(lastMessage = warningMessage) }
        }

        override fun onError(code: Int, message: String?, extraInfo: Bundle?) {
            if (currentConfig == null) {
                return
            }
            val errorMessage = buildMessage("TRTC error", code, message)
            Log.e(tag, errorMessage)
            updateStatus {
                copy(
                    phase = DjiTrtcLivePhase.Error,
                    lastErrorCode = code,
                    lastMessage = errorMessage
                )
            }
        }
    }

    private val frameListener = object : ICameraStreamManager.CameraFrameListener {
        override fun onFrame(
            frameData: ByteArray,
            offset: Int,
            length: Int,
            width: Int,
            height: Int,
            format: ICameraStreamManager.FrameFormat
        ) {
            val cloud = trtcCloud ?: return
            val config = currentConfig ?: return
            val videoFrame = TRTCCloudDef.TRTCVideoFrame().apply {
                pixelFormat = mapFrameFormat(format)
                bufferType = TRTCCloudDef.TRTC_VIDEO_BUFFER_TYPE_BYTE_ARRAY
                data = frameData.copyOfRange(offset, offset + length)
                this.width = width
                this.height = height
                timestamp = SystemClock.elapsedRealtimeNanos() / 1_000_000L
                rotation = config.rotation
            }

            cloud.sendCustomVideoData(config.streamType, videoFrame)

            val current = _status.value
            if (current.videoWidth != width || current.videoHeight != height || current.frameFormat != format) {
                updateStatus {
                    copy(
                        videoWidth = width,
                        videoHeight = height,
                        frameFormat = format,
                        lastMessage = "Sending DJI frames to TRTC"
                    )
                }
            }
        }
    }

    @Synchronized
    fun start(config: DjiTrtcLiveConfig) {
        validateConfig(config)
        stop()

        currentConfig = config
        updateStatus {
            DjiTrtcLiveStatus(
                phase = DjiTrtcLivePhase.Starting,
                roomId = config.roomId,
                userId = config.userId,
                cameraIndex = config.cameraIndex,
                frameFormat = config.frameFormat,
                streamType = config.streamType,
                lastMessage = "Starting DJI -> TRTC stream"
            )
        }

        try {
            val cloud = TRTCCloud.sharedInstance(appContext)
            trtcCloud = cloud
            cloud.setListener(trtcListener)
            cloud.enableCustomVideoCapture(config.streamType, true)

            if (config.enableMicrophoneAudio) {
                cloud.muteLocalAudio(false)
                cloud.startLocalAudio(config.audioQuality)
            } else {
                cloud.stopLocalAudio()
                cloud.muteLocalAudio(true)
            }

            cloud.enterRoom(buildTrtcParams(config), config.appScene)

            cameraStreamManager.setKeepAliveDecoding(config.keepAliveDecoding)
            cameraStreamManager.removeFrameListener(frameListener)
            cameraStreamManager.addFrameListener(config.cameraIndex, config.frameFormat, frameListener)
        } catch (throwable: Throwable) {
            Log.e(tag, "Failed to start DJI -> TRTC stream", throwable)
            updateStatus {
                copy(
                    phase = DjiTrtcLivePhase.Error,
                    lastMessage = throwable.message ?: "Failed to start DJI -> TRTC stream"
                )
            }
            stopInternal()
        }
    }

    @Synchronized
    fun stop() {
        val hadSession = trtcCloud != null || currentConfig != null
        stopInternal()
        if (hadSession) {
            updateStatus { DjiTrtcLiveStatus(lastMessage = "DJI -> TRTC stream stopped") }
        }
    }

    @Synchronized
    fun switchCamera(cameraIndex: ComponentIndexType) {
        require(cameraIndex != ComponentIndexType.UNKNOWN) { "cameraIndex cannot be UNKNOWN" }

        val config = currentConfig ?: return
        val updatedConfig = config.copy(cameraIndex = cameraIndex)
        currentConfig = updatedConfig

        if (trtcCloud != null) {
            cameraStreamManager.removeFrameListener(frameListener)
            cameraStreamManager.addFrameListener(cameraIndex, updatedConfig.frameFormat, frameListener)
        }

        updateStatus {
            copy(
                cameraIndex = cameraIndex,
                lastMessage = "Switched DJI camera source to ${cameraIndex.name}"
            )
        }
    }

    @Synchronized
    fun setMicrophoneEnabled(enabled: Boolean) {
        val config = currentConfig ?: return
        currentConfig = config.copy(enableMicrophoneAudio = enabled)

        trtcCloud?.let { cloud ->
            if (enabled) {
                cloud.muteLocalAudio(false)
                cloud.startLocalAudio(config.audioQuality)
            } else {
                cloud.stopLocalAudio()
                cloud.muteLocalAudio(true)
            }
        }

        updateStatus {
            copy(lastMessage = if (enabled) "Microphone enabled" else "Microphone disabled")
        }
    }

    @Synchronized
    fun release() {
        stop()
    }

    @Synchronized
    private fun stopInternal() {
        cameraStreamManager.removeFrameListener(frameListener)
        cameraStreamManager.setKeepAliveDecoding(false)

        trtcCloud?.let { cloud ->
            try {
                cloud.enableCustomVideoCapture(
                    currentConfig?.streamType ?: TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_BIG,
                    false
                )
                cloud.stopLocalAudio()
                cloud.muteLocalAudio(true)
                cloud.setListener(null)
                cloud.exitRoom()
            } catch (throwable: Throwable) {
                Log.w(tag, "Failed while stopping TRTC session", throwable)
            }
        }

        trtcCloud = null
        currentConfig = null
        TRTCCloud.destroySharedInstance()
    }

    private fun buildTrtcParams(config: DjiTrtcLiveConfig): TRTCCloudDef.TRTCParams {
        return TRTCCloudDef.TRTCParams().apply {
            sdkAppId = config.sdkAppId
            userId = config.userId
            userSig = config.userSig
            strRoomId = config.roomId
            role = config.role
            privateMapKey = config.privateMapKey
            streamId = config.streamId
            businessInfo = config.businessInfo
        }
    }

    private fun validateConfig(config: DjiTrtcLiveConfig) {
        require(config.sdkAppId > 0) { "sdkAppId must be greater than 0" }
        require(config.userId.isNotBlank()) { "userId cannot be blank" }
        require(config.userSig.isNotBlank()) { "userSig cannot be blank" }
        require(config.roomId.isNotBlank()) { "roomId cannot be blank" }
        require(config.cameraIndex != ComponentIndexType.UNKNOWN) { "cameraIndex cannot be UNKNOWN" }
        mapFrameFormat(config.frameFormat)
    }

    private fun mapFrameFormat(format: ICameraStreamManager.FrameFormat): Int {
        return when (format) {
            ICameraStreamManager.FrameFormat.YUV420_888 -> TRTCCloudDef.TRTC_VIDEO_PIXEL_FORMAT_I420
            ICameraStreamManager.FrameFormat.NV21 -> TRTCCloudDef.TRTC_VIDEO_PIXEL_FORMAT_NV21
            ICameraStreamManager.FrameFormat.RGBA_8888 -> TRTCCloudDef.TRTC_VIDEO_PIXEL_FORMAT_RGBA
            else -> error("Unsupported DJI frame format for TRTC custom capture: $format")
        }
    }

    private fun buildMessage(prefix: String, code: Int, message: String?): String {
        return "$prefix(code=$code): ${message.orEmpty().ifBlank { "no detail" }}"
    }

    private fun updateStatus(update: DjiTrtcLiveStatus.() -> DjiTrtcLiveStatus) {
        _status.value = _status.value.update()
    }
}
