package com.fuwaki.djifly.ui.widget.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fuwaki.djifly.platform.registration.PlatformRegistrationState
import com.fuwaki.djifly.platform.ws.WsMessageDirection
import com.fuwaki.djifly.platform.ws.WsMessageLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ServerConnectionUiModel(
    val status: String,
    val detail: String,
    val color: Color,
    val isConnected: Boolean
)

fun PlatformRegistrationState.toServerConnectionUiModel(): ServerConnectionUiModel = when (this) {
    PlatformRegistrationState.WaitingForAircraft -> ServerConnectionUiModel(
        status = "待连接",
        detail = "等待飞机连接并读取序列号后再向服务器注册",
        color = Color(0xFF90A4AE),
        isConnected = false
    )

    is PlatformRegistrationState.Registering -> ServerConnectionUiModel(
        status = "连接中",
        detail = "正在向服务器登记 ${serialNumber}",
        color = Color(0xFF1E88E5),
        isConnected = false
    )

    is PlatformRegistrationState.RetryScheduled -> ServerConnectionUiModel(
        status = "重试中",
        detail = "${retryAfterSeconds} 秒后发起第 ${nextAttempt} 次重试",
        color = Color(0xFFFB8C00),
        isConnected = false
    )

    is PlatformRegistrationState.Registered -> ServerConnectionUiModel(
        status = "已连接",
        detail = buildString {
            append(droneName)
            if (droneId != null) {
                append(" · ID ")
                append(droneId)
            }
            append(" · ")
            append(serialNumber)
        },
        color = Color(0xFF2E7D32),
        isConnected = true
    )

    is PlatformRegistrationState.Failed -> ServerConnectionUiModel(
        status = "连接异常",
        detail = reason,
        color = Color(0xFFC62828),
        isConnected = false
    )
}

@Composable
fun ServerConnectionChip(
    registrationState: PlatformRegistrationState,
    modifier: Modifier = Modifier,
    showDetail: Boolean = true
) {
    val uiModel = registrationState.toServerConnectionUiModel()

    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.42f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier
                .border(1.dp, uiModel.color.copy(alpha = 0.28f), RoundedCornerShape(999.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(uiModel.color)
            )
            Text(
                text = "服务器 ${uiModel.status}",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            if (showDetail) {
                Spacer(modifier = Modifier.size(2.dp))
                Text(
                    text = uiModel.detail,
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ServerConnectionIndicatorDot(
    registrationState: PlatformRegistrationState,
    modifier: Modifier = Modifier
) {
    val uiModel = registrationState.toServerConnectionUiModel()

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(uiModel.color)
                .border(1.5.dp, Color.Black.copy(alpha = 0.3f), CircleShape)
        )
    }
}

@Composable
fun WebSocketMessageHistoryDialog(
    messages: List<WsMessageLog>,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { }
                    ),
                color = Color(0xFF1A1A1A),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.3f))
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "通讯记录",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${messages.size} 条",
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (messages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无消息",
                                color = Color.White.copy(alpha = 0.4f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 380.dp)
                        ) {
                            items(
                                items = messages.asReversed(),
                                key = { it.stableKey() }
                            ) { log ->
                                WebSocketMessageItem(log)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WebSocketMessageHistory(
    messages: List<WsMessageLog>,
    modifier: Modifier = Modifier,
    maxItems: Int = 50,
    header: String = "WebSocket 日志",
    emptyMessage: String = "暂无消息"
) {
    val displayMessages = messages.takeLast(maxItems).asReversed()

    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = header,
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${displayMessages.size} 条",
                    color = Color.White.copy(alpha = 0.46f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (displayMessages.isEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = emptyMessage,
                    color = Color.White.copy(alpha = 0.56f),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(6.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(
                        items = displayMessages,
                        key = { it.stableKey() }
                    ) { log ->
                        WebSocketMessageItem(log)
                    }
                }
            }
        }
    }
}

private fun WsMessageLog.stableKey(): String {
    return "$timestamp-${direction.name}-$type-${name.orEmpty()}-${content.hashCode()}"
}

@Composable
private fun WebSocketMessageItem(log: WsMessageLog) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        val directionColor = when (log.direction) {
            WsMessageDirection.SENT -> Color(0xFF4CAF50)
            WsMessageDirection.RECEIVED -> Color(0xFF2196F3)
        }
        val directionText = when (log.direction) {
            WsMessageDirection.SENT -> "↑"
            WsMessageDirection.RECEIVED -> "↓"
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = directionText,
                color = directionColor,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = log.type,
                color = Color.White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            if (log.name != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "[${log.name}]",
                    color = Color.White.copy(alpha = 0.56f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = log.timestamp.toHistoryTimeLabel(),
                color = Color.White.copy(alpha = 0.4f),
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = log.content.ifBlank { "空消息体" },
            color = Color.White.copy(alpha = 0.66f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun Long.toHistoryTimeLabel(): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(this))
}
