package com.fuwaki.djifly.ui.screen

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fuwaki.djifly.data.models.MatchStatus
import com.fuwaki.djifly.data.models.TaskType
import com.fuwaki.djifly.data.models.TaskVo
import com.fuwaki.djifly.ui.theme.AccentBlue
import com.fuwaki.djifly.ui.theme.Divider
import com.fuwaki.djifly.ui.theme.StatusAmber
import com.fuwaki.djifly.ui.theme.StatusGreen
import com.fuwaki.djifly.ui.theme.SurfaceCard
import com.fuwaki.djifly.ui.theme.SurfaceDark
import com.fuwaki.djifly.ui.theme.TextPrimary
import com.fuwaki.djifly.ui.theme.TextSecondary
import com.fuwaki.djifly.ui.theme.TextTertiary
import com.fuwaki.djifly.ui.viewmodel.HomeViewModel

/**
 * 待执飞任务首页（REQ-DJIFLY-001 场景 1/3）。
 *
 * 只展示当前飞手**已双方确认**（matchStatus=CONFIRMED，或已 IN_PROGRESS/PENDING_ACCEPTANCE）的任务，
 * 无任务时空状态引导前往 `app/` 接单——不提供任务大厅/抢单入口（ADR-0002）。
 */
@Composable
fun HomeScreen(
    onNavigateToTaskDetail: (String) -> Unit,
    onNavigateToStarter: (String?) -> Unit = {},
    onNavigateToMyTasks: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadHomeData()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // ── Header ──
        item {
            HomeHeader(userName = uiState.userName)
        }

        // ── 待执飞任务 ──
        item {
            SectionHeader(
                title = "待执飞任务",
                subtitle = "仅展示双方已确认的任务"
            )
        }

        when {
            uiState.isLoading -> item {
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
            uiState.pendingTasks.isEmpty() -> item {
                // 场景 3：无任务时引导前往 app 接单（跨应用深链不在本任务范围，见 REQ-DJIFLY-001）
                EmptyPendingTasks(
                    onGoToApp = {
                        Toast.makeText(
                            context,
                            "接单在 app：请打开业务 App（app/）发单接单；双方确认后回到本应用执飞。",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
            else -> items(uiState.pendingTasks, key = { it.id }) { task ->
                PendingTaskCard(
                    task = task,
                    onClick = { onNavigateToTaskDetail(task.taskNum) }
                )
            }
        }

        // ── 快捷入口（无任务上下文的飞行准备 + 任务记录） ──
        item {
            Spacer(modifier = Modifier.height(20.dp))
            QuickActions(
                onStarterClick = { onNavigateToStarter(null) },
                onHistoryClick = onNavigateToMyTasks
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// Private composables
// ═══════════════════════════════════════════════════════

@Composable
private fun HomeHeader(userName: String?) {
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
            text = if (userName.isNullOrBlank()) "飞行执行" else "$userName · 飞行执行",
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TextTertiary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PendingTaskCard(task: TaskVo, onClick: () -> Unit) {
    val taskType = TaskType.fromValue(task.taskType)
    val matchStatus = MatchStatus.fromValue(task.matchStatus)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, Divider)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 类型徽标
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AccentBlue.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = taskType?.label ?: "未知类型",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = AccentBlue,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = task.taskName.ifBlank { task.taskNum },
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                MatchStatusBadge(matchStatus)
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "任务编号：${task.taskNum}",
                color = TextTertiary,
                style = MaterialTheme.typography.bodySmall
            )

            // 关键约束：约定时间 / 货物 / 报价 / 操作提示
            val constraints = buildList {
                task.scheduledTime?.takeIf { it.isNotBlank() }?.let { add("约定时间 $it") }
                task.cargoWeightKg?.let { add("载重 ${it.toPlainString()} kg") }
                task.cargoCategory?.takeIf { it.isNotBlank() }?.let { add("货类 $it") }
                task.aircraftModelName?.takeIf { it.isNotBlank() }?.let { add("机型 $it") }
                task.quotedAmount?.let { add("锁价 ¥${it.toPlainString()}") }
            }
            if (constraints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = constraints.joinToString(" · "),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 地点：航点参考（后端任务以航点表达起降/作业位置）
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "地点：${FlightTaskContext.waypointPreview(task.waypoints)}",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            task.actionHint?.takeIf { it.isNotBlank() }?.let { hint ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = hint,
                    color = AccentBlue,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun MatchStatusBadge(status: MatchStatus?) {
    val (text, color) = when (status) {
        MatchStatus.CONFIRMED -> "已确认" to StatusGreen
        MatchStatus.PENDING_ACCEPTANCE -> "待验收" to StatusAmber
        else -> "执行中" to AccentBlue
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 空状态（场景 3）：引导前往 `app/` 接单，不提供大厅抢单。
 *
 * `app/` 与 `djifly/` 是两个安装包，跨应用深链不在本任务范围（REQ-DJIFLY-001 可选项），
 * 因此这里给出明确的文字指引而不是跳转。
 */
@Composable
private fun EmptyPendingTasks(onGoToApp: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, Divider)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.TaskAlt,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "暂无待执飞任务",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "请前往 app 接单：用户下单并双方确认后，任务会出现在这里。\n" +
                    "本应用不提供任务大厅与抢单。",
                color = TextTertiary,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onGoToApp) {
                Text("前往 app 接单", color = AccentBlue)
            }
        }
    }
}

@Composable
private fun QuickActions(onStarterClick: () -> Unit, onHistoryClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionItem(
            icon = Icons.Default.PlayArrow,
            label = "飞行准备",
            onClick = onStarterClick,
            modifier = Modifier.weight(1f)
        )
        QuickActionItem(
            icon = Icons.Default.History,
            label = "任务记录",
            onClick = onHistoryClick,
            modifier = Modifier.weight(1f)
        )
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
        modifier = modifier.height(72.dp).clickable(onClick = onClick),
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
