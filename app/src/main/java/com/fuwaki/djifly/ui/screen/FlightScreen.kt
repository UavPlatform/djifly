package com.fuwaki.djifly.ui.screen

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.fuwaki.djifly.platform.registration.PlatformRegistrationManager
import com.fuwaki.djifly.platform.registration.PlatformRegistrationState
import com.fuwaki.djifly.platform.ws.WsCommunicationState
import com.fuwaki.djifly.platform.ws.WsConnectionState
import com.fuwaki.djifly.sdk.DjiSdkManager
import com.fuwaki.djifly.sdk.SdkConnectionState
import com.fuwaki.djifly.ui.widget.FpvWidget
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val TAG = "FlightScreen"

private data class FlightScreenSummary(
    val productName: String,
    val sdkStatusLabel: String,
    val batteryPercentage: Int,
    val gpsSatelliteCount: Int,
    val uplinkQuality: Int,
    val downlinkQuality: Int,
    val flightTimeRemainingSeconds: Int
)

@Composable
fun FlightScreen(
    sdkManager: DjiSdkManager,
    registrationManager: PlatformRegistrationManager,
    wsCommunicationState: WsCommunicationState,
    onBack: () -> Unit
) {
    val connectionStateFlow = remember(sdkManager) {
        sdkManager.sdkStatus
            .map { it.connectionState }
            .distinctUntilChanged()
    }
    val connectionState by connectionStateFlow.collectAsState(
        initial = sdkManager.sdkStatus.value.connectionState
    )
    val registrationState by registrationManager.state.collectAsState()
    val wsConnectionState by wsCommunicationState.connectionState.collectAsState()

    if (connectionState is SdkConnectionState.ProductConnected) {
        ImmersiveFlightScreenContent(
            sdkManager = sdkManager,
            registrationState = registrationState,
            wsConnectionState = wsConnectionState,
            onBack = onBack
        )
    } else {
        ConnectionRequiredScreen(
            registrationState = registrationState,
            wsConnectionState = wsConnectionState,
            onBack = onBack
        )
    }
}

@Composable
private fun ConnectionRequiredScreen(
    registrationState: PlatformRegistrationState,
    wsConnectionState: WsConnectionState,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF090909)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "飞控页暂不可用",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "当前没有检测到飞机连接，已退化为稳定模式页面。",
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(20.dp))
                StatusCard(title = "连接状态") {
                    StatusLine(label = "飞机", value = "未连接", valueColor = Color(0xFFFFB300))
                    StatusLine(
                        label = "服务器注册",
                        value = registrationState.statusLabel(),
                        valueColor = registrationState.statusColor()
                    )
                    StatusLine(
                        label = "WebSocket",
                        value = wsConnectionState.statusLabel(),
                        valueColor = wsConnectionState.statusColor()
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedButton(onClick = onBack) {
                    Text("返回首页")
                }
            }
        }
    }
}

