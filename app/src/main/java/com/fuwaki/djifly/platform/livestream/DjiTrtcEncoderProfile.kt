package com.fuwaki.djifly.platform.livestream

import com.tencent.trtc.TRTCCloudDef

/**
 * 由 DJI 解码帧尺寸推导 TRTC 编码参数。
 *
 * TRTC 的 `TRTCVideoEncParam.videoResolutionMode` 决定编码画面是横屏还是竖屏：
 * 例如 `1280_720 + LANDSCAPE = 1280x720`，`1280_720 + PORTRAIT = 720x1280`。
 * 若编码方向与实际帧方向相反，SDK 会为避免画幅不符而把画面旋转 90°，
 * 观众端就会看到「竖着的横屏画面」。自定义采集下必须显式按帧尺寸设置方向。
 */
internal object DjiTrtcEncoderProfile {

    const val FPS = 20

    fun forFrame(width: Int, height: Int): TRTCCloudDef.TRTCVideoEncParam {
        require(width > 0 && height > 0) { "frame size must be positive: ${width}x$height" }
        val resolution = resolutionFor(width, height)
        val shortEdge = minOf(width, height)
        return TRTCCloudDef.TRTCVideoEncParam().apply {
            videoResolution = resolution
            videoResolutionMode = if (width >= height) {
                TRTCCloudDef.TRTC_VIDEO_RESOLUTION_MODE_LANDSCAPE
            } else {
                TRTCCloudDef.TRTC_VIDEO_RESOLUTION_MODE_PORTRAIT
            }
            videoFps = FPS
            videoBitrate = bitrateFor(shortEdge)
            minVideoBitrate = videoBitrate * 6 / 10
            enableAdjustRes = false
        }
    }

    /**
     * 按画幅比例选 16:9 / 4:3 / 1:1 档位，再按短边选清晰度。
     * 长边封顶 720p：推流走移动网络，1080p 档位码率会把上行打满。
     */
    internal fun resolutionFor(width: Int, height: Int): Int {
        val longEdge = maxOf(width, height)
        val shortEdge = minOf(width, height)
        val ratio = longEdge.toFloat() / shortEdge
        return when {
            ratio < 1.2f -> when {
                shortEdge >= 480 -> TRTCCloudDef.TRTC_VIDEO_RESOLUTION_480_480
                shortEdge >= 270 -> TRTCCloudDef.TRTC_VIDEO_RESOLUTION_270_270
                else -> TRTCCloudDef.TRTC_VIDEO_RESOLUTION_160_160
            }
            ratio < 1.45f -> when {
                shortEdge >= 720 -> TRTCCloudDef.TRTC_VIDEO_RESOLUTION_960_720
                shortEdge >= 480 -> TRTCCloudDef.TRTC_VIDEO_RESOLUTION_640_480
                shortEdge >= 360 -> TRTCCloudDef.TRTC_VIDEO_RESOLUTION_480_360
                shortEdge >= 240 -> TRTCCloudDef.TRTC_VIDEO_RESOLUTION_320_240
                else -> TRTCCloudDef.TRTC_VIDEO_RESOLUTION_160_120
            }
            else -> when {
                shortEdge >= 720 -> TRTCCloudDef.TRTC_VIDEO_RESOLUTION_1280_720
                shortEdge >= 540 -> TRTCCloudDef.TRTC_VIDEO_RESOLUTION_960_540
                shortEdge >= 360 -> TRTCCloudDef.TRTC_VIDEO_RESOLUTION_640_360
                shortEdge >= 180 -> TRTCCloudDef.TRTC_VIDEO_RESOLUTION_320_180
                else -> TRTCCloudDef.TRTC_VIDEO_RESOLUTION_160_90
            }
        }
    }

    /** LIVE 场景推荐码率（kbps），随分辨率档位变化。 */
    private fun bitrateFor(shortEdge: Int): Int = when {
        shortEdge >= 720 -> 1800
        shortEdge >= 540 -> 1300
        shortEdge >= 480 -> 900
        shortEdge >= 360 -> 600
        shortEdge >= 270 -> 450
        shortEdge >= 240 -> 375
        shortEdge >= 180 -> 300
        else -> 200
    }
}
