package com.fuwaki.djifly.platform.livestream

import com.tencent.trtc.TRTCCloudDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归：观众端画面方向由编码方向决定，编码档位必须跟随 DJI 帧实际方向。
 * 见 REQ-PILOT-EXEC-004 验收 2（需求方与监管员看到的画面应与飞手端预览一致）。
 */
class DjiTrtcEncoderProfileTest {

    @Test
    fun landscapeFrameEncodesAsLandscape() {
        val param = DjiTrtcEncoderProfile.forFrame(1280, 720)

        assertEquals(TRTCCloudDef.TRTC_VIDEO_RESOLUTION_1280_720, param.videoResolution)
        assertEquals(TRTCCloudDef.TRTC_VIDEO_RESOLUTION_MODE_LANDSCAPE, param.videoResolutionMode)
        assertEquals(DjiTrtcEncoderProfile.FPS, param.videoFps)
        assertTrue(param.videoBitrate > 0)
        assertTrue(param.minVideoBitrate in 1 until param.videoBitrate)
    }

    @Test
    fun portraitFrameEncodesAsPortrait() {
        val param = DjiTrtcEncoderProfile.forFrame(720, 1280)

        assertEquals(TRTCCloudDef.TRTC_VIDEO_RESOLUTION_1280_720, param.videoResolution)
        assertEquals(TRTCCloudDef.TRTC_VIDEO_RESOLUTION_MODE_PORTRAIT, param.videoResolutionMode)
    }

    @Test
    fun fourByThreeFrameKeepsFourByThreeTier() {
        val param = DjiTrtcEncoderProfile.forFrame(960, 720)

        assertEquals(TRTCCloudDef.TRTC_VIDEO_RESOLUTION_960_720, param.videoResolution)
        assertEquals(TRTCCloudDef.TRTC_VIDEO_RESOLUTION_MODE_LANDSCAPE, param.videoResolutionMode)
    }

    @Test
    fun squareFrameUsesSquareTier() {
        assertEquals(
            TRTCCloudDef.TRTC_VIDEO_RESOLUTION_480_480,
            DjiTrtcEncoderProfile.resolutionFor(480, 480)
        )
    }

    @Test
    fun resolutionTiersStayWithinKnownEncodedSizes() {
        val cases = mapOf(
            (1920 to 1080) to TRTCCloudDef.TRTC_VIDEO_RESOLUTION_1280_720,
            (960 to 540) to TRTCCloudDef.TRTC_VIDEO_RESOLUTION_960_540,
            (640 to 360) to TRTCCloudDef.TRTC_VIDEO_RESOLUTION_640_360,
            (640 to 480) to TRTCCloudDef.TRTC_VIDEO_RESOLUTION_640_480,
        )

        cases.forEach { (size, expected) ->
            assertEquals("${size.first}x${size.second}", expected, DjiTrtcEncoderProfile.resolutionFor(size.first, size.second))
        }
    }
}
