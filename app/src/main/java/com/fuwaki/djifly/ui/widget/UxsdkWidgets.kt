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
 *
 * This file contains AndroidView wrappers for DJI UXSDK components,
 * allowing them to be used in Jetpack Compose layouts.
 */
@Composable
fun CameraControlsComposeWidget(
    cameraIndex: ComponentIndexType,
    lensType: CameraLensType,
    modifier: Modifier = Modifier,
    isPhotoVideoSwitchVisible: Boolean = true,
    isCameraCaptureVisible: Boolean = true,
) {
    // 1. 去掉 fillMaxSize()，给一个合理的控制面板宽度，比如 60dp
    Column(
        modifier = modifier
            .width(60.dp) // 限制宽度为正常无人机 UI 面板的宽度
            .wrapContentHeight() // 高度由内部按钮撑开
            .clip(RoundedCornerShape(8.dp)) // (可选) 加个圆角更好看
            .background(Color(0xCC000000)) // 半透明黑底，或者用你原来的 0xFF1A1A1A
            .padding(vertical = 12.dp, horizontal = 4.dp), // 上下左右留点内边距
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp) // 2. 用统一的间距代替 weight
    ) {

        // 拍照/录像切换按钮
        if (isPhotoVideoSwitchVisible) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth() // 填满 60dp 的宽度 (减去 padding)
                    .aspectRatio(1f), // 3. 关键：强制宽高比 1:1，保证按钮是正方形/圆形，绝不变形
                factory = { context ->
                    PhotoVideoSwitchWidget(context).apply {
                        // 内部原生 View 填满 Compose 分配给它的正方形空间
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    view.updateCameraSource(cameraIndex, lensType)
                }
            )
        }

        // 快门按钮
        if (isCameraCaptureVisible) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f), // 同样强制 1:1，保证两个按钮一模一样大
                factory = { context ->
                    CameraCaptureWidget(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    view.updateCameraSource(cameraIndex, lensType)
                }
            )
        }
    }
}
/**
 * FPV Video Stream Widget
 * Displays the primary camera feed from the drone
 */
@Composable
fun FpvWidget(
    modifier: Modifier = Modifier,
    cameraIndex: ComponentIndexType = ComponentIndexType.LEFT_OR_MAIN,
    enableCenterPoint: Boolean = true,
    enableGridLines: Boolean = true
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            FPVWidget(context).apply {
                updateVideoSource(cameraIndex)
                isCenterPointEnabled = enableCenterPoint
                isGridLinesEnabled = enableGridLines
            }
        },
        update = { widget ->
            widget.updateVideoSource(cameraIndex)
            widget.isCenterPointEnabled = enableCenterPoint
            widget.isGridLinesEnabled = enableGridLines
        }
    )
}

/**
 * Camera Controls Widget
 */
@Composable
fun CameraControlsWidget(
    modifier: Modifier = Modifier
) {
    AndroidView<CameraControlsWidget>(
        modifier = modifier,
        factory = { context ->
            CameraControlsWidget(context)
        }
    )
}

/**
 * Take Off Widget
 */
@Composable
fun TakeOffWidget(
    modifier: Modifier = Modifier
) {
    AndroidView<TakeOffWidget>(
        modifier = modifier,
        factory = { context ->
            TakeOffWidget(context)
        }
    )
}

/**
 * Return Home Widget
 */
@Composable
fun ReturnHomeWidget(
    modifier: Modifier = Modifier
) {
    AndroidView<ReturnHomeWidget>(
        modifier = modifier,
        factory = { context ->
            ReturnHomeWidget(context)
        }
    )
}

/**
 * Top Bar Panel Widget
 */
@Composable
fun TopBarPanelWidget(
    modifier: Modifier = Modifier,
    onSettingClick: () -> Unit = {}
) {
    AndroidView<TopBarPanelWidget>(
        modifier = modifier,
        factory = { context ->
            TopBarPanelWidget(context).apply {
                settingWidget?.setOnClickListener { onSettingClick() }
            }
        },
        update = { widget ->
            widget.settingWidget?.setOnClickListener { onSettingClick() }
        }
    )
}

/**
 * Remaining Flight Time Widget
 */
@Composable
fun RemainingFlightTimeWidget(
    modifier: Modifier = Modifier
) {
    AndroidView<RemainingFlightTimeWidget>(
        modifier = modifier,
        factory = { context ->
            RemainingFlightTimeWidget(context)
        }
    )
}

/**
 * Horizontal Situation Indicator Widget
 */
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

/**
 * Lens Control Widget
 */
@Composable
fun LensControlWidget(
    modifier: Modifier = Modifier
) {
    AndroidView<LensControlWidget>(
        modifier = modifier,
        factory = { context ->
            LensControlWidget(context)
        }
    )
}

/**
 * Focus Mode Widget
 */
@Composable
fun FocusModeWidget(
    modifier: Modifier = Modifier
) {
    AndroidView<FocusModeWidget>(
        modifier = modifier,
        factory = { context ->
            FocusModeWidget(context)
        }
    )
}

/**
 * Focus/Exposure Switch Widget
 */
@Composable
fun FocusExposureSwitchWidget(
    modifier: Modifier = Modifier
) {
    AndroidView<FocusExposureSwitchWidget>(
        modifier = modifier,
        factory = { context ->
            FocusExposureSwitchWidget(context)
        }
    )
}

/**
 * Auto Exposure Lock Widget
 */
@Composable
fun AutoExposureLockWidget(
    modifier: Modifier = Modifier
) {
    AndroidView<AutoExposureLockWidget>(
        modifier = modifier,
        factory = { context ->
            AutoExposureLockWidget(context)
        }
    )
}
