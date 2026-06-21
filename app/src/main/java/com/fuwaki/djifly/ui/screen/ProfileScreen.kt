package com.fuwaki.djifly.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fuwaki.djifly.ui.theme.AccentBlue
import com.fuwaki.djifly.ui.theme.Divider
import com.fuwaki.djifly.ui.theme.StatusGreen
import com.fuwaki.djifly.ui.theme.StatusRed
import com.fuwaki.djifly.ui.theme.SurfaceCard
import com.fuwaki.djifly.ui.theme.SurfaceDark
import com.fuwaki.djifly.ui.theme.TextPrimary
import com.fuwaki.djifly.ui.theme.TextSecondary
import com.fuwaki.djifly.ui.theme.TextTertiary
import com.fuwaki.djifly.ui.viewmodel.ProfileViewModel

@Composable
fun ProfileScreen(
    onNavigateToDroneManage: () -> Unit,
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val userName by viewModel.userName.collectAsState()
    val userId by viewModel.userId.collectAsState()
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
            .verticalScroll(rememberScrollState())
    ) {
        // Green gradient header
        ProfileHeader(
            userName = userName,
            userId = userId
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Stats card
        StatsCard()

        Spacer(modifier = Modifier.height(16.dp))

        // Menu items
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column {
                MenuItemRow(
                    icon = "💰",
                    title = "我的钱包",
                    onClick = {
                        Toast.makeText(context, "即将上线", Toast.LENGTH_SHORT).show()
                    }
                )
                MenuDivider()
                MenuItemRow(
                    icon = "📊",
                    title = "数据中心",
                    onClick = {
                        Toast.makeText(context, "即将上线", Toast.LENGTH_SHORT).show()
                    }
                )
                MenuDivider()
                MenuItemRow(
                    icon = "✈️",
                    title = "我的无人机",
                    onClick = onNavigateToDroneManage
                )
                MenuDivider()
                MenuItemRow(
                    icon = "📋",
                    title = "接单设置",
                    onClick = {
                        Toast.makeText(context, "即将上线", Toast.LENGTH_SHORT).show()
                    }
                )
                MenuDivider()
                MenuItemRow(
                    icon = "⭐",
                    title = "评价管理",
                    onClick = {
                        Toast.makeText(context, "即将上线", Toast.LENGTH_SHORT).show()
                    }
                )
                MenuDivider()
                MenuItemRow(
                    icon = "📍",
                    title = "历史任务",
                    onClick = {
                        Toast.makeText(context, "即将上线", Toast.LENGTH_SHORT).show()
                    }
                )
                MenuDivider()
                MenuItemRow(
                    icon = "⚙️",
                    title = "设置",
                    onClick = {
                        Toast.makeText(context, "即将上线", Toast.LENGTH_SHORT).show()
                    }
                )
                MenuDivider()
                MenuItemRow(
                    icon = "📞",
                    title = "联系客服",
                    onClick = {
                        Toast.makeText(context, "即将上线", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Logout button
        OutlinedButton(
            onClick = { showLogoutDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, StatusRed)
        ) {
            Text(
                text = "退出登录",
                color = StatusRed,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // Logout confirmation dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text("确认退出", color = TextPrimary)
            },
            text = {
                Text("确定要退出登录吗？", color = TextSecondary)
            },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.logout { onLogout() }
                }) {
                    Text("确认", color = StatusRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = SurfaceCard,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }
}

@Composable
private fun ProfileHeader(
    userName: String,
    userId: Long
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        StatusGreen,
                        StatusGreen.copy(alpha = 0.7f),
                        SurfaceDark
                    )
                )
            )
            .padding(top = 48.dp, bottom = 24.dp, start = 24.dp, end = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(StatusGreen.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = userName.firstOrNull()?.toString() ?: "?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = userName.ifBlank { "未登录" },
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                color = StatusGreen.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "飞手",
                            color = StatusGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ID: $userId",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun StatsCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(label = "今日接单", value = "0")
            StatItem(label = "评分", value = "5.0")
            StatItem(label = "完成", value = "0")
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = AccentBlue,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = TextTertiary,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun MenuItemRow(
    icon: String,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            fontSize = 20.sp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun MenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = Divider
    )
}
