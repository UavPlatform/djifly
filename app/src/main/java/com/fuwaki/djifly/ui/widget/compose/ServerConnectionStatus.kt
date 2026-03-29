package com.fuwaki.djifly.ui.widget.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fuwaki.djifly.platform.registration.PlatformRegistrationState

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