@Composable
private fun ImmersiveFlightScreenContent(
    sdkManager: DjiSdkManager,
    registrationState: PlatformRegistrationState,
    wsConnectionState: WsConnectionState,
    onBack: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val summaryFlow = remember(sdkManager) {
        sdkManager.sdkStatus
            .map { status ->
                val connectedName = (status.connectionState as? SdkConnectionState.ProductConnected)?.productName
                    ?: status.productInfo.name
                FlightScreenSummary(
                    productName = connectedName,
                    sdkStatusLabel = status.sdkStatusText,
                    batteryPercentage = status.flightData.batteryPercentage,
                    gpsSatelliteCount = status.flightData.gpsSatelliteCount,
                    uplinkQuality = status.flightData.uplinkQuality,
                    downlinkQuality = status.flightData.downlinkQuality,
                    flightTimeRemainingSeconds = status.flightData.flightTimeRemaining
                )
            }
            .distinctUntilChanged()
    }
    val summary by summaryFlow.collectAsState(
        initial = sdkManager.sdkStatus.value.toFlightScreenSummary()
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                Log.d(TAG, "Immersive Flight Screen Active")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            FpvWidget(
                modifier = Modifier.fillMaxSize(),
                showCameraName = false,
                showCameraSide = false,
                enableCenterPoint = false,
                enableGridLines = false
            )

            ViewportScrim(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(188.dp),
                brush = Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.86f),
                        Color.Black.copy(alpha = 0.38f),
                        Color.Transparent
                    )
                )
            )

            ViewportScrim(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(220.dp),
                brush = Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.34f),
                        Color.Black.copy(alpha = 0.82f)
                    )
                )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FlightHeader(
                    productName = summary.productName,
                    sdkStatusLabel = summary.sdkStatusLabel,
                    onBack = onBack
                )

                FlightTopStatusBar(
                    summary = summary,
                    registrationState = registrationState,
                    wsConnectionState = wsConnectionState
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                FlightTelemetryPanel(
                    summary = summary,
                    registrationState = registrationState,
                    wsConnectionState = wsConnectionState,
                    modifier = Modifier.weight(1f)
                )

                FlightActionPanel(
                    onTakeOff = { sdkManager.performTakeOff() },
                    onReturnHome = { sdkManager.performRTH() }
                )
            }
        }
    }
}

@Composable
private fun FlightHeader(
    productName: String,
    sdkStatusLabel: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.background(
                color = Color.Black.copy(alpha = 0.42f),
                shape = CircleShape
            )
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Flight Console",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = productName.ifBlank { "未知设备" },
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall
            )
        }
        StatusPill(
            label = "SDK",
            value = sdkStatusLabel,
            accentColor = Color.White
        )
    }
}

@Composable
private fun FlightTopStatusBar(
    summary: FlightScreenSummary,
    registrationState: PlatformRegistrationState,
    wsConnectionState: WsConnectionState
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatusPill(
            label = "BAT",
            value = "${summary.batteryPercentage}%",
            accentColor = batteryColor(summary.batteryPercentage)
        )
        StatusPill(
            label = "GPS",
            value = "${summary.gpsSatelliteCount}",
            accentColor = signalQualityColor(summary.gpsSatelliteCount.coerceAtMost(100))
        )
        StatusPill(
            label = "RC",
            value = "${summary.uplinkQuality}%",
            accentColor = signalQualityColor(summary.uplinkQuality)
        )
        StatusPill(
            label = "HD",
            value = "${summary.downlinkQuality}%",
            accentColor = signalQualityColor(summary.downlinkQuality)
        )
        StatusPill(
            label = "TIME",
            value = summary.flightTimeRemainingSeconds.toFlightTimeLabel(),
            accentColor = Color(0xFF29B6F6)
        )
        StatusPill(
            label = "SERVER",
            value = registrationState.statusLabel(),
            accentColor = registrationState.statusColor()
        )
        StatusPill(
            label = "WS",
            value = wsConnectionState.statusLabel(),
            accentColor = wsConnectionState.statusColor()
        )
    }
}

@Composable
private fun StatusPill(
    label: String,
    value: String,
    accentColor: Color
) {
    Column(
        modifier = Modifier
            .background(
                color = accentColor.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.55f),
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = value,
            color = accentColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun FlightTelemetryPanel(
    summary: FlightScreenSummary,
    registrationState: PlatformRegistrationState,
    wsConnectionState: WsConnectionState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.heightIn(min = 144.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.48f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Flight Overlay",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Stable",
                    color = Color(0xFF4CAF50),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            OverlayMetricLine(
                label = "飞行时间",
                value = summary.flightTimeRemainingSeconds.toFlightTimeLabel(),
                valueColor = Color(0xFF29B6F6)
            )
            OverlayMetricLine(
                label = "服务器",
                value = registrationState.statusLabel(),
                valueColor = registrationState.statusColor()
            )
            OverlayMetricLine(
                label = "WebSocket",
                value = wsConnectionState.statusLabel(),
                valueColor = wsConnectionState.statusColor()
            )
            OverlayMetricLine(
                label = "链路",
                value = "RC ${summary.uplinkQuality}% / HD ${summary.downlinkQuality}%",
                valueColor = signalQualityColor(
                    quality = minOf(summary.uplinkQuality, summary.downlinkQuality)
                )
            )
        }
    }
}

@Composable
private fun OverlayMetricLine(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.62f),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            color = valueColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun FlightActionPanel(
    onTakeOff: () -> Unit,
    onReturnHome: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(132.dp)
            .heightIn(min = 144.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.48f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Actions",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = onTakeOff,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF16A34A).copy(alpha = 0.92f)
                )
            ) {
                Text("起飞")
            }
            OutlinedButton(
                onClick = onReturnHome,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                )
            ) {
                Text("返航")
            }
        }
    }
}

