package com.fuwaki.djifly.ui.screen

import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.fuwaki.djifly.platform.registration.PlatformRegistrationManager
import com.fuwaki.djifly.platform.registration.PlatformRegistrationState
import com.fuwaki.djifly.platform.ws.WsCommunicationState
import com.fuwaki.djifly.platform.ws.WsConnectionState
import com.fuwaki.djifly.platform.ws.WsMessageLog
import com.fuwaki.djifly.sdk.DjiSdkManager
import com.fuwaki.djifly.sdk.DjiSdkStatus
import com.fuwaki.djifly.sdk.SdkConnectionState
import com.fuwaki.djifly.ui.widget.CameraControlsComposeWidget
import com.fuwaki.djifly.ui.widget.FocusModeWidget
import com.fuwaki.djifly.ui.widget.FpvWidget
import com.fuwaki.djifly.ui.widget.HorizontalSituationIndicatorWidget
import com.fuwaki.djifly.ui.widget.LensControlWidget
import com.fuwaki.djifly.ui.widget.compose.CameraConfigBar
import com.fuwaki.djifly.ui.widget.compose.ReturnHomeButton
import com.fuwaki.djifly.ui.widget.compose.ServerConnectionChip
import com.fuwaki.djifly.ui.widget.compose.ServerConnectionIndicatorDot
import com.fuwaki.djifly.ui.widget.compose.TakeOffButton
import com.fuwaki.djifly.ui.widget.compose.WebSocketMessageHistory
import dji.sdk.keyvalue.value.common.CameraLensType
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.ux.core.widget.setting.SettingPanelWidget
import dji.v5.ux.core.widget.systemstatus.SystemStatusWidget
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val TAG = "FlightScreen"

private val OverlaySurfaceColor = Color(0xD90A0F14)
private val OverlayBorderColor = Color(0x1AFFFFFF)
private val OverlayTextSecondary = Color(0xB8FFFFFF)
private val OverlayTextTertiary = Color(0x80FFFFFF)
private val AccentBlue = Color(0xFF38BDF8)

private enum class FlightBottomPanel(
    val title: String,
    val subtitle: String
) {
    Camera(
        title = "相机参数",
        subtitle = "曝光、白平衡、快门、ISO / EI 与存储"
    ),
    Link(
        title = "链路与通讯",
        subtitle = "服务器注册、WebSocket 状态与消息历史"
    ),
    Info(
        title = "机体与系统",
        subtitle = "飞机、固件、控制器与传感状态"
    )
}

