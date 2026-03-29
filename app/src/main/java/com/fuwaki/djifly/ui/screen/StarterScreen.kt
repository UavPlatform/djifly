package com.fuwaki.djifly.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.fuwaki.djifly.platform.registration.PlatformRegistrationManager
import com.fuwaki.djifly.platform.registration.PlatformRegistrationState
import com.fuwaki.djifly.sdk.DjiSdkManager
import com.fuwaki.djifly.sdk.DjiSdkStatus
import com.fuwaki.djifly.sdk.SdkConnectionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StarterScreen(
    sdkManager: DjiSdkManager = DjiSdkManager.getInstance(),
    registrationManager: PlatformRegistrationManager,
    navController: NavController? = null
) {
    val sdkStatus by sdkManager.sdkStatus.collectAsState()
    val registrationState by registrationManager.state.collectAsState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isWideLayout = configuration.screenWidthDp >= 900

    val serverPanel = buildServerPanelState(registrationState)
    val overview = buildOverviewState(
        cloudControlAvailable = serverPanel.cloudControlAvailable,
        aircraftConnected = sdkStatus.isProductConnected
    )

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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OverviewCard(overview = overview)

        if (isWideLayout) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RemoteControllerStatusCard(
                    sdkStatus = sdkStatus,
                    modifier = Modifier.weight(1f)
                )
                ServerStatusCard(
                    registrationState = registrationState,
                    serverPanel = serverPanel,
                    sdkStatus = sdkStatus,
                    stateUpdatedAtMillis = serverStateUpdatedAtMillis,
                    onRetryNow = { registrationManager.retryNow() },
                    onCopyDiagnostics = { text ->
                        copyToClipboard(context, text)
                        Toast
                            .makeText(context, "服务器诊断信息已复制", Toast.LENGTH_SHORT)
                            .show()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RemoteControllerStatusCard(
                    sdkStatus = sdkStatus,
                    modifier = Modifier.weight(1f)
                )
                ServerStatusCard(
                    registrationState = registrationState,
                    serverPanel = serverPanel,
                    sdkStatus = sdkStatus,
                    stateUpdatedAtMillis = serverStateUpdatedAtMillis,
                    onRetryNow = { registrationManager.retryNow() },
                    onCopyDiagnostics = { text ->
                        copyToClipboard(context, text)
                        Toast
                            .makeText(context, "服务器诊断信息已复制", Toast.LENGTH_SHORT)
                            .show()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        FlightEntryCard(
            navController = navController,
            cloudControlAvailable = serverPanel.cloudControlAvailable,
            aircraftConnected = sdkStatus.isProductConnected
        )

        DiagnosticsCard(
            expanded = diagnosticsExpanded,
            onToggle = { diagnosticsExpanded = !diagnosticsExpanded },
            sdkStatus = sdkStatus,
            registrationState = registrationState,
            stateUpdatedAtMillis = serverStateUpdatedAtMillis
        )
    }
}

@Composable
private fun OverviewCard(overview: OverviewState) {
    Surface(
        color = overview.color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "云端控制总览",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = overview.headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = overview.color
            )
            Text(
                text = overview.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RemoteControllerStatusCard(
    sdkStatus: DjiSdkStatus,
    modifier: Modifier = Modifier
) {
    InfoGroupCard(
        title = "遥控器状态",
        icon = Icons.Default.Build,
        modifier = modifier
    ) {
        val productInfo = sdkStatus.productInfo
        val connected = sdkStatus.isProductConnected

        SummaryItem(
            label = "连接",
            value = if (connected) "已接入" else "未连接",
            valueColor = if (connected) Color(0xFF2E7D32) else Color(0xFFFB8C00)
        )
        SummaryItem(label = "遥控器型号", value = displayValue(productInfo.controllerModel))
        SummaryItem(label = "飞机序列号", value = displayValue(productInfo.serialNumber))
        SummaryItem(label = "上行信号", value = signalValue(sdkStatus.flightData.uplinkQuality, connected))
        SummaryItem(label = "下行信号", value = signalValue(sdkStatus.flightData.downlinkQuality, connected))
        SummaryItem(label = "SDK", value = sdkStatus.sdkStatusText)
    }
}

@Composable
private fun ServerStatusCard(
    registrationState: PlatformRegistrationState,
    serverPanel: ServerPanelState,
    sdkStatus: DjiSdkStatus,
    stateUpdatedAtMillis: Long,
    onRetryNow: () -> Unit,
    onCopyDiagnostics: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    InfoGroupCard(
        title = "服务器状态",
        icon = Icons.Default.Settings,
        modifier = modifier
    ) {
        Surface(
            color = serverPanel.color.copy(alpha = 0.10f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = serverPanel.status,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = serverPanel.color
                )
                Text(
                    text = serverPanel.primaryReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (serverPanel.cloudControlAvailable) {
            Text(
                text = "云端控制可用",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF2E7D32),
                fontWeight = FontWeight.Medium
            )
        } else {
            Text(
                text = "云端控制当前不可用",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFC62828),
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (serverPanel.showRetryAction) {
            OutlinedButton(
                onClick = onRetryNow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("立即重试")
            }
            Spacer(modifier = Modifier.height(8.dp))
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
    }
}

@Composable
private fun FlightEntryCard(
    navController: NavController?,
    cloudControlAvailable: Boolean,
    aircraftConnected: Boolean
) {
    val hint = when {
        !aircraftConnected -> "可进入飞控页，但当前未检测到飞机连接。"
        !cloudControlAvailable -> "可进入飞控页，但云端控制当前不可用。"
        else -> "飞机和服务器均已连接，云端控制可用。"
    }

    val hintColor = when {
        !aircraftConnected -> Color(0xFFFB8C00)
        !cloudControlAvailable -> Color(0xFFC62828)
        else -> Color(0xFF2E7D32)
    }

    InfoGroupCard(
        title = "飞控入口",
        icon = Icons.Default.PlayArrow
    ) {
        Button(
            onClick = { navController?.navigate("flight") },
            enabled = navController != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "进入飞行控制台",
                fontWeight = FontWeight.Bold
            )
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
    stateUpdatedAtMillis: Long
) {
    InfoGroupCard(
        title = "诊断信息",
        icon = Icons.Default.Info
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (expanded) "已展开：启动流程与调试字段" else "默认折叠：点击查看启动流程与调试字段",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            OutlinedButton(onClick = onToggle) {
                Text(if (expanded) "收起" else "展开")
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryItem(label = "SDK 状态", value = sdkStatus.sdkStatusText)
                SummaryItem(label = "初始化进度", value = "${sdkStatus.initProgress.coerceIn(0, 100)}%")
                SummaryItem(label = "状态更新时间", value = formatLocalTime(stateUpdatedAtMillis))

                Text(
                    text = "启动流程（诊断）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
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

private fun buildOverviewState(
    cloudControlAvailable: Boolean,
    aircraftConnected: Boolean
): OverviewState {
    return when {
        cloudControlAvailable -> OverviewState(
            headline = "云控通道已就绪",
            detail = "服务器连接正常，可执行云端控制。",
            color = Color(0xFF2E7D32)
        )

        aircraftConnected -> OverviewState(
            headline = "飞机已接入，等待云控通道",
            detail = "可先进入飞控页，服务器连接成功后自动具备云控能力。",
            color = Color(0xFFFB8C00)
        )

        else -> OverviewState(
            headline = "等待设备接入",
            detail = "先连接遥控器与飞机，再建立服务器连接。",
            color = Color(0xFF607D8B)
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

private data class OverviewState(
    val headline: String,
    val detail: String,
    val color: Color
)

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
