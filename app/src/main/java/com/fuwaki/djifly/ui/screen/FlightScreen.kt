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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
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
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fuwaki.djifly.data.models.FlightEntryGate
import com.fuwaki.djifly.platform.attachment.DeliverableItemPhase
import com.fuwaki.djifly.platform.attachment.DeliverableItemUi
import com.fuwaki.djifly.platform.attachment.DeliveryCompletionGate
import com.fuwaki.djifly.platform.attachment.FpvSnapshotCapture
import com.fuwaki.djifly.platform.attachment.FpvSnapshotTarget
import com.fuwaki.djifly.platform.live.RiderLiveRequestState
import com.fuwaki.djifly.platform.livestream.LiveStopCoordinator
import com.fuwaki.djifly.ui.viewmodel.ActionState
import com.fuwaki.djifly.ui.viewmodel.TaskViewModel
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
import com.fuwaki.djifly.ui.components.FlightEdgeCameraButton
import com.fuwaki.djifly.ui.components.FlightMinimalTopHud
import com.fuwaki.djifly.ui.components.FlightToolsMenuSheet
import com.fuwaki.djifly.ui.components.GhostIconButton
import com.fuwaki.djifly.ui.components.PlatformOpsBar
import dji.v5.ux.core.widget.setting.SettingPanelWidget
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

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
    liveStopCoordinator: LiveStopCoordinator,
    taskNum: String? = null,
    onBack: () -> Unit,
    taskViewModel: TaskViewModel = hiltViewModel()
) {
    // 退出飞行页（含进程级返回/页面销毁）时受控停止推流并回执 LIVE_STOPPED（t12/FLY 高-3）
    DisposableEffect(Unit) {
        onDispose {
            liveStopCoordinator.stopStreamingAndNotify()
        }
    }

    // 1B-8：飞行链路任务上下文（自由飞行 taskNum 为空 → FreeFlight，行为不变）
    val taskDetail by taskViewModel.taskDetail.collectAsState()
    val detailLoading by taskViewModel.detailLoading.collectAsState()
    val actionState by taskViewModel.actionState.collectAsState()
    val taskContext = FlightTaskContext.resolve(taskNum, taskDetail, detailLoading)

    // 执飞门禁（TASK-DJIFLY-002）：本页是入口校验的兜底——即使绕过起飞检查页直达，
    // 非 CONFIRMED/等价状态的任务也会被拦截并给出明确提示（后端 startExecution 双确认门禁为最终兜底）。
    // 任务详情未就绪（加载中/加载失败）同样按未校验处理，不放行。
    val gateVerifying = taskContext is FlightTaskContext.Loading
    val flightGateReason = remember(taskContext, taskDetail) {
        when (taskContext) {
            is FlightTaskContext.Ready -> taskDetail?.let { FlightEntryGate.denyReason(it) }
            FlightTaskContext.Loading -> "正在校验任务状态，校验通过后才能进入飞行控制台"
            FlightTaskContext.FreeFlight -> null
        }
    }

    LaunchedEffect(taskNum) {
        taskViewModel.clearDeliverables()
        if (!taskNum.isNullOrBlank()) {
            taskViewModel.loadTaskDetail(taskNum)
        }
    }

    // 确认送达：仅在用户提交后消费 actionState（避免陈旧状态干扰）
    var taskCompleted by remember(taskNum) { mutableStateOf(false) }
    var awaitingCompleteResult by remember { mutableStateOf(false) }
    var showConfirmDeliveryDialog by remember { mutableStateOf(false) }
    val toastContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val fpvSnapshotTarget = remember { FpvSnapshotTarget() }
    var capturingEvidence by remember { mutableStateOf(false) }

    val deliverables = taskViewModel.deliverables.collectAsState()
    val liveRequestState by taskViewModel.liveRequestState.collectAsState()

    LaunchedEffect(liveRequestState) {
        when (val state = liveRequestState) {
            is RiderLiveRequestState.Success ->
                Toast.makeText(toastContext, state.userMessage, Toast.LENGTH_SHORT).show()
            is RiderLiveRequestState.Error ->
                Toast.makeText(toastContext, state.userMessage, Toast.LENGTH_LONG).show()
            else -> {}
        }
    }

    LaunchedEffect(actionState, awaitingCompleteResult) {
        if (!awaitingCompleteResult) return@LaunchedEffect
        when (val state = actionState) {
            is ActionState.Success -> {
                taskCompleted = true
                awaitingCompleteResult = false
                Toast.makeText(toastContext, state.message, Toast.LENGTH_LONG).show()
                taskViewModel.resetActionState()
            }
            is ActionState.Error -> {
                awaitingCompleteResult = false
                Toast.makeText(toastContext, state.message, Toast.LENGTH_LONG).show()
                taskViewModel.resetActionState()
            }
            else -> {}
        }
    }

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

    if (flightGateReason != null) {
        FlightGateBlockedScreen(
            verifying = gateVerifying,
            reason = flightGateReason,
            onBack = onBack
        )
        return
    }

    if (connectionState is SdkConnectionState.ProductConnected) {
        Box(modifier = Modifier.fillMaxSize()) {
            val readyTask = (taskContext as? FlightTaskContext.Ready)?.takeUnless { taskCompleted }
            ImmersiveFlightScreenContent(
                sdkManager = sdkManager,
                registrationState = registrationState,
                wsConnectionState = wsConnectionState,
                wsMessages = wsMessages,
                fpvSnapshotTarget = fpvSnapshotTarget,
                taskContext = readyTask,
                onBack = onBack
            )
            if (readyTask != null) {
                PlatformOpsBar(
                    deliverables = deliverables.value,
                    capturingEvidence = capturingEvidence,
                    canConfirmDelivery = DeliveryCompletionGate.canConfirmDelivery(deliverables.value),
                    onCaptureEvidence = {
                        val activity = toastContext.findFragmentActivity()
                        if (activity == null) {
                            Toast.makeText(toastContext, "无法截取图传画面", Toast.LENGTH_SHORT).show()
                            return@PlatformOpsBar
                        }
                        if (capturingEvidence || DeliveryCompletionGate.hasBlockingUpload(deliverables.value)) {
                            Toast.makeText(toastContext, "正在上传送达凭证，请稍候", Toast.LENGTH_SHORT).show()
                            return@PlatformOpsBar
                        }
                        capturingEvidence = true
                        scope.launch {
                            val fileName = FpvSnapshotCapture.snapshotFileName(readyTask.taskNum)
                            when (
                                val result = FpvSnapshotCapture.captureFromFpv(
                                    fpvSnapshotTarget,
                                    activity,
                                    fileName
                                )
                            ) {
                                is FpvSnapshotCapture.CaptureResult.Ok -> {
                                    taskViewModel.addDeliverableFromJpeg(result.jpegBytes, result.fileName)
                                    taskViewModel.uploadAllDeliverables(readyTask.taskNum)
                                }
                                is FpvSnapshotCapture.CaptureResult.Failed ->
                                    Toast.makeText(toastContext, result.reason, Toast.LENGTH_LONG).show()
                            }
                            capturingEvidence = false
                        }
                    },
                    onConfirmDelivery = { showConfirmDeliveryDialog = true },
                    onStartLive = { taskViewModel.requestRiderLiveStream() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp)
                )
            }
        }
    } else {
        ConnectionRequiredScreen(
            registrationState = registrationState,
            wsConnectionState = wsConnectionState,
            onBack = onBack
        )
    }

    if (showConfirmDeliveryDialog && taskContext is FlightTaskContext.Ready) {
        FlightConfirmDeliveryDialog(
            deliverables = deliverables.value,
            onRemove = taskViewModel::removeDeliverable,
            onRetry = taskViewModel::retryDeliverable,
            onDismiss = { showConfirmDeliveryDialog = false },
            onConfirm = { note ->
                if (!DeliveryCompletionGate.canConfirmDelivery(deliverables.value)) {
                    Toast.makeText(
                        toastContext,
                        DeliveryCompletionGate.REASON_EVIDENCE_REQUIRED,
                        Toast.LENGTH_LONG
                    ).show()
                    return@FlightConfirmDeliveryDialog
                }
                if (DeliveryCompletionGate.hasBlockingUpload(deliverables.value)) {
                    Toast.makeText(toastContext, "送达凭证仍在上传中，请稍候", Toast.LENGTH_SHORT).show()
                    return@FlightConfirmDeliveryDialog
                }
                showConfirmDeliveryDialog = false
                awaitingCompleteResult = true
                taskViewModel.completeTask(taskContext.taskNum, note)
            }
        )
    }
}

