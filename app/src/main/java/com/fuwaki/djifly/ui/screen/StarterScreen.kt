package com.fuwaki.djifly.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.fuwaki.djifly.sdk.DjiSdkManager
import com.fuwaki.djifly.sdk.DjiSdkStatus
import com.fuwaki.djifly.sdk.MsdkInfo

/**
 * DJI Fly 启动界面 - 响应式紧凑设计 (参考 MSDK Sample 丰富信息)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarterScreen(
    sdkManager: DjiSdkManager = DjiSdkManager.getInstance(),
    navController: NavController? = null
) {
    val sdkStatus by sdkManager.sdkStatus.collectAsState()
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    
    val isSmallScreen = screenHeight < 640.dp
    val verticalGap = if (isSmallScreen) 6.dp else 12.dp
    val horizontalPadding = if (isSmallScreen) 16.dp else 24.dp

    Scaffold(
        bottomBar = {
            BottomActionButton(sdkStatus, navController, horizontalPadding, isSmallScreen)
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(verticalGap)
            ) {
                Spacer(modifier = Modifier.height(if (isSmallScreen) 8.dp else 16.dp))

                // 1. 标题栏
                HeaderSection(isSmallScreen)

                // 2. 核心状态概要
                StatusSummaryRow(sdkStatus)

                // 3. MSDK 运行环境信息 (参考 Sample 代码丰富)
                MsdkInfoCard(sdkStatus.msdkInfo, isSmallScreen)

                // 4. 产品/硬件详情
                ProductInfoCard(sdkStatus, isSmallScreen)

                // 5. 进度通知
                ProgressSection(sdkStatus)

                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeaderSection(isSmallScreen: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = if (isSmallScreen) 2.dp else 4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Build,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(if (isSmallScreen) 20.dp else 24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "DJI Fly 控制中心",
            style = if (isSmallScreen) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StatusSummaryRow(sdkStatus: DjiSdkStatus) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val isSdkHealthy = sdkStatus.connectionState !is com.fuwaki.djifly.sdk.SdkConnectionState.RegistrationFailed
        CompactIndicatorCard(
            label = "SDK",
            value = sdkStatus.sdkStatusText,
            color = if (isSdkHealthy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        CompactIndicatorCard(
            label = "连接",
            value = if (sdkStatus.isProductConnected) "已就绪" else "未连接",
            color = if (sdkStatus.isProductConnected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MsdkInfoCard(info: MsdkInfo, isSmallScreen: Boolean) {
    InfoGroupCard(title = "MSDK 环境信息", icon = Icons.Default.Info) {
        Row(modifier = Modifier.fillMaxWidth()) {
            InfoItem("版本", info.sdkVersion, Modifier.weight(1f))
            InfoItem("LDM模式", if (info.isLdmEnabled) "开启" else "关闭", Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            InfoItem("国家码", info.countryCode, Modifier.weight(1f))
            InfoItem("Debug", if (info.isDebug) "Yes" else "No", Modifier.weight(1f))
        }
    }
}

@Composable
private fun ProductInfoCard(sdkStatus: DjiSdkStatus, isSmallScreen: Boolean) {
    val info = sdkStatus.productInfo
    InfoGroupCard(title = "设备硬件参数", icon = Icons.Default.Settings) {
        Row(modifier = Modifier.fillMaxWidth()) {
            InfoItem("设备", info.name, Modifier.weight(1f))
            InfoItem("型号", info.type.toString(), Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            InfoItem("固件", info.firmwareVersion, Modifier.weight(1f))
            InfoItem("SN码", info.serialNumber, Modifier.weight(1f))
        }
    }
}

@Composable
private fun InfoGroupCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun CompactIndicatorCard(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.1f),
        border = CardDefaults.outlinedCardBorder(enabled = true).copy(brush = Brush.linearGradient(listOf(color.copy(alpha = 0.5f), color.copy(alpha = 0.5f))))
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.width(6.dp))
            Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.ExtraBold, color = color)
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        Text(
            text = if (value.isBlank() || value == "UNKNOWN") "---" else value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProgressSection(sdkStatus: DjiSdkStatus) {
    if (sdkStatus.initProgress < 100) {
        CompactProgressBar("初始化", sdkStatus.initProgress / 100f)
    }
}

@Composable
private fun CompactProgressBar(label: String, progress: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(48.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.weight(1f).height(4.dp).clip(CircleShape),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun BottomActionButton(
    sdkStatus: DjiSdkStatus, 
    navController: NavController?, 
    padding: androidx.compose.ui.unit.Dp,
    isSmallScreen: Boolean
) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = padding, vertical = if (isSmallScreen) 8.dp else 16.dp)) {
        if (sdkStatus.isProductConnected && navController != null) {
            Button(
                onClick = { navController.navigate("flight") },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("进入飞行控制台", fontWeight = FontWeight.Bold)
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth().height(44.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("等待无人机接入...", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
