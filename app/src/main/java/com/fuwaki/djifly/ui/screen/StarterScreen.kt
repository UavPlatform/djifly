package com.fuwaki.djifly.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.unit.dp
import com.fuwaki.djifly.data.models.FlightEntryGate
import com.fuwaki.djifly.platform.registration.PlatformRegistrationManager
import com.fuwaki.djifly.platform.registration.PlatformRegistrationState
import com.fuwaki.djifly.sdk.DjiSdkManager
import com.fuwaki.djifly.sdk.DjiSdkStatus
import com.fuwaki.djifly.sdk.SdkConnectionState
import com.fuwaki.djifly.ui.components.ShellPrimaryButton
import com.fuwaki.djifly.ui.theme.StatusAmber
import com.fuwaki.djifly.ui.theme.StatusGreen
import com.fuwaki.djifly.ui.theme.StatusRed
import com.fuwaki.djifly.ui.viewmodel.TaskViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StarterScreen(
    sdkManager: DjiSdkManager,
    registrationManager: PlatformRegistrationManager,
    taskNum: String? = null,
    onEnterFlight: (String?) -> Unit = {},
    taskViewModel: TaskViewModel = hiltViewModel()
) {
    val sdkStatus by sdkManager.sdkStatus.collectAsState()
    val registrationState by registrationManager.state.collectAsState()
    val context = LocalContext.current

    // 1B-8：任务上下文（自由飞行 taskNum 为空 → FreeFlight，不加载任务）
    val taskDetail by taskViewModel.taskDetail.collectAsState()
    val detailLoading by taskViewModel.detailLoading.collectAsState()
    val taskContext = FlightTaskContext.resolve(taskNum, taskDetail, detailLoading)

    // 执飞门禁（TASK-DJIFLY-002）：携任务进入时先校验撮合状态，非 CONFIRMED/等价状态拒绝进入飞行控制台。
    // 任务详情未就绪（加载中/加载失败）同样不放行——未校验 ≠ 已确认，失败时可退出重进重新校验。
    val gateVerifying = taskContext is FlightTaskContext.Loading
    val flightGateReason = remember(taskContext, taskDetail) {
        when (taskContext) {
            is FlightTaskContext.Ready -> taskDetail?.let { FlightEntryGate.denyReason(it) }
            FlightTaskContext.Loading -> "正在校验任务状态，校验通过后才能进入飞行控制台"
            FlightTaskContext.FreeFlight -> null
        }
    }

    LaunchedEffect(taskNum) {
        if (!taskNum.isNullOrBlank()) {
            taskViewModel.loadTaskDetail(taskNum)
        }
    }

    val serverPanel = buildServerPanelState(registrationState)

    var diagnosticsExpanded by rememberSaveable { mutableStateOf(false) }
    var serverStateUpdatedAtMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(registrationState) {
        serverStateUpdatedAtMillis = System.currentTimeMillis()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "起飞检查",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        when (val ctx = taskContext) {
            is FlightTaskContext.Ready -> TaskContextCard(ctx)
            is FlightTaskContext.Loading -> TaskContextLoadingCard(
                onRetry = {
                    taskNum?.takeIf { it.isNotBlank() }?.let { taskViewModel.loadTaskDetail(it) }
                }
            )
            FlightTaskContext.FreeFlight -> {}
        }

        PreflightStepsCard(
            aircraftConnected = sdkStatus.isProductConnected,
            cloudControlAvailable = serverPanel.cloudControlAvailable,
            serverPanel = serverPanel,
            gateVerifying = gateVerifying,
            gateReason = flightGateReason,
            hasOrder = taskContext is FlightTaskContext.Ready
        )

        FlightEntryCard(
            gateReason = flightGateReason,
            gateVerifying = gateVerifying,
            onEnterFlight = {
                // 携任务进入（1B-8）：FreeFlight 时传 null 保持自由飞行语义
                onEnterFlight((taskContext as? FlightTaskContext.Ready)?.taskNum)
            },
            onStartLiveStream = { taskViewModel.requestRiderLiveStream() },
            cloudControlAvailable = serverPanel.cloudControlAvailable,
            aircraftConnected = sdkStatus.isProductConnected
        )

        DiagnosticsCard(
            expanded = diagnosticsExpanded,
            onToggle = { diagnosticsExpanded = !diagnosticsExpanded },
            sdkStatus = sdkStatus,
            registrationState = registrationState,
            serverPanel = serverPanel,
            stateUpdatedAtMillis = serverStateUpdatedAtMillis,
            onRetryNow = { registrationManager.retryNow() },
            onCopyDiagnostics = { text ->
                copyToClipboard(context, text)
                Toast.makeText(context, "诊断信息已复制", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

private enum class StepDotState { Pending, Active, Done, Error }

@Composable
private fun PreflightStepsCard(
    aircraftConnected: Boolean,
    cloudControlAvailable: Boolean,
    serverPanel: ServerPanelState,
    gateVerifying: Boolean,
    gateReason: String?,
    hasOrder: Boolean
) {
    val step1 = if (aircraftConnected) StepDotState.Done else StepDotState.Pending
    val step2 = when {
        cloudControlAvailable -> StepDotState.Done
        aircraftConnected && serverPanel.showRetryAction -> StepDotState.Error
        aircraftConnected -> StepDotState.Active
        else -> StepDotState.Pending
    }
    val step3 = when {
        gateVerifying -> StepDotState.Active
        gateReason != null -> StepDotState.Error
        aircraftConnected && cloudControlAvailable -> StepDotState.Done
        else -> StepDotState.Pending
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "作业就绪步骤",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            PreflightStepRow(
                title = "连飞机",
                subtitle = if (aircraftConnected) "遥控器与飞机已接入" else "请连接遥控器并开机",
                state = step1
            )
            PreflightStepRow(
                title = "平台在线",
                subtitle = serverPanel.primaryReason,
                state = step2
            )
            PreflightStepRow(
                title = if (hasOrder) "订单可执飞" else "可执飞",
                subtitle = when {
                    gateVerifying -> "正在校验订单状态…"
                    gateReason != null -> gateReason
                    hasOrder -> "订单已确认，满足进入作业条件"
                    else -> "连接测试模式，无关联订单"
                },
                state = step3
            )
        }
    }
}

@Composable
private fun PreflightStepRow(
    title: String,
    subtitle: String,
    state: StepDotState
) {
    val dotColor = when (state) {
        StepDotState.Done -> StatusGreen
        StepDotState.Active -> StatusAmber
        StepDotState.Error -> StatusRed
        StepDotState.Pending -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FlightEntryCard(
    gateReason: String? = null,
    gateVerifying: Boolean = false,
    onEnterFlight: () -> Unit,
    onStartLiveStream: () -> Unit = {},
    cloudControlAvailable: Boolean,
    aircraftConnected: Boolean
) {
    val hint = when {
        gateReason != null -> gateReason
        !aircraftConnected -> "可先进入作业页，但当前未检测到飞机连接。"
        !cloudControlAvailable -> "可先进入作业页，平台设备通道尚未就绪。"
        else -> "飞机与平台均已连接，可以开始作业。"
    }

    val hintColor = when {
        gateReason != null -> Color(0xFFFB8C00)
        !aircraftConnected -> Color(0xFFFB8C00)
        !cloudControlAvailable -> Color(0xFFC62828)
        else -> Color(0xFF2E7D32)
    }

    InfoGroupCard(
        title = "进入作业",
        icon = Icons.Default.PlayArrow
    ) {
        ShellPrimaryButton(
            text = when {
                gateReason != null -> "任务未确认，无法进入"
                else -> "进入作业"
            },
            onClick = onEnterFlight,
            enabled = gateReason == null,
            loading = gateVerifying,
            loadingText = "正在校验任务状态…"
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onStartLiveStream,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("开播图传")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = hintColor
        )
    }
}

@Composable
private fun DiagnosticsCard(
    expanded: Boolean,
    onToggle: () -> Unit,
    sdkStatus: DjiSdkStatus,
    registrationState: PlatformRegistrationState,
    serverPanel: ServerPanelState,
    stateUpdatedAtMillis: Long,
    onRetryNow: () -> Unit,
    onCopyDiagnostics: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "诊断信息",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!expanded) {
                Text(
                    text = "遥控器、平台通道与启动流程详情",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "遥控器",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val productInfo = sdkStatus.productInfo
                    val connected = sdkStatus.isProductConnected
                    SummaryItem(
                        label = "连接",
                        value = if (connected) "已接入" else "未连接",
                        valueColor = if (connected) StatusGreen else StatusAmber
                    )
                    SummaryItem(label = "遥控器型号", value = displayValue(productInfo.controllerModel))
                    SummaryItem(label = "飞机序列号", value = displayValue(productInfo.serialNumber))
                    SummaryItem(
                        label = "上行信号",
                        value = signalValue(sdkStatus.flightData.uplinkQuality, connected)
                    )
                    SummaryItem(
                        label = "下行信号",
                        value = signalValue(sdkStatus.flightData.downlinkQuality, connected)
                    )

                    Text(
                        text = "平台通道",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    SummaryItem(label = "状态", value = serverPanel.status, valueColor = serverPanel.color)
                    SummaryItem(label = "说明", value = serverPanel.primaryReason)

                    if (serverPanel.showRetryAction) {
                        OutlinedButton(
                            onClick = onRetryNow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("立即重试连接")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            onCopyDiagnostics(
                                buildServerDiagnosticText(
                                    registrationState = registrationState,
                                    sdkStatus = sdkStatus,
                                    stateUpdatedAtMillis = stateUpdatedAtMillis
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("复制诊断")
                    }

                    Text(
                        text = "启动流程",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    SummaryItem(label = "SDK 状态", value = sdkStatus.sdkStatusText)
                    SummaryItem(
                        label = "初始化进度",
                        value = "${sdkStatus.initProgress.coerceIn(0, 100)}%"
                    )
                    SummaryItem(
                        label = "状态更新时间",
                        value = formatLocalTime(stateUpdatedAtMillis)
                    )
                    buildStartupDiagnosticSteps(
                        sdkStatus = sdkStatus,
                        registrationState = registrationState
                    ).forEach { step ->
                        SummaryItem(
                            label = step.title,
                            value = "${step.stateLabel} · ${step.detail}"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoGroupCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SummaryItem(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            modifier = Modifier.widthIn(min = 72.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun buildServerPanelState(registrationState: PlatformRegistrationState): ServerPanelState {
    return when (registrationState) {
        PlatformRegistrationState.WaitingForAircraft -> ServerPanelState(
            status = "待连接",
            primaryReason = "等待飞机连接并读取 SN 后发起注册",
            color = Color(0xFF90A4AE),
            cloudControlAvailable = false,
            showRetryAction = false
        )

        is PlatformRegistrationState.Registering -> ServerPanelState(
            status = "连接中",
            primaryReason = "正在向服务器登记 ${registrationState.serialNumber}",
            color = Color(0xFF1E88E5),
            cloudControlAvailable = false,
            showRetryAction = false
        )

        is PlatformRegistrationState.RetryScheduled -> ServerPanelState(
            status = "重试中",
            primaryReason = "${registrationState.retryAfterSeconds} 秒后进行第 ${registrationState.nextAttempt} 次重试",
            color = Color(0xFFFB8C00),
            cloudControlAvailable = false,
            showRetryAction = true
        )

        is PlatformRegistrationState.Registered -> ServerPanelState(
            status = "已连接",
            primaryReason = "已成功建立服务器连接",
            color = Color(0xFF2E7D32),
            cloudControlAvailable = true,
            showRetryAction = false
        )

        is PlatformRegistrationState.Failed -> ServerPanelState(
            status = "连接异常",
            primaryReason = registrationState.reason,
            color = Color(0xFFC62828),
            cloudControlAvailable = false,
            showRetryAction = true
        )
    }
}

private fun buildStartupDiagnosticSteps(
    sdkStatus: DjiSdkStatus,
    registrationState: PlatformRegistrationState
): List<StartupDiagnosticStep> {
    val serialReady = hasRealValue(sdkStatus.productInfo.serialNumber)

    val sdkStep = when (val state = sdkStatus.connectionState) {
        is SdkConnectionState.RegistrationFailed -> StartupDiagnosticStep(
            title = "SDK 初始化",
            stateLabel = "异常",
            detail = state.error.ifBlank { "DJI SDK 注册失败" }
        )

        is SdkConnectionState.Initializing -> StartupDiagnosticStep(
            title = "SDK 初始化",
            stateLabel = "进行中",
            detail = "当前进度 ${sdkStatus.initProgress.coerceIn(0, 100)}%"
        )

        else -> StartupDiagnosticStep(
            title = "SDK 初始化",
            stateLabel = "完成",
            detail = "SDK 初始化完成"
        )
    }

    val aircraftStep = if (sdkStatus.isProductConnected) {
        StartupDiagnosticStep(
            title = "飞机接入",
            stateLabel = "完成",
            detail = displayValue(sdkStatus.productInfo.name)
        )
    } else {
        StartupDiagnosticStep(
            title = "飞机接入",
            stateLabel = "等待",
            detail = "等待遥控器与飞机接入"
        )
    }

    val serialStep = if (serialReady) {
        StartupDiagnosticStep(
            title = "读取 SN",
            stateLabel = "完成",
            detail = displayValue(sdkStatus.productInfo.serialNumber)
        )
    } else {
        StartupDiagnosticStep(
            title = "读取 SN",
            stateLabel = "等待",
            detail = "飞机接入后自动读取"
        )
    }

    val serverStep = when (registrationState) {
        PlatformRegistrationState.WaitingForAircraft -> StartupDiagnosticStep(
            title = "服务器注册",
            stateLabel = "等待",
            detail = "等待飞机接入与 SN"
        )

        is PlatformRegistrationState.Registering -> StartupDiagnosticStep(
            title = "服务器注册",
            stateLabel = "进行中",
            detail = "正在注册 ${registrationState.serialNumber}"
        )

        is PlatformRegistrationState.RetryScheduled -> StartupDiagnosticStep(
            title = "服务器注册",
            stateLabel = "重试",
            detail = "${registrationState.retryAfterSeconds} 秒后重试"
        )

        is PlatformRegistrationState.Registered -> StartupDiagnosticStep(
            title = "服务器注册",
            stateLabel = "完成",
            detail = "已连接"
        )

        is PlatformRegistrationState.Failed -> StartupDiagnosticStep(
            title = "服务器注册",
            stateLabel = "异常",
            detail = registrationState.reason
        )
    }

    return listOf(sdkStep, aircraftStep, serialStep, serverStep)
}

private fun buildServerDiagnosticText(
    registrationState: PlatformRegistrationState,
    sdkStatus: DjiSdkStatus,
    stateUpdatedAtMillis: Long
): String {
    return buildString {
        appendLine("timestamp=${formatLocalTime(System.currentTimeMillis())}")
        appendLine("state_updated_at=${formatLocalTime(stateUpdatedAtMillis)}")
        appendLine("registration_state=${registrationStateLabel(registrationState)}")
        appendLine("cloud_control_available=${registrationState is PlatformRegistrationState.Registered}")
        appendLine("sdk_state=${sdkStatus.sdkStatusText}")
        appendLine("sdk_progress=${sdkStatus.initProgress.coerceIn(0, 100)}")
        appendLine("product_connected=${sdkStatus.isProductConnected}")
        appendLine("serial_number=${displayValue(sdkStatus.productInfo.serialNumber)}")
        appendLine("controller_model=${displayValue(sdkStatus.productInfo.controllerModel)}")
        appendLine("uplink_quality=${signalValue(sdkStatus.flightData.uplinkQuality, sdkStatus.isProductConnected)}")
        appendLine("downlink_quality=${signalValue(sdkStatus.flightData.downlinkQuality, sdkStatus.isProductConnected)}")

        when (registrationState) {
            PlatformRegistrationState.WaitingForAircraft -> {
                appendLine("detail=waiting_for_aircraft")
            }

            is PlatformRegistrationState.Registering -> {
                appendLine("serial=${registrationState.serialNumber}")
            }

            is PlatformRegistrationState.RetryScheduled -> {
                appendLine("serial=${registrationState.serialNumber}")
                appendLine("next_attempt=${registrationState.nextAttempt}")
                appendLine("retry_after_seconds=${registrationState.retryAfterSeconds}")
                appendLine("last_error=${registrationState.lastError}")
            }

            is PlatformRegistrationState.Registered -> {
                appendLine("serial=${registrationState.serialNumber}")
                appendLine("drone_id=${registrationState.droneId ?: "N/A"}")
                appendLine("drone_name=${registrationState.droneName}")
                appendLine("created_on_server=${registrationState.createdOnServer}")
            }

            is PlatformRegistrationState.Failed -> {
                appendLine("serial=${registrationState.serialNumber ?: "N/A"}")
                appendLine("retryable=${registrationState.retryable}")
                appendLine("reason=${registrationState.reason}")
            }
        }
    }
}

private fun registrationStateLabel(state: PlatformRegistrationState): String {
    return when (state) {
        PlatformRegistrationState.WaitingForAircraft -> "WaitingForAircraft"
        is PlatformRegistrationState.Registering -> "Registering"
        is PlatformRegistrationState.RetryScheduled -> "RetryScheduled"
        is PlatformRegistrationState.Registered -> "Registered"
        is PlatformRegistrationState.Failed -> "Failed"
    }
}

private fun copyToClipboard(context: Context, content: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("server_diagnostic", content))
}

private fun signalValue(quality: Int, connected: Boolean): String {
    if (!connected) return "---"
    return "${quality.coerceIn(0, 100)}%"
}

private fun displayValue(value: String): String {
    return if (hasRealValue(value)) value else "---"
}

private fun hasRealValue(value: String): Boolean {
    if (value.isBlank()) return false
    if (value == "N/A") return false
    if (value == "UNKNOWN") return false
    if (value == "未知设备") return false
    return true
}

private fun formatLocalTime(timeMillis: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timeMillis))
}

private data class ServerPanelState(
    val status: String,
    val primaryReason: String,
    val color: Color,
    val cloudControlAvailable: Boolean,
    val showRetryAction: Boolean
)

private data class StartupDiagnosticStep(
    val title: String,
    val stateLabel: String,
    val detail: String
)

/**
 * 任务上下文卡（1B-8）：任务名/状态/赏金/航点预览/操作提示（t32 actionHint 服务端统一计算）。
 */
@Composable
private fun TaskContextCard(context: FlightTaskContext.Ready) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "当前任务",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = context.taskName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                context.orderStatus?.let { status ->
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = "赏金 ${context.rewardText}" +
                    (context.totalDistanceText?.let { " · 距离 $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "航点参考：${context.waypointPreview}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            context.actionHint?.let { hint ->
                Text(
                    text = "提示：$hint",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun TaskContextLoadingCard(onRetry: () -> Unit = {}) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "任务上下文加载中…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // 校验未完成前不放行执飞入口；加载失败（无网络等）时可在此重新校验
            TextButton(onClick = onRetry) {
                Text("重新校验任务状态", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
