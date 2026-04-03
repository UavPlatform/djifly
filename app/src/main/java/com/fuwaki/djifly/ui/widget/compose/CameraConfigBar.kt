package com.fuwaki.djifly.ui.widget.compose

import android.view.ViewGroup
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dji.v5.ux.cameracore.widget.autoexposurelock.AutoExposureLockWidget
import dji.v5.ux.visualcamera.ev.CameraConfigEVWidget
import dji.v5.ux.visualcamera.iso.CameraConfigISOAndEIWidget
import dji.v5.ux.visualcamera.shutter.CameraConfigShutterWidget
import dji.v5.ux.visualcamera.storage.CameraConfigStorageWidget
import dji.v5.ux.visualcamera.wb.CameraConfigWBWidget

/**
 * 相机参数栏组件
 * 封装了 DJI UXSDK 的多个相机配置组件：AE Lock、存储、白平衡、曝光补偿、ISO/EI、快门速度
 */
@Composable
fun CameraConfigBar(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(46.dp)
            .background(
                color = Color(0xD90A0F14),
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AndroidView(
            factory = { context ->
                AutoExposureLockWidget(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.size(28.dp)
        )

        AndroidViewWidget { CameraConfigStorageWidget(it) }
        AndroidViewWidget { CameraConfigWBWidget(it) }
        AndroidViewWidget { CameraConfigEVWidget(it) }
        AndroidViewWidget { CameraConfigISOAndEIWidget(it) }
        AndroidViewWidget { CameraConfigShutterWidget(it) }
    }
}

@Composable
private fun <T : android.view.View> AndroidViewWidget(factory: (android.content.Context) -> T) {
    AndroidView(
        factory = { context ->
            factory(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = Modifier.fillMaxHeight()
    )
}
