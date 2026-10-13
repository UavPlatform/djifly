package com.fuwaki.djifly.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuwaki.djifly.platform.attachment.DeliverableItemPhase
import com.fuwaki.djifly.platform.attachment.DeliverableItemUi

@Composable
fun UploadProgressBanner(
    message: String,
    modifier: Modifier = Modifier,
) {
    if (message.isBlank()) return
    Text(
        text = message,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = Color.White.copy(alpha = 0.9f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

fun platformUploadBannerMessage(
    deliverables: List<DeliverableItemUi>,
    capturingEvidence: Boolean,
): String {
    val uploading = deliverables.any {
        it.phase == DeliverableItemPhase.UPLOADING || it.phase == DeliverableItemPhase.PENDING
    }
    val failed = deliverables.any { it.phase == DeliverableItemPhase.FAILED }
    return when {
        capturingEvidence || uploading -> "正在上传送达凭证…"
        failed -> "上传失败，请重新拍摄"
        else -> ""
    }
}

@Composable
fun PlatformOpsBar(
    deliverables: List<DeliverableItemUi>,
    capturingEvidence: Boolean,
    canConfirmDelivery: Boolean,
    onCaptureEvidence: () -> Unit,
    onConfirmDelivery: () -> Unit,
    onStartLive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uploading = deliverables.any {
        it.phase == DeliverableItemPhase.UPLOADING || it.phase == DeliverableItemPhase.PENDING
    }
    val banner = platformUploadBannerMessage(deliverables, capturingEvidence)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        UploadProgressBanner(message = banner)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OpsIconWithCaption(
                icon = Icons.Default.Videocam,
                caption = "开播",
                onClick = onStartLive,
                enabled = true
            )
            OpsIconWithCaption(
                icon = Icons.Default.PhotoCamera,
                caption = "拍摄",
                onClick = onCaptureEvidence,
                enabled = !capturingEvidence && !uploading,
                loading = capturingEvidence || uploading
            )
            OpsIconWithCaption(
                icon = Icons.Default.CheckCircle,
                caption = "送达",
                onClick = onConfirmDelivery,
                enabled = canConfirmDelivery && !uploading && !capturingEvidence,
                highlighted = canConfirmDelivery && !uploading && !capturingEvidence
            )
        }
    }
}

@Composable
private fun OpsIconWithCaption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    caption: String,
    onClick: () -> Unit,
    enabled: Boolean,
    loading: Boolean = false,
    highlighted: Boolean = false,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        GhostIconButton(
            icon = icon,
            onClick = onClick,
            contentDescription = caption,
            enabled = enabled,
            loading = loading,
            highlighted = highlighted
        )
        Text(
            text = caption,
            color = Color.White.copy(alpha = if (enabled) 0.75f else 0.38f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