@Composable
private fun ViewportScrim(
    modifier: Modifier,
    brush: Brush
) {
    Box(
        modifier = modifier.background(brush = brush)
    )
}

@Composable
private fun StatusCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            content()
        }
    }
}

@Composable
private fun StatusLine(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            color = valueColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun PlatformRegistrationState.statusLabel(): String {
    return when (this) {
        PlatformRegistrationState.WaitingForAircraft -> "等待飞机"
        is PlatformRegistrationState.Registering -> "注册中"
        is PlatformRegistrationState.RetryScheduled -> "${retryAfterSeconds} 秒后重试"
        is PlatformRegistrationState.Registered -> "已连接"
        is PlatformRegistrationState.Failed -> "连接失败"
    }
}

private fun PlatformRegistrationState.statusColor(): Color {
    return when (this) {
        PlatformRegistrationState.WaitingForAircraft -> Color(0xFF90A4AE)
        is PlatformRegistrationState.Registering -> Color(0xFF1E88E5)
        is PlatformRegistrationState.RetryScheduled -> Color(0xFFFB8C00)
        is PlatformRegistrationState.Registered -> Color(0xFF2E7D32)
        is PlatformRegistrationState.Failed -> Color(0xFFC62828)
    }
}

private fun WsConnectionState.statusLabel(): String {
    return when (this) {
        WsConnectionState.Idle -> "空闲"
        WsConnectionState.Connecting -> "连接中"
        WsConnectionState.Connected -> "已连接"
        is WsConnectionState.Disconnected -> "已断开"
        is WsConnectionState.Error -> "异常"
    }
}

private fun WsConnectionState.statusColor(): Color {
    return when (this) {
        WsConnectionState.Idle -> Color(0xFF90A4AE)
        WsConnectionState.Connecting -> Color(0xFF1E88E5)
        WsConnectionState.Connected -> Color(0xFF2E7D32)
        is WsConnectionState.Disconnected -> Color(0xFFFB8C00)
        is WsConnectionState.Error -> Color(0xFFC62828)
    }
}

private fun Int.toFlightTimeLabel(): String {
    val safeValue = coerceAtLeast(0)
    val minutes = safeValue / 60
    val seconds = safeValue % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun batteryColor(percentage: Int): Color {
    return when {
        percentage > 30 -> Color(0xFF4CAF50)
        percentage > 15 -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }
}

private fun signalQualityColor(quality: Int): Color {
    return when {
        quality >= 80 -> Color(0xFF4CAF50)
        quality >= 50 -> Color(0xFFFFC107)
        quality > 0 -> Color(0xFFFF7043)
        else -> Color(0xFF90A4AE)
    }
}

private fun com.fuwaki.djifly.sdk.DjiSdkStatus.toFlightScreenSummary(): FlightScreenSummary {
    val connectedName = (connectionState as? SdkConnectionState.ProductConnected)?.productName
        ?: productInfo.name
    return FlightScreenSummary(
        productName = connectedName,
        sdkStatusLabel = sdkStatusText,
        batteryPercentage = flightData.batteryPercentage,
        gpsSatelliteCount = flightData.gpsSatelliteCount,
        uplinkQuality = flightData.uplinkQuality,
        downlinkQuality = flightData.downlinkQuality,
        flightTimeRemainingSeconds = flightData.flightTimeRemaining
    )
}