/**
 * 执飞门禁拦截页（TASK-DJIFLY-002）：任务非 CONFIRMED/等价状态时拒绝进入飞行控制台。
 * 文案即 [FlightEntryGate.denyReason] 给出的明确原因；[verifying] 表示任务状态仍在校验中。
 */
@Composable
private fun FlightGateBlockedScreen(
    reason: String,
    onBack: () -> Unit,
    verifying: Boolean = false
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
                if (verifying) {
                    CircularProgressIndicator(
                        color = AccentBlue,
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                Text(
                    text = if (verifying) "正在校验任务状态" else "无法开始执飞",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = reason,
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedButton(onClick = onBack) {
                    Text("返回")
                }
            }
        }
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
    fpvSnapshotTarget: FpvSnapshotTarget,
    taskContext: FlightTaskContext.Ready?,
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
    var engineerMode by rememberSaveable { mutableStateOf(false) }
    var showToolsMenu by rememberSaveable { mutableStateOf(false) }

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
                    enableGridLines = false,
                    onFpvViewAttached = { fpvSnapshotTarget.fpvView = it }
                )

                ViewportScrim(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(if (isCompact) 72.dp else 84.dp),
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.52f),
                            Color.Black.copy(alpha = 0.18f),
                            Color.Transparent
                        )
                    )
                )

                ViewportScrim(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(if (isCompact) 120.dp else 140.dp),
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.18f),
                            Color.Black.copy(alpha = 0.48f)
                        )
                    )
                )

                FlightMinimalTopHud(
                    batteryPercent = summary.batteryPercentage,
                    gpsSatellites = summary.gpsSatelliteCount,
                    registrationState = registrationState,
                    taskContext = taskContext,
                    showEngineerDetails = engineerMode,
                    productShortLabel = summary.productName.ifBlank { "飞行" },
                    onBack = onBack,
                    onOpenMore = { showToolsMenu = true },
                    onOpenSettings = {
                        showToolsMenu = false
                        activePanel = null
                        isSettingsDrawerOpen = true
                    },
                    onToggleEngineerMode = {
                        engineerMode = !engineerMode
                        if (!engineerMode) {
                            activePanel = null
                        }
                    },
                    onLinkIndicatorClick = if (engineerMode) {
                        {
                            showToolsMenu = false
                            isSettingsDrawerOpen = false
                            activePanel = FlightBottomPanel.Link
                        }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = if (isCompact) 10.dp else 14.dp, vertical = 8.dp)
                )

                if (!isSettingsDrawerOpen && activePanel == null && !showToolsMenu) {
                    FlightActionRail(
                        onTakeOff = { sdkManager.performTakeOff() },
                        onReturnHome = { sdkManager.performRTH() },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = if (isCompact) 8.dp else 12.dp)
                    )

                    FlightEdgeCameraButton(
                        highlighted = false,
                        onClick = {
                            isSettingsDrawerOpen = false
                            activePanel = FlightBottomPanel.Camera
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = if (isCompact) 8.dp else 12.dp)
                    )

                    FlightHsiPanel(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = if (isCompact) 88.dp else 96.dp)
                    )
                }

                FlightToolsMenuSheet(
                    visible = showToolsMenu,
                    engineerMode = engineerMode,
                    onDismiss = { showToolsMenu = false },
                    onSelectCamera = {
                        showToolsMenu = false
                        isSettingsDrawerOpen = false
                        activePanel = FlightBottomPanel.Camera
                    },
                    onSelectLink = {
                        showToolsMenu = false
                        isSettingsDrawerOpen = false
                        activePanel = FlightBottomPanel.Link
                    },
                    onSelectInfo = {
                        showToolsMenu = false
                        isSettingsDrawerOpen = false
                        activePanel = FlightBottomPanel.Info
                    },
                    onOpenDjiSettings = {
                        showToolsMenu = false
                        activePanel = null
                        isSettingsDrawerOpen = true
                    },
                    onToggleEngineerMode = {
                        engineerMode = !engineerMode
                        if (!engineerMode) {
                            activePanel = null
                        }
                        showToolsMenu = false
                    }
                )

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
private fun FlightActionRail(
    onTakeOff: () -> Unit,
    onReturnHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TakeOffButton(onConfirm = onTakeOff)
        ReturnHomeButton(onConfirm = onReturnHome)
    }
}

