package com.fuwaki.djifly.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuwaki.djifly.data.models.TaskStatus
import com.fuwaki.djifly.data.models.TaskType
import com.fuwaki.djifly.data.models.TaskVo
import com.fuwaki.djifly.data.models.WaypointVo
import com.fuwaki.djifly.ui.theme.*
import java.util.Locale

// ---------------------------------------------------------------------------
// TaskTypeBadge
// ---------------------------------------------------------------------------

@Composable
fun TaskTypeBadge(type: TaskType, modifier: Modifier = Modifier) {
    val (bgColor, textColor) = when (type) {
        TaskType.AERIAL_PHOTO -> Color(0xFF1E3A5F) to StatusBlue
        TaskType.SURVEY -> Color(0xFF1A3B2A) to StatusGreen
        TaskType.TRANSPORT -> Color(0xFF3B2E10) to StatusAmber
        TaskType.MONITORING -> Color(0xFF2D1B4E) to Color(0xFFA78BFA)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = type.label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ---------------------------------------------------------------------------
// TaskStatusBadge
// ---------------------------------------------------------------------------

@Composable
fun TaskStatusBadge(status: TaskStatus, modifier: Modifier = Modifier) {
    val (bgColor, textColor) = when (status) {
        TaskStatus.IDLE -> Color(0xFF1A2E40) to StatusBlue
        TaskStatus.IN_PROGRESS -> Color(0xFF1A3B2A) to StatusGreen
        TaskStatus.COMPLETED -> Color(0xFF2A2A2A) to TextTertiary
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = status.label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ---------------------------------------------------------------------------
// TaskCard
// ---------------------------------------------------------------------------

@Composable
fun TaskCard(
    task: TaskVo,
    onAccept: (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val taskType = TaskType.fromValue(task.taskType)
    val waypointCount = task.waypoints.size

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Type badge + task name
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (taskType != null) {
                    TaskTypeBadge(type = taskType)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = task.taskName,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // Description
            if (!task.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = task.description,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Info row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "📍 ${waypointCount}个航点",
                    color = TextTertiary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "🕐 ${task.createTime}",
                    color = TextTertiary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Price + optional accept button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = task.totalAmount?.let { "¥${it.toPlainString()}" } ?: "¥--",
                    color = AccentBlue,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                if (onAccept != null) {
                    Button(
                        onClick = onAccept,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text(
                            text = "立即接单",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// WaypointItem
// ---------------------------------------------------------------------------

@Composable
fun WaypointItem(waypoint: WaypointVo, index: Int, modifier: Modifier = Modifier) {
    val circledIndex = circledNumber(index + 1)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCardLight)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = circledIndex,
            color = AccentBlue,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = String.format(Locale.US, "%.4f°%s", kotlin.math.abs(waypoint.latitude), if (waypoint.latitude >= 0) "N" else "S"),
            color = TextPrimary,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = String.format(Locale.US, "%.4f°%s", kotlin.math.abs(waypoint.longitude), if (waypoint.longitude >= 0) "E" else "W"),
            color = TextPrimary,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "高度: ${String.format(Locale.US, "%.0f", waypoint.altitude)}m",
            color = TextSecondary,
            fontSize = 13.sp
        )
    }
}

private fun circledNumber(n: Int): String {
    val circled = listOf("①","②","③","④","⑤","⑥","⑦","⑧","⑨","⑩",
        "⑪","⑫","⑬","⑭","⑮","⑯","⑰","⑱","⑲","⑳")
    return if (n in 1..circled.size) circled[n - 1] else "$n"
}

// ---------------------------------------------------------------------------
// ConfirmDialog
// ---------------------------------------------------------------------------

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text(text = title, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(text = message, fontSize = 14.sp)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                modifier = Modifier.height(40.dp)
            ) {
                Text(text = confirmText, color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                border = ButtonDefaults.outlinedButtonBorder,
                modifier = Modifier.height(40.dp)
            ) {
                Text(text = "取消", color = TextSecondary)
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
