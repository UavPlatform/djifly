package com.fuwaki.djifly.ui.widget

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.ux.cameracore.widget.cameracontrols.CameraControlsWidget
import dji.v5.ux.cameracore.widget.cameracontrols.lenscontrol.LensControlWidget
import dji.v5.ux.cameracore.widget.focusmode.FocusModeWidget
import dji.v5.ux.cameracore.widget.focusexposureswitch.FocusExposureSwitchWidget
import dji.v5.ux.cameracore.widget.autoexposurelock.AutoExposureLockWidget
import dji.v5.ux.core.panel.topbar.TopBarPanelWidget
import dji.v5.ux.core.widget.remainingflighttime.RemainingFlightTimeWidget
import dji.v5.ux.core.widget.hsi.HorizontalSituationIndicatorWidget
import dji.v5.ux.flight.takeoff.TakeOffWidget
import dji.v5.ux.flight.returnhome.ReturnHomeWidget
import dji.v5.ux.core.widget.fpv.FPVWidget

/**
 * UXSDK Component Wrappers for Compose
 *
 * This file contains AndroidView wrappers for DJI UXSDK components,
 * allowing them to be used in Jetpack Compose layouts.
 */

/**
 * FPV Video Stream Widget
 * Displays the primary camera feed from the drone
 *
 * @param modifier Layout modifier
 * @param cameraIndex Which camera to display (LEFT_OR_MAIN, RIGHT, etc.)
 * @param enableCenterPoint Whether to show center crosshair
 * @param enableGridLines Whether to show grid overlay
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
 * Provides capture controls (photo/video recording)
 *
 * @param modifier Layout modifier
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
 * Button for automatic takeoff
 *
 * @param modifier Layout modifier
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
 * Button for one-key return to home
 *
 * @param modifier Layout modifier
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
 * Status bar showing battery, signal strength, GPS, etc.
 *
 * @param modifier Layout modifier
 * @param onSettingClick Callback when settings button is clicked
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
 * Displays estimated remaining flight time
 *
 * @param modifier Layout modifier
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
 * Attitude indicator showing horizon and aircraft orientation
 *
 * @param modifier Layout modifier
 */
@Composable
fun HorizontalSituationIndicatorWidget(
    modifier: Modifier = Modifier
) {
    AndroidView<HorizontalSituationIndicatorWidget>(
        modifier = modifier,
        factory = { context ->
            HorizontalSituationIndicatorWidget(context)
        }
    )
}

/**
 * Lens Control Widget
 * Controls for switching between camera lenses (zoom/wide)
 *
 * @param modifier Layout modifier
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
 * Controls for focus mode (Auto/Manual)
 *
 * @param modifier Layout modifier
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
 * Toggle between focus and exposure adjustment modes
 *
 * @param modifier Layout modifier
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
 * Lock/unlock auto exposure
 *
 * @param modifier Layout modifier
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
