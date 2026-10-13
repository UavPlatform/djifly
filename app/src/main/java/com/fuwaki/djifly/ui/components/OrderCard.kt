package com.fuwaki.djifly.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fuwaki.djifly.data.models.MatchStatus
import com.fuwaki.djifly.data.models.TaskType
import com.fuwaki.djifly.data.models.TaskVo
import com.fuwaki.djifly.ui.screen.FlightTaskContext
import com.fuwaki.djifly.ui.theme.StatusAmber
import com.fuwaki.djifly.ui.theme.StatusGreen

@Composable
fun OrderCard(
    task: TaskVo,
    onClick: () -> Unit,
    onStartJob: () -> Unit,
    modifier: Modifier = Modifier
) {
    val taskType = TaskType.fromValue(task.taskType)
    val matchStatus = MatchStatus.fromValue(task.matchStatus)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = taskType?.label ?: "未知类型",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = task.taskName.ifBlank { task.taskNum },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                OrderMatchStatusBadge(matchStatus)
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "任务编号：${task.taskNum}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )

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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "地点：${FlightTaskContext.waypointPreview(task.waypoints)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            task.actionHint?.takeIf { it.isNotBlank() }?.let { hint ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = hint,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            ShellPrimaryButton(
                text = "进入起飞检查",
                onClick = onStartJob
            )
        }
    }
}

@Composable
private fun OrderMatchStatusBadge(status: MatchStatus?) {
    val (text, color) = when (status) {
        MatchStatus.CONFIRMED -> "已确认" to StatusGreen
        MatchStatus.PENDING_ACCEPTANCE -> "待验收" to StatusAmber
        else -> "执行中" to MaterialTheme.colorScheme.primary
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f)
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
