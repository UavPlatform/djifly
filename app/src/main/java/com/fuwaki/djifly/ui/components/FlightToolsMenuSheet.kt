package com.fuwaki.djifly.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val SheetBackground = Color(0xD90A0F14)

@Composable
fun FlightToolsMenuSheet(
    visible: Boolean,
    engineerMode: Boolean,
    onDismiss: () -> Unit,
    onSelectCamera: () -> Unit,
    onSelectLink: () -> Unit,
    onSelectInfo: () -> Unit,
    onOpenDjiSettings: () -> Unit,
    onToggleEngineerMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.38f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 14.dp)
                .background(SheetBackground, RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "更多",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                GhostIconButton(
                    icon = Icons.Default.Close,
                    onClick = onDismiss,
                    contentDescription = "关闭",
                    showBorder = false
                )
            }

            MenuRow(Icons.Default.PhotoCamera, "相机参数", onSelectCamera)
            MenuRow(Icons.Default.Link, "链路与通讯", onSelectLink)
            MenuRow(Icons.Default.Info, "机体与系统", onSelectInfo)
            MenuRow(Icons.Default.Settings, "DJI 系统设置", onOpenDjiSettings)
            MenuRow(
                icon = Icons.Default.Tune,
                title = if (engineerMode) "关闭排障模式" else "开启排障模式",
                onClick = onToggleEngineerMode
            )
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GhostIconButton(
            icon = icon,
            onClick = onClick,
            contentDescription = title,
            showBorder = false
        )
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.92f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}
