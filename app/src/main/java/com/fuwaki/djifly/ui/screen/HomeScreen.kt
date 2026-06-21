package com.fuwaki.djifly.ui.screen

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fuwaki.djifly.data.models.TaskType
import com.fuwaki.djifly.data.models.TaskVo
import com.fuwaki.djifly.ui.theme.AccentBlue
import com.fuwaki.djifly.ui.theme.AccentBlueDark
import com.fuwaki.djifly.ui.theme.Divider
import com.fuwaki.djifly.ui.theme.StatusAmber
import com.fuwaki.djifly.ui.theme.StatusGreen
import com.fuwaki.djifly.ui.theme.SurfaceCard
import com.fuwaki.djifly.ui.theme.SurfaceCardLight
import com.fuwaki.djifly.ui.theme.SurfaceDark
import com.fuwaki.djifly.ui.theme.TextPrimary
import com.fuwaki.djifly.ui.theme.TextSecondary
import com.fuwaki.djifly.ui.theme.TextTertiary
import com.fuwaki.djifly.ui.viewmodel.HomeViewModel

/**
 * Pilot home dashboard.
 *
 * Shows location header, today's overview stats, active tasks,
 * quick-action grid, and recommended new tasks.
 */
@Composable
fun HomeScreen(
    onNavigateToTaskDetail: (String) -> Unit,
    onNavigateToStarter: () -> Unit,
    onNavigateToTaskSquare: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToMessages: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
            .statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
    ) {
        // ── Location header ──
        item {
            LocationHeader(
                cityName = null, // will show "定位中..."
                onNotificationClick = onNavigateToMessages
            )
        }

        // ── Today's overview card ──
        item {
            TodayOverviewCard(
                todayOrders = uiState.todayOrders,
                todayEarnings = uiState.todayEarnings,
                rating = uiState.rating,
                completionRate = uiState.completionRate
            )
        }

        // ── Active tasks section ──
        item {
            SectionHeader(
                title = "进行中任务",
                actionText = "查看全部",
                onActionClick = onNavigateToTaskSquare
            )
        }

        if (uiState.activeTasks.isEmpty() && !uiState.isLoading) {
            item {
                EmptyStateHint(text = "暂无进行中任务")
            }
        } else {
            items(uiState.activeTasks, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    onClick = { onNavigateToTaskDetail(task.taskNum) }
                )
            }
        }

        // ── Quick actions grid ──
        item {
            Spacer(modifier = Modifier.height(20.dp))
            QuickActionsGrid(
                onTaskSquareClick = onNavigateToTaskSquare,
                onFlightClick = onNavigateToStarter,
                onHistoryClick = onNavigateToHistory,
                onMessagesClick = onNavigateToMessages
            )
        }

        // ── New tasks recommendation ──
        item {
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(
                title = "🌟 新任务推荐",
                actionText = "查看更多",
                onActionClick = onNavigateToTaskSquare
            )
        }

        if (uiState.recommendedTasks.isEmpty() && !uiState.isLoading) {
            item {
                EmptyStateHint(text = "暂无新任务推荐")
            }
        } else {
            items(uiState.recommendedTasks, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    onClick = { onNavigateToTaskDetail(task.taskNum) }
                )
            }
        }

        // ── Loading indicator ──
        if (uiState.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = AccentBlue,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// Private composables
// ═══════════════════════════════════════════════════════

@Composable
private fun LocationHeader(
    cityName: String?,
    onNotificationClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = AccentBlue,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = cityName ?: "定位中...",
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onNotificationClick) {
            Box {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "通知",
                    tint = TextSecondary
                )
                // Notification badge dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(StatusAmber)
                )
            }
        }
    }
}

@Composable
private fun TodayOverviewCard(
    todayOrders: Int,
    todayEarnings: String,
    rating: Double,
    completionRate: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(AccentBlue, AccentBlueDark)
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OverviewStatItem(label = "今日接单", value = "$todayOrders")
                OverviewStatItem(label = "今日收益", value = todayEarnings)
                OverviewStatItem(label = "评分", value = "%.1f".format(rating))
                OverviewStatItem(label = "完成率", value = completionRate)
            }
        }
    }
}

@Composable
private fun OverviewStatItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.weight(1f))
        if (actionText != null && onActionClick != null) {
            Text(
                text = actionText,
                color = TextTertiary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable { onActionClick() }
            )
        }
    }
}

@Composable
private fun TaskCard(
    task: TaskVo,
    onClick: () -> Unit
) {
    val taskType = TaskType.fromValue(task.taskType)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, Divider)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Task type badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = when (taskType) {
                    TaskType.AERIAL_PHOTO -> AccentBlue.copy(alpha = 0.15f)
                    TaskType.SURVEY -> StatusGreen.copy(alpha = 0.15f)
                    TaskType.TRANSPORT -> StatusAmber.copy(alpha = 0.15f)
                    TaskType.MONITORING -> Color(0xFF8B5CF6).copy(alpha = 0.15f)
                    else -> SurfaceCardLight
                }
            ) {
                Text(
                    text = taskType?.label ?: "未知",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = when (taskType) {
                        TaskType.AERIAL_PHOTO -> AccentBlue
                        TaskType.SURVEY -> StatusGreen
                        TaskType.TRANSPORT -> StatusAmber
                        TaskType.MONITORING -> Color(0xFF8B5CF6)
                        else -> TextTertiary
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.taskName.ifBlank { task.taskNum },
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = task.taskNum,
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (task.totalAmount != null) {
                Text(
                    text = "¥${task.totalAmount}",
                    color = AccentBlue,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun QuickActionsGrid(
    onTaskSquareClick: () -> Unit,
    onFlightClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onMessagesClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "快捷操作",
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionItem(
                icon = Icons.Default.Search,
                label = "任务大厅",
                onClick = onTaskSquareClick,
                modifier = Modifier.weight(1f)
            )
            QuickActionItem(
                icon = Icons.Default.PlayArrow,
                label = "飞行控制",
                onClick = onFlightClick,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionItem(
                icon = Icons.Default.History,
                label = "历史任务",
                onClick = onHistoryClick,
                modifier = Modifier.weight(1f)
            )
            QuickActionItem(
                icon = Icons.Default.ChatBubble,
                label = "消息",
                onClick = onMessagesClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(72.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, Divider)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = AccentBlue,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun EmptyStateHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = TextTertiary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
