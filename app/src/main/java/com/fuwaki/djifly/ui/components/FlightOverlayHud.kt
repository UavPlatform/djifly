package com.fuwaki.djifly.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuwaki.djifly.ui.screen.FlightTaskContext
import com.fuwaki.djifly.ui.widget.compose.ServerConnectionIndicatorDot
import com.fuwaki.djifly.platform.registration.PlatformRegistrationState

@Composable
fun FlightMinimalTopHud(
    batteryPercent: Int,
    gpsSatellites: Int,
    registrationState: PlatformRegistrationState,
    taskContext: FlightTaskContext.Ready?,
    showEngineerDetails: Boolean,
    productShortLabel: String,
    onBack: () -> Unit,
    onOpenMore: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleEngineerMode: () -> Unit,
    onLinkIndicatorClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var taskExpanded by rememberSaveable { mutableStateOf(false) }
    var titleTapCount by remember { mutableIntStateOf(0) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GhostIconButton(
            icon = Icons.Default.ArrowBack,
            onClick = onBack,
            contentDescription = "返回"
        )

        HudMetricChip(
            label = "电",
            value = "${batteryPercent}%",
            accent = batteryHudColor(batteryPercent)
        )
        HudMetricChip(
            label = "GPS",
            value = "$gpsSatellites",
            accent = gpsHudColor(gpsSatellites)
        )

        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = onLinkIndicatorClick != null,
                    onClick = { onLinkIndicatorClick?.invoke() }
                )
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            ServerConnectionIndicatorDot(registrationState = registrationState)
        }

        if (taskContext != null) {
            TaskSummaryChip(
                taskName = taskContext.taskName,
                subtitle = taskContext.actionHint ?: ("赏金 " + taskContext.rewardText),
                expanded = taskExpanded,
                onClick = { taskExpanded = !taskExpanded },
                modifier = Modifier.weight(1f)
            )
        } else {
            Text(
                text = productShortLabel,
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            titleTapCount++
                            if (titleTapCount >= 3) {
                                titleTapCount = 0
                                onToggleEngineerMode()
                            }
                        }
                    ),
                color = Color.White.copy(alpha = 0.88f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (showEngineerDetails) {
            GhostIconButton(
                icon = Icons.Default.Settings,
                onClick = onOpenSettings,
                contentDescription = "系统设置",
                showBorder = false
            )
        }

        GhostIconButton(
            icon = Icons.Default.MoreHoriz,
            onClick = onOpenMore,
            contentDescription = "更多",
            showBorder = false
        )
    }

    if (taskContext != null) {
        AnimatedVisibility(
            visible = taskExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = taskContext.actionHint ?: ("赏金 " + taskContext.rewardText),
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun FlightEdgeCameraButton(
    onClick: () -> Unit,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    GhostIconButton(
        icon = Icons.Default.PhotoCamera,
        onClick = onClick,
        contentDescription = "相机参数",
        highlighted = highlighted,
        modifier = modifier
    )
}

@Composable
fun FlightEdgeMoreButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GhostIconButton(
        icon = Icons.Default.MoreHoriz,
        onClick = onClick,
        contentDescription = "更多",
        showBorder = false,
        modifier = modifier
    )
}

@Composable
private fun HudMetricChip(
    label: String,
    value: String,
    accent: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TaskSummaryChip(
    taskName: String,
    subtitle: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = if (expanded) 0.5f else 0.38f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "任务·$taskName",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

private fun batteryHudColor(percentage: Int): Color = when {
    percentage > 30 -> Color(0xFF22C55E)
    percentage > 15 -> Color(0xFFF59E0B)
    else -> Color(0xFFEF4444)
}

private fun gpsHudColor(count: Int): Color = when {
    count >= 10 -> Color(0xFF22C55E)
    count >= 6 -> Color(0xFFF59E0B)
    else -> Color(0xFF90A4AE)
}