private data class FlightScreenSummary(
    val productName: String,
    val controllerModel: String,
    val serialNumber: String,
    val firmwareVersion: String,
    val sdkStatusLabel: String,
    val batteryPercentage: Int,
    val gpsSatelliteCount: Int,
    val uplinkQuality: Int,
    val downlinkQuality: Int,
    val flightTimeRemainingSeconds: Int,
    val isVisionSystemHealthy: Boolean
)

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
    val wsMessages by wsCommunicationState.messages.collectAsState()

    if (connectionState is SdkConnectionState.ProductConnected) {
        ImmersiveFlightScreenContent(
            sdkManager = sdkManager,
            registrationState = registrationState,
            wsConnectionState = wsConnectionState,
            wsMessages = wsMessages,
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
    wsMessages: List<WsMessageLog>,
    onBack: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val summaryFlow = remember(sdkManager) {
        sdkManager.sdkStatus
            .map { status -> status.toFlightScreenSummary() }
            .distinctUntilChanged()
    }
    val summary by summaryFlow.collectAsState(
        initial = sdkManager.sdkStatus.value.toFlightScreenSummary()
    )
    var activePanel by rememberSaveable { mutableStateOf<FlightBottomPanel?>(null) }
    var isSettingsDrawerOpen by rememberSaveable { mutableStateOf(false) }

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
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isCompact = maxWidth < 900.dp || maxHeight < 500.dp
            val telemetryWidth = if (isCompact) 158.dp else 184.dp
            val settingsDrawerWidth = if (isCompact) {
                (maxWidth - 24.dp).coerceAtLeast(360.dp)
            } else {
                (maxWidth * 0.6f).coerceIn(420.dp, 560.dp)
            }

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
                        .height(if (isCompact) 136.dp else 156.dp),
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.88f),
                            Color.Black.copy(alpha = 0.42f),
                            Color.Transparent
                        )
                    )
                )

                ViewportScrim(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(if (isCompact) 204.dp else 228.dp),
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.42f),
                            Color.Black.copy(alpha = 0.88f)
                        )
                    )
                )

                FlightTopOverlay(
                    summary = summary,
                    registrationState = registrationState,
                    wsConnectionState = wsConnectionState,
                    activePanel = activePanel,
                    isCompact = isCompact,
                    onBack = onBack,
                    onToggleLinkPanel = {
                        isSettingsDrawerOpen = false
                        activePanel = if (activePanel == FlightBottomPanel.Link) {
                            null
                        } else {
                            FlightBottomPanel.Link
                        }
                    },
                    onOpenSettings = {
                        activePanel = null
                        isSettingsDrawerOpen = true
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = if (isCompact) 12.dp else 18.dp, vertical = 10.dp)
                )

                if (!isSettingsDrawerOpen && activePanel == null) {
                    FlightActionRail(
                        onTakeOff = { sdkManager.performTakeOff() },
                        onReturnHome = { sdkManager.performRTH() },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                start = if (isCompact) 12.dp else 18.dp,
                                top = if (isCompact) 112.dp else 126.dp
                            )
                    )

                    FlightCameraRail(
                        activePanel = activePanel,
                        isCompact = isCompact,
                        onToggleCameraPanel = {
                            isSettingsDrawerOpen = false
                            activePanel = if (activePanel == FlightBottomPanel.Camera) {
                                null
                            } else {
                                FlightBottomPanel.Camera
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .navigationBarsPadding()
                            .padding(
                                end = if (isCompact) 12.dp else 18.dp,
                                bottom = if (isCompact) 8.dp else 0.dp
                            )
                    )

                    FlightTelemetryPanel(
                        activePanel = activePanel,
                        telemetryWidth = telemetryWidth,
                        onToggleLinkPanel = {
                            isSettingsDrawerOpen = false
                            activePanel = if (activePanel == FlightBottomPanel.Link) {
                                null
                            } else {
                                FlightBottomPanel.Link
                            }
                        },
                        onToggleInfoPanel = {
                            isSettingsDrawerOpen = false
                            activePanel = if (activePanel == FlightBottomPanel.Info) {
                                null
                            } else {
                                FlightBottomPanel.Info
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .navigationBarsPadding()
                            .padding(
                                start = if (isCompact) 12.dp else 18.dp,
                                bottom = if (isCompact) 14.dp else 18.dp
                            )
                    )

                    FlightHsiPanel(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = if (isCompact) 16.dp else 20.dp)
                    )
                }

                if (activePanel != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.46f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { activePanel = null }
                    )

                    FlightBottomDrawer(
                        activePanel = activePanel ?: FlightBottomPanel.Camera,
                        summary = summary,
                        registrationState = registrationState,
                        wsConnectionState = wsConnectionState,
                        wsMessages = wsMessages,
                        isCompact = isCompact,
                        onSelectPanel = { activePanel = it },
                        onClose = { activePanel = null },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = if (isCompact) 12.dp else 18.dp, vertical = 14.dp)
                            .fillMaxWidth()
                    )
                }

                if (isSettingsDrawerOpen) {
                    FlightSettingsDrawer(
                        drawerWidth = settingsDrawerWidth,
                        isCompact = isCompact,
                        onClose = { isSettingsDrawerOpen = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun FlightTopOverlay(
    summary: FlightScreenSummary,
    registrationState: PlatformRegistrationState,
    wsConnectionState: WsConnectionState,
    activePanel: FlightBottomPanel?,
    isCompact: Boolean,
    onBack: () -> Unit,
    onToggleLinkPanel: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topLinkLabel = when {
        registrationState is PlatformRegistrationState.Registered &&
            wsConnectionState == WsConnectionState.Connected -> "在线"
        registrationState is PlatformRegistrationState.Failed ||
            wsConnectionState is WsConnectionState.Error -> "异常"
        registrationState is PlatformRegistrationState.Registering ||
            wsConnectionState == WsConnectionState.Connecting -> "连接中"
        else -> "待机"
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularGlassIconButton(
                icon = Icons.Default.ArrowBack,
                onClick = onBack
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(if (isCompact) 0.92f else 1f)
            ) {
                Text(
                    text = "Flight Deck",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = buildString {
                        append(summary.productName.ifBlank { "未知设备" })
                        if (summary.controllerModel.isNotBlank()) {
                            append(" · ")
                            append(summary.controllerModel)
                        }
                        if (summary.sdkStatusLabel.isNotBlank()) {
                            append(" · SDK ")
                            append(summary.sdkStatusLabel)
                        }
                    },
                    color = OverlayTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            EmbeddedSystemStatusStrip(
                modifier = Modifier
                    .width(if (isCompact) 196.dp else 248.dp)
                    .height(if (isCompact) 36.dp else 40.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleLinkPanel
                ),
                shape = RoundedCornerShape(999.dp),
                color = OverlaySurfaceColor,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (activePanel == FlightBottomPanel.Link) {
                        registrationState.statusColor().copy(alpha = 0.55f)
                    } else {
                        OverlayBorderColor
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ServerConnectionIndicatorDot(registrationState = registrationState)
                    Text(
                        text = "NET $topLinkLabel",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!summary.isVisionSystemHealthy) {
                Spacer(modifier = Modifier.width(8.dp))
                CompactTextBadge(
                    label = "VISION CHECK",
                    accentColor = Color(0xFFF59E0B)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            CircularGlassIconButton(
                icon = Icons.Default.Settings,
                onClick = onOpenSettings
            )
        }

        FlightStatusRibbon(
            summary = summary,
            isCompact = isCompact,
            modifier = Modifier
        )
    }
}

@Composable
private fun FlightStatusRibbon(
    summary: FlightScreenSummary,
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RibbonMetricSegment(
                label = if (isCompact) "BAT" else "BATTERY",
                value = "${summary.batteryPercentage}%",
                accentColor = batteryColor(summary.batteryPercentage)
            )
            RibbonDivider()
            RibbonMetricSegment(
                label = "GPS",
                value = "${summary.gpsSatelliteCount}",
                accentColor = signalQualityColor(summary.gpsSatelliteCount.coerceAtMost(100))
            )
            RibbonDivider()
            RibbonMetricSegment(
                label = "LINK",
                value = "${summary.uplinkQuality} / ${summary.downlinkQuality}",
                accentColor = signalQualityColor(
                    quality = minOf(summary.uplinkQuality, summary.downlinkQuality)
                )
            )
            RibbonDivider()
            RibbonMetricSegment(
                label = "TIME",
                value = summary.flightTimeRemainingSeconds.toFlightTimeLabel(),
                accentColor = AccentBlue
            )
        }
    }
}

@Composable
private fun RibbonMetricSegment(
    label: String,
    value: String,
    accentColor: Color
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            color = OverlayTextTertiary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = accentColor,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RibbonDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(20.dp)
            .background(OverlayBorderColor)
    )
}

@Composable
private fun FlightActionRail(
    onTakeOff: () -> Unit,
    onReturnHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val railWidth = 68.dp

    GlassSurface(
        modifier = modifier.width(railWidth),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "FLIGHT",
                color = OverlayTextTertiary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
            TakeOffButton(onConfirm = onTakeOff)
            ReturnHomeButton(onConfirm = onReturnHome)
        }
    }
}

@Composable
private fun FlightCameraRail(
    activePanel: FlightBottomPanel?,
    isCompact: Boolean,
    onToggleCameraPanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val controlWidth = if (isCompact) 76.dp else 82.dp
    val railWidth = if (isCompact) 92.dp else 98.dp

    GlassSurface(
        modifier = modifier.width(railWidth),
        shape = RoundedCornerShape(26.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PanelLauncherChip(
                label = "参数",
                active = activePanel == FlightBottomPanel.Camera,
                onClick = onToggleCameraPanel,
                icon = null,
                modifier = Modifier.widthIn(max = controlWidth)
            )
            CameraControlsComposeWidget(
                cameraIndex = ComponentIndexType.LEFT_OR_MAIN,
                lensType = CameraLensType.UNKNOWN,
                controlWidth = controlWidth
            )
        }
    }
}

@Composable
private fun FlightTelemetryPanel(
    activePanel: FlightBottomPanel?,
    telemetryWidth: androidx.compose.ui.unit.Dp,
    onToggleLinkPanel: () -> Unit,
    onToggleInfoPanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier.width(telemetryWidth),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PanelLauncherChip(
                label = "链路",
                active = activePanel == FlightBottomPanel.Link,
                onClick = onToggleLinkPanel,
                icon = null,
                modifier = Modifier.weight(1f)
            )
            PanelLauncherChip(
                label = "设备",
                active = activePanel == FlightBottomPanel.Info,
                onClick = onToggleInfoPanel,
                icon = null,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FlightHsiPanel(
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "姿态 / 航向",
                color = OverlayTextTertiary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
            Box(
                modifier = Modifier.height(72.dp),
                contentAlignment = Alignment.Center
            ) {
                HorizontalSituationIndicatorWidget(
                    modifier = Modifier.fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun FlightBottomDrawer(
    activePanel: FlightBottomPanel,
    summary: FlightScreenSummary,
    registrationState: PlatformRegistrationState,
    wsConnectionState: WsConnectionState,
    wsMessages: List<WsMessageLog>,
    isCompact: Boolean,
    onSelectPanel: (FlightBottomPanel) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier.fillMaxHeight(if (isCompact) 0.84f else 0.72f),
        shape = RoundedCornerShape(26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activePanel.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = activePanel.subtitle,
                        color = OverlayTextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                CircularGlassIconButton(
                    icon = Icons.Default.Close,
                    onClick = onClose
                )
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FlightBottomPanel.values().forEach { panel ->
                    PanelLauncherChip(
                        label = when (panel) {
                            FlightBottomPanel.Camera -> "CAMERA"
                            FlightBottomPanel.Link -> "LINK"
                            FlightBottomPanel.Info -> "INFO"
                        },
                        active = panel == activePanel,
                        onClick = { onSelectPanel(panel) },
                        icon = null
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (activePanel) {
                    FlightBottomPanel.Camera -> CameraPanelContent()
                    FlightBottomPanel.Link -> LinkPanelContent(
                        registrationState = registrationState,
                        wsConnectionState = wsConnectionState,
                        wsMessages = wsMessages,
                        summary = summary,
                        isCompact = isCompact
                    )
                    FlightBottomPanel.Info -> InfoPanelContent(
                        summary = summary,
                        registrationState = registrationState,
                        wsConnectionState = wsConnectionState,
                        isCompact = isCompact
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPanelContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "曝光 / 白平衡 / ISO / EI / 快门 / 存储",
            color = OverlayTextSecondary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CameraAssistCard(
                title = "对焦模式",
                modifier = Modifier.weight(1f)
            ) {
                FocusModeWidget(modifier = Modifier.fillMaxSize())
            }
            CameraAssistCard(
                title = "镜头控制",
                modifier = Modifier.weight(1f)
            ) {
                LensControlWidget(modifier = Modifier.fillMaxSize())
            }
        }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.04f),
            border = BorderStroke(1.dp, OverlayBorderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CameraConfigBar()
            }
        }
    }
}

@Composable
private fun LinkPanelContent(
    registrationState: PlatformRegistrationState,
    wsConnectionState: WsConnectionState,
    wsMessages: List<WsMessageLog>,
    summary: FlightScreenSummary,
    isCompact: Boolean
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val useStackedLayout = maxWidth < 720.dp
        if (useStackedLayout) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LinkSummaryCard(
                    registrationState = registrationState,
                    wsConnectionState = wsConnectionState,
                    summary = summary,
                    modifier = Modifier.fillMaxWidth()
                )
                WebSocketMessageHistory(
                    messages = wsMessages,
                    header = "最近通讯记录",
                    emptyMessage = wsConnectionState.logEmptyStateLabel(),
                    maxItems = 100,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 280.dp)
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LinkSummaryCard(
                    registrationState = registrationState,
                    wsConnectionState = wsConnectionState,
                    summary = summary,
                    modifier = Modifier.width(300.dp)
                )
                WebSocketMessageHistory(
                    messages = wsMessages,
                    header = "最近通讯记录",
                    emptyMessage = wsConnectionState.logEmptyStateLabel(),
                    maxItems = 100,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 240.dp, max = 320.dp)
                )
            }
        }
    }
}

@Composable
private fun LinkSummaryCard(
    registrationState: PlatformRegistrationState,
    wsConnectionState: WsConnectionState,
    summary: FlightScreenSummary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, OverlayBorderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ServerConnectionChip(
                registrationState = registrationState,
                modifier = Modifier.fillMaxWidth(),
                showDetail = true
            )
            InfoMetricCell(
                label = "WebSocket",
                value = wsConnectionState.statusLabel(),
                accentColor = wsConnectionState.statusColor(),
                modifier = Modifier.fillMaxWidth()
            )
            InfoMetricCell(
                label = "控制链路",
                value = "RC ${summary.uplinkQuality}% / HD ${summary.downlinkQuality}%",
                accentColor = signalQualityColor(
                    quality = minOf(summary.uplinkQuality, summary.downlinkQuality)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            InfoMetricCell(
                label = "链路详情",
                value = wsConnectionState.logEmptyStateLabel(),
                accentColor = wsConnectionState.statusColor(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CameraAssistCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, OverlayBorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                color = OverlayTextSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            GlassInset(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp),
                shape = RoundedCornerShape(16.dp),
                content = content
            )
        }
    }
}

@Composable
private fun InfoPanelContent(
    summary: FlightScreenSummary,
    registrationState: PlatformRegistrationState,
    wsConnectionState: WsConnectionState,
    isCompact: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isCompact) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoMetricCell(
                    label = "飞机",
                    value = summary.productName,
                    accentColor = Color.White,
                    modifier = Modifier.weight(1f)
                )
                InfoMetricCell(
                    label = "控制器",
                    value = summary.controllerModel,
                    accentColor = Color.White,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoMetricCell(
                    label = "固件",
                    value = summary.firmwareVersion,
                    accentColor = AccentBlue,
                    modifier = Modifier.weight(1f)
                )
                InfoMetricCell(
                    label = "序列号",
                    value = summary.serialNumber,
                    accentColor = Color.White,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoMetricCell(
                    label = "视觉",
                    value = if (summary.isVisionSystemHealthy) "正常" else "检查",
                    accentColor = if (summary.isVisionSystemHealthy) {
                        Color(0xFF22C55E)
                    } else {
                        Color(0xFFF59E0B)
                    },
                    modifier = Modifier.weight(1f)
                )
                InfoMetricCell(
                    label = "服务器",
                    value = registrationState.statusLabel(),
                    accentColor = registrationState.statusColor(),
                    modifier = Modifier.weight(1f)
                )
            }
            InfoMetricCell(
                label = "WebSocket",
                value = wsConnectionState.statusLabel(),
                accentColor = wsConnectionState.statusColor(),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoMetricCell(
                    label = "飞机",
                    value = summary.productName,
                    accentColor = Color.White,
                    modifier = Modifier.weight(1f)
                )
                InfoMetricCell(
                    label = "控制器",
                    value = summary.controllerModel,
                    accentColor = Color.White,
                    modifier = Modifier.weight(1f)
                )
                InfoMetricCell(
                    label = "固件",
                    value = summary.firmwareVersion,
                    accentColor = AccentBlue,
                    modifier = Modifier.weight(1f)
                )
                InfoMetricCell(
                    label = "序列号",
                    value = summary.serialNumber,
                    accentColor = Color.White,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoMetricCell(
                    label = "视觉",
                    value = if (summary.isVisionSystemHealthy) "正常" else "检查",
                    accentColor = if (summary.isVisionSystemHealthy) {
                        Color(0xFF22C55E)
                    } else {
                        Color(0xFFF59E0B)
                    },
                    modifier = Modifier.weight(1f)
                )
                InfoMetricCell(
                    label = "SDK",
                    value = summary.sdkStatusLabel,
                    accentColor = Color.White,
                    modifier = Modifier.weight(1f)
                )
                InfoMetricCell(
                    label = "服务器",
                    value = registrationState.statusLabel(),
                    accentColor = registrationState.statusColor(),
                    modifier = Modifier.weight(1f)
                )
                InfoMetricCell(
                    label = "WebSocket",
                    value = wsConnectionState.statusLabel(),
                    accentColor = wsConnectionState.statusColor(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun FlightSettingsDrawer(
    drawerWidth: androidx.compose.ui.unit.Dp,
    isCompact: Boolean,
    onClose: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.56f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                )
        )

        Surface(
            modifier = Modifier
                .align(if (isCompact) Alignment.Center else Alignment.CenterEnd)
                .fillMaxHeight(if (isCompact) 0.94f else 1f)
                .width(drawerWidth)
                .padding(12.dp),
            shape = RoundedCornerShape(if (isCompact) 24.dp else 28.dp),
            color = Color(0xF20B1016),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "系统设置",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "DJI 原生设置面板保持独立托管，不和主叠层耦合。",
                            color = OverlayTextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    CircularGlassIconButton(
                        icon = Icons.Default.Close,
                        onClick = onClose
                    )
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        val activity = context.findFragmentActivity()
                        if (activity == null) {
                            TextView(context).apply {
                                text = "Setting panel unavailable"
                                setTextColor(android.graphics.Color.WHITE)
                                textSize = 14f
                                setPadding(48, 48, 48, 48)
                            }
                        } else {
                            object : SettingPanelWidget(activity) {
                                override fun onBackPressed(): Boolean {
                                    val handled = super.onBackPressed()
                                    if (!handled) {
                                        onClose()
                                        return true
                                    }
                                    return handled
                                }
                            }.apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun EmbeddedSystemStatusStrip(
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val activity = context.findFragmentActivity()
                if (activity == null) {
                    TextView(context).apply {
                        text = "System"
                        setTextColor(android.graphics.Color.WHITE)
                        textSize = 12f
                        setPadding(24, 8, 24, 8)
                    }
                } else {
                    SystemStatusWidget(activity).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun PanelLauncherChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        ),
        shape = RoundedCornerShape(999.dp),
        color = if (active) {
            AccentBlue.copy(alpha = 0.18f)
        } else {
            OverlaySurfaceColor
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (active) AccentBlue.copy(alpha = 0.42f) else OverlayBorderColor
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (active) AccentBlue else Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                color = if (active) AccentBlue else Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CircularGlassIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        ),
        shape = CircleShape,
        color = OverlaySurfaceColor,
        border = BorderStroke(1.dp, OverlayBorderColor)
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun CompactTextBadge(
    label: String,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accentColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.24f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            color = accentColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CompactMetricChip(
    label: String,
    value: String,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = OverlaySurfaceColor,
        border = BorderStroke(1.dp, OverlayBorderColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                color = OverlayTextTertiary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                color = accentColor,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun InfoMetricCell(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, OverlayBorderColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                color = OverlayTextTertiary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
        Text(
            text = value,
            color = accentColor,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = OverlaySurfaceColor,
        border = BorderStroke(1.dp, OverlayBorderColor)
    ) {
        content()
    }
}

@Composable
private fun GlassInset(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, OverlayBorderColor)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
            content = content
        )
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

private fun WsConnectionState.logEmptyStateLabel(): String {
    return when (this) {
        WsConnectionState.Idle -> "等待建立连接后开始记录消息"
        WsConnectionState.Connecting -> "连接建立中，日志将在握手完成后刷新"
        WsConnectionState.Connected -> "当前已连接，等待首条消息"
        is WsConnectionState.Disconnected -> reason
        is WsConnectionState.Error -> message
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
        percentage > 30 -> Color(0xFF22C55E)
        percentage > 15 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }
}

private fun signalQualityColor(quality: Int): Color {
    return when {
        quality >= 80 -> Color(0xFF22C55E)
        quality >= 50 -> Color(0xFFF59E0B)
        quality > 0 -> Color(0xFFFF7043)
        else -> Color(0xFF90A4AE)
    }
}

private fun DjiSdkStatus.toFlightScreenSummary(): FlightScreenSummary {
    val connectedName = (connectionState as? SdkConnectionState.ProductConnected)?.productName
        ?: productInfo.name
    return FlightScreenSummary(
        productName = connectedName,
        controllerModel = productInfo.controllerModel,
        serialNumber = productInfo.serialNumber,
        firmwareVersion = productInfo.firmwareVersion,
        sdkStatusLabel = sdkStatusText,
        batteryPercentage = flightData.batteryPercentage,
        gpsSatelliteCount = flightData.gpsSatelliteCount,
        uplinkQuality = flightData.uplinkQuality,
        downlinkQuality = flightData.downlinkQuality,
        flightTimeRemainingSeconds = flightData.flightTimeRemaining,
        isVisionSystemHealthy = flightData.isVisionSystemHealthy
    )
}
