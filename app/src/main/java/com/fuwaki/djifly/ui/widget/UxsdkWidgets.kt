package com.fuwaki.djifly.ui.widget

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dji.sdk.keyvalue.value.common.CameraLensType
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.ux.cameracore.widget.cameracontrols.CameraControlsWidget
import dji.v5.ux.cameracore.widget.cameracontrols.camerasettingsindicator.CameraSettingsMenuIndicatorWidget
import dji.v5.ux.cameracore.widget.cameracontrols.exposuresettingsindicator.ExposureSettingsIndicatorWidget
import dji.v5.ux.cameracore.widget.cameracontrols.lenscontrol.LensControlWidget
import dji.v5.ux.cameracore.widget.cameracontrols.photovideoswitch.PhotoVideoSwitchWidget
import dji.v5.ux.cameracore.widget.cameracapture.CameraCaptureWidget
import dji.v5.ux.cameracore.widget.focusmode.FocusModeWidget
import dji.v5.ux.cameracore.widget.focusexposureswitch.FocusExposureSwitchWidget
import dji.v5.ux.cameracore.widget.autoexposurelock.AutoExposureLockWidget
import dji.v5.ux.core.panel.topbar.TopBarPanelWidget
import dji.v5.ux.core.widget.remainingflighttime.RemainingFlightTimeWidget
import dji.v5.ux.core.widget.hsi.HorizontalSituationIndicatorWidget
import dji.v5.ux.flight.takeoff.TakeOffWidget
import dji.v5.ux.flight.returnhome.ReturnHomeWidget
import dji.v5.ux.core.widget.fpv.FPVWidget
import dji.v5.ux.core.communication.OnStateChangeCallback

/**
 * UXSDK Component Wrappers for Compose
 */
@Composable
fun CameraControlsComposeWidget(
    cameraIndex: ComponentIndexType,
    lensType: CameraLensType,
    modifier: Modifier = Modifier,
    isPhotoVideoSwitchVisible: Boolean = true,
    isCameraCaptureVisible: Boolean = true,
) {
    Column(
        modifier = modifier
            .width(60.dp)
            .wrapContentHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xCC000000))
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isPhotoVideoSwitchVisible) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                factory = { context ->
                    PhotoVideoSwitchWidget(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view -> view.updateCameraSource(cameraIndex, lensType) }
            )
        }
        if (isCameraCaptureVisible) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                factory = { context ->
                    CameraCaptureWidget(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view -> view.updateCameraSource(cameraIndex, lensType) }
            )
        }
    }
}

/**
 * FPV Video Stream Widget
 */
@Composable
fun FpvWidget(
    modifier: Modifier = Modifier,
    cameraIndex: ComponentIndexType = ComponentIndexType.LEFT_OR_MAIN,
    showCameraName: Boolean = false, // 强制不显示 DJI_MINI_4_PRO
    showCameraSide: Boolean = false, // 强制不显示 LEFT_OR_MAIN
    enableCenterPoint: Boolean = false,
    enableGridLines: Boolean = false
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            FPVWidget(context).apply {
                updateVideoSource(cameraIndex)
                // 强制隐藏机型和位置信息
                isCameraSourceNameVisible = showCameraName
                isCameraSourceSideVisible = showCameraSide
                isCenterPointEnabled = enableCenterPoint
                isGridLinesEnabled = enableGridLines
            }
        },
        update = { widget ->
            widget.updateVideoSource(cameraIndex)
            // 在 update 中再次强制刷新，防止 Model 异步刷新覆盖配置
            if (widget.isCameraSourceNameVisible != showCameraName) {
                widget.isCameraSourceNameVisible = showCameraName
            }
            if (widget.isCameraSourceSideVisible != showCameraSide) {
                widget.isCameraSourceSideVisible = showCameraSide
            }
            widget.isCenterPointEnabled = enableCenterPoint
            widget.isGridLinesEnabled = enableGridLines
        }
    )
}

@Composable
fun HorizontalSituationIndicatorWidget(
    modifier: Modifier = Modifier
) {
    AndroidView<HorizontalSituationIndicatorWidget>(
        modifier = modifier,
        factory = { context ->
            HorizontalSituationIndicatorWidget(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        }
    )
}

@Composable
fun LensControlWidget(modifier: Modifier = Modifier) {
    AndroidView<LensControlWidget>(modifier = modifier, factory = { context -> LensControlWidget(context) })
}

@Composable
fun FocusModeWidget(modifier: Modifier = Modifier) {
    AndroidView<FocusModeWidget>(modifier = modifier, factory = { context -> FocusModeWidget(context) })
}

@Composable
fun AutoExposureLockWidget(modifier: Modifier = Modifier) {
    AndroidView<AutoExposureLockWidget>(modifier = modifier, factory = { context -> AutoExposureLockWidget(context) })
}