@Composable
private fun FlightHsiPanel(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.height(64.dp),
        contentAlignment = Alignment.Center
    ) {
        HorizontalSituationIndicatorWidget(
            modifier = Modifier.fillMaxHeight()
        )
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
    Surface(
        modifier = modifier.fillMaxHeight(if (isCompact) 0.84f else 0.72f),
        shape = RoundedCornerShape(26.dp),
        color = Color(0xD90A0F14),
        border = BorderStroke(1.dp, OverlayBorderColor)
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
                GhostIconButton(
                    icon = Icons.Default.Close,
                    onClick = onClose,
                    contentDescription = "关闭",
                    showBorder = false
                )
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FlightBottomPanel.values().forEach { panel ->
                    PanelLauncherChip(
                        label = when (panel) {
                            FlightBottomPanel.Camera -> "相机"
                            FlightBottomPanel.Link -> "链路"
                            FlightBottomPanel.Info -> "机体"
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
                    GhostIconButton(
                        icon = Icons.Default.Close,
                        onClick = onClose,
                        contentDescription = "关闭",
                        showBorder = false
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
/**
 * 确认送达对话框：须已在本会话成功上传凭证；可选完成说明（t32）。
 */
@Composable
private fun FlightConfirmDeliveryDialog(
    deliverables: List<DeliverableItemUi>,
    onRemove: (String) -> Unit,
    onRetry: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var note by remember { mutableStateOf("") }
    val canConfirm = DeliveryCompletionGate.canConfirmDelivery(deliverables)
    val uploading = DeliveryCompletionGate.hasBlockingUpload(deliverables)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认送达", color = Color.White) },
        text = {
            Column {
                Text(
                    if (canConfirm) {
                        "送达凭证已上传。确认后订单进入待验收，用户将在飞翼通 App 中验收。"
                    } else {
                        DeliveryCompletionGate.REASON_EVIDENCE_REQUIRED
                    },
                    color = Color.White.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(8.dp))

                deliverables.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = (entry.item.fileName ?: "交付成果") + " · " + when (entry.phase) {
                                    DeliverableItemPhase.PENDING -> "待上传"
                                    DeliverableItemPhase.UPLOADING -> "上传中…"
                                    DeliverableItemPhase.UPLOADED -> "已上传"
                                    DeliverableItemPhase.FAILED -> "失败"
                                },
                                color = Color.White,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            entry.message?.let { message ->
                                Text(
                                    text = message,
                                    color = Color(0xFFFFB74D),
                                    fontSize = 10.sp
                                )
                            }
                        }
                        when (entry.phase) {
                            DeliverableItemPhase.UPLOADING -> {}
                            DeliverableItemPhase.FAILED -> {
                                TextButton(onClick = { onRetry(entry.item.id) }) {
                                    Text("重试", fontSize = 11.sp, color = AccentBlue)
                                }
                                TextButton(onClick = { onRemove(entry.item.id) }) {
                                    Text("移除", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                                }
                            }
                            else -> {
                                TextButton(onClick = { onRemove(entry.item.id) }) {
                                    Text("移除", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 500) note = it },
                    placeholder = {
                        Text("完成说明（可选，≤500 字）", color = Color.White.copy(alpha = 0.5f))
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm && !uploading,
                onClick = { onConfirm(note.trim().ifBlank { null }) }
            ) {
                Text("确认送达", color = AccentBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color.White.copy(alpha = 0.6f))
            }
        },
        containerColor = Color(0xFF1E293B),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}
