package com.fuwaki.djifly.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
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

/**
 * DJI Fly 启动界面 - 响应式紧凑设计 (MD3 最佳实践)
 * 适配不同屏幕尺寸，确保内容不超出单屏
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
    
    // 响应式间距控制
    val isSmallScreen = screenHeight < 640.dp
    val verticalGap = if (isSmallScreen) 8.dp else 16.dp
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
                Spacer(modifier = Modifier.height(if (isSmallScreen) 12.dp else 24.dp))

                // 1. 紧凑标题栏
                HeaderSection(isSmallScreen)

                // 2. 状态核心区 (水平排列以节省高度)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isSdkHealthy = sdkStatus.connectionState !is com.fuwaki.djifly.sdk.SdkConnectionState.RegistrationFailed
                    CompactStatusCard(
                        title = "SDK 状态",
                        status = sdkStatus.sdkStatusText,
                        icon = if (isSdkHealthy) Icons.Default.Check else Icons.Default.Warning,
                        color = if (isSdkHealthy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1.1f)
                    )
                    
                    CompactConnectionCard(sdkStatus, Modifier.weight(0.9f))
                }

                // 3. 产品详情 (网格化布局，无阴影平面设计更紧凑)
                CompactProductCard(sdkStatus, isSmallScreen)

                // 4. 进度通知区 (仅在活跃时显示)
                ProgressSection(sdkStatus)

                // 占位，将内容推向顶部
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeaderSection(isSmallScreen: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = if (isSmallScreen) 4.dp else 8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Build,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(if (isSmallScreen) 24.dp else 28.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "DJI Fly 控制中心",
            style = if (isSmallScreen) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun CompactStatusCard(
    title: String,
    status: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CompactConnectionCard(sdkStatus: DjiSdkStatus, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.Center) {
            ConnectionIndicator("飞行器", sdkStatus.isProductConnected)
            Spacer(modifier = Modifier.height(4.dp))
            ConnectionIndicator("SDK", sdkStatus.connectionState is com.fuwaki.djifly.sdk.SdkConnectionState.ProductConnected)
        }
    }
}

@Composable
private fun ConnectionIndicator(label: String, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (active) Color(0xFF4CAF50) else Color(0xFFF44336))
        )
    }
}

@Composable
private fun CompactProductCard(sdkStatus: DjiSdkStatus, isSmallScreen: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(if (isSmallScreen) 12.dp else 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(6.dp))
                Text("设备参数详情", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(if (isSmallScreen) 8.dp else 12.dp))
            
            // 2x2 网格化信息展示
            Row(modifier = Modifier.fillMaxWidth()) {
                InfoItem("设备名称", sdkStatus.productInfo.name, Modifier.weight(1f))
                InfoItem("型号类型", sdkStatus.productInfo.type.toString(), Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                InfoItem("固件版本", sdkStatus.productInfo.firmwareVersion, Modifier.weight(1f))
                InfoItem("序列号", sdkStatus.productInfo.serialNumber, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        Text(
            text = if (value.isBlank() || value == "UNKNOWN") "未连接" else value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProgressSection(sdkStatus: DjiSdkStatus) {
    val showInit = sdkStatus.initProgress < 100
    val showDb = sdkStatus.databaseDownloadProgress > 0 && sdkStatus.databaseDownloadProgress < 1
    
    if (showInit || showDb) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
            if (showInit) CompactProgressBar("系统初始化", sdkStatus.initProgress / 100f)
            if (showDb) CompactProgressBar("数据库同步", sdkStatus.databaseDownloadProgress)
        }
    }
}

@Composable
private fun CompactProgressBar(label: String, progress: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(64.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.weight(1f).height(4.dp).clip(CircleShape),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BottomActionButton(
    sdkStatus: DjiSdkStatus, 
    navController: NavController?, 
    padding: androidx.compose.ui.unit.Dp,
    isSmallScreen: Boolean
) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = padding, vertical = if (isSmallScreen) 12.dp else 20.dp)) {
        if (sdkStatus.isProductConnected && navController != null) {
            Button(
                onClick = { navController.navigate("flight") },
                modifier = Modifier.fillMaxWidth().height(if (isSmallScreen) 48.dp else 54.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("开始飞行任务", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth().height(if (isSmallScreen) 44.dp else 50.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp), 
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "等待无人机连接...", 
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
