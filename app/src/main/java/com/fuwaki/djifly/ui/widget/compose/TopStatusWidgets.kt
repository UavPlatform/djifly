package com.fuwaki.djifly.ui.widget.compose

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import com.fuwaki.djifly.sdk.DjiSdkManager
import com.fuwaki.djifly.sdk.FlightDataState
import dji.v5.ux.core.widget.systemstatus.SystemStatusWidget

/**
 * 辅助函数：从 Context 中找寻 FragmentActivity
 */
private fun Context.findFragmentActivity(): FragmentActivity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is FragmentActivity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

/**
 * 接入真实数据的自定义 Compose 顶部状态栏
 */
@Composable
fun TopStatusRow(
    sdkManager: DjiSdkManager,
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit = {}
) {
    val sdkStatus by sdkManager.sdkStatus.collectAsState()
    val flightData = sdkStatus.flightData

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 0. 系统状态 (向左侧伸展)
        AndroidView(
            factory = { context ->
                val activity = context.findFragmentActivity()
                    ?: throw IllegalStateException("当前 Context 无法转换为 FragmentActivity")
                
                SystemStatusWidget(activity)
            },
            modifier = Modifier
                .weight(1f) // 让它占据左侧所有剩余空间
                .height(28.dp)
        )

        // 1. 避障状态
        StatusIconWithText(
            text = "Vision", 
            color = if (flightData.isVisionSystemHealthy) Color(0xFF4CAF50) else Color(0xFFF44336)
        )
        
        Spacer(modifier = Modifier.width(12.dp))

        // 2. GPS 卫星
        StatusIconWithText(text = "${flightData.gpsSatelliteCount}", color = Color.White)
        
        Spacer(modifier = Modifier.width(12.dp))

        // 3. 遥控器信号 (RC)
        SignalBars(strength = convertQualityToBars(flightData.uplinkQuality), color = Color.White)
        
        Spacer(modifier = Modifier.width(12.dp))

        // 4. 图传信号 (HD)
        Text(
            text = "HD", 
            color = Color.White, 
            fontSize = 10.sp, 
            fontWeight = FontWeight.Bold, 
            modifier = Modifier
                .background(Color.White.copy(0.2f), RoundedCornerShape(2.dp))
                .padding(horizontal = 2.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        SignalBars(strength = convertQualityToBars(flightData.downlinkQuality), color = Color(0xFF2196F3))

        Spacer(modifier = Modifier.width(16.dp))

        // 5. 电池百分比
        BatteryStatus(percentage = flightData.batteryPercentage)

        Spacer(modifier = Modifier.width(8.dp))

        // 6. 设置按钮
        IconButton(onClick = onSettingsClick, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

private fun convertQualityToBars(quality: Int): Int {
    return when {
        quality > 80 -> 5
        quality > 60 -> 4
        quality > 40 -> 3
        quality > 20 -> 2
        quality > 0 -> 1
        else -> 0
    }
}

@Composable
private fun StatusIconWithText(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(color = color, radius = size.minDimension / 2)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SignalBars(strength: Int, color: Color) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
        for (i in 1..5) {
            val barColor = if (i <= strength) color else Color.White.copy(0.3f)
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height((4 + i * 2).dp)
                    .background(barColor, RoundedCornerShape(0.5.dp))
            )
        }
    }
}

@Composable
private fun BatteryStatus(percentage: Int) {
    val color = when {
        percentage > 30 -> Color(0xFF4CAF50)
        percentage > 15 -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "$percentage%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(6.dp))
        Canvas(modifier = Modifier.width(22.dp).height(12.dp)) {
            val strokeWidth = 1.dp.toPx()
            drawRect(
                color = Color.White,
                topLeft = Offset(0f, 0f),
                size = Size(size.width - 2.dp.toPx(), size.height),
                style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth)
            )
            drawRect(
                color = Color.White,
                topLeft = Offset(size.width - 1.5.dp.toPx(), size.height * 0.3f),
                size = Size(1.5.dp.toPx(), size.height * 0.4f)
            )
            val padding = 2.dp.toPx()
            val availableWidth = size.width - 2.dp.toPx() - padding * 2
            val fillWidth = availableWidth * (percentage.coerceIn(0, 100) / 100f)
            drawRect(
                color = color,
                topLeft = Offset(padding, padding),
                size = Size(fillWidth, size.height - padding * 2)
            )
        }
    }
}
