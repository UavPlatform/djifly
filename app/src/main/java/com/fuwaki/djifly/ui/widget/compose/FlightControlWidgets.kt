package com.fuwaki.djifly.ui.widget.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * 专业起飞按钮 (带滑动二次确认，模仿 DJI Fly)
 */
@Composable
fun TakeOffButton(
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit = {}
) {
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        FlightControlButton(
            icon = Icons.Default.KeyboardArrowUp,
            label = "TAKE OFF",
            contentColor = Color(0xFF4CAF50),
            onClick = { showDialog = true }
        )

        if (showDialog) {
            TakeOffConfirmationDialog(
                onConfirm = {
                    showDialog = false
                    onConfirm()
                },
                onDismiss = { showDialog = false }
            )
        }
    }
}

/**
 * 专业返航按钮
 */
@Composable
fun ReturnHomeButton(
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit = {}
) {
    // 逻辑类似，可根据需要也添加滑动确认
    FlightControlButton(
        modifier = modifier,
        icon = Icons.Default.Home,
        label = "RTH",
        contentColor = Color(0xFFFF9800),
        onClick = onConfirm
    )
}

@Composable
private fun TakeOffConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // 居中显示的滑动确认遮罩
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .pointerInput(Unit) { /* 拦截点击 */ },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(300.dp)
                .background(Color(0xFF1A1A1A), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Text(
                "准备起飞",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "请确保周围环境安全，长按并向右滑动以起飞",
                color = Color.Gray,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            // 滑动确认条
            SliderConfirmationBar(onConfirm = onConfirm)

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "取消",
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.clickable { onDismiss() },
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun SliderConfirmationBar(onConfirm: () -> Unit) {
    val density = LocalDensity.current
    val barWidth = 240.dp
    val handleSize = 48.dp
    val maxOffsetPx = with(density) { (barWidth - handleSize).toPx() }
    
    var offsetX by remember { mutableStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(targetValue = offsetX)

    Box(
        modifier = Modifier
            .width(barWidth)
            .height(handleSize)
            .background(Color.Black.copy(0.3f), CircleShape)
            .border(1.dp, Color.White.copy(0.2f), CircleShape),
        contentAlignment = Alignment.CenterStart
    ) {
        // 背景文字
        Text(
            "向右滑动起飞",
            modifier = Modifier.fillMaxWidth().alpha(1f - (offsetX / maxOffsetPx)),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = Color.White.copy(0.4f),
            fontSize = 14.sp
        )

        // 滑块
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .size(handleSize)
                .padding(2.dp)
                .background(Color(0xFF4CAF50), CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            if (offsetX >= maxOffsetPx * 0.9f) {
                                onConfirm()
                            }
                            offsetX = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount.x).coerceIn(0f, maxOffsetPx)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.KeyboardArrowUp, null, tint = Color.White)
        }
    }
}

@Composable
private fun FlightControlButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    contentColor: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier.width(56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                .border(1.5.dp, contentColor.copy(alpha = 0.8f), CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier
                .background(Color.Black.copy(0.3f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

// 辅助扩展
@Composable
private fun Modifier.clickable(onClick: () -> Unit) = this.pointerInput(Unit) {
    detectDragGestures(onDrag = { _, _ -> }, onDragEnd = { onClick() })
}
