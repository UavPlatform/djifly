package com.fuwaki.djifly.ui.widget.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val Green = Color(0xFF00D15A)
private val Orange = Color(0xFFFF9900)
private val GlassBg = Color(0x99000000)
private val GlassBorder = Color(0x33FFFFFF)

@Composable
fun TakeOffButton(modifier: Modifier = Modifier, onConfirm: () -> Unit = {}) {
    ConfirmableButton(modifier, Icons.Default.KeyboardArrowUp, Green, onConfirm)
}

@Composable
fun ReturnHomeButton(modifier: Modifier = Modifier, onConfirm: () -> Unit = {}) {
    ConfirmableButton(modifier, Icons.Default.Home, Orange, onConfirm)
}

@Composable
private fun ConfirmableButton(
    modifier: Modifier,
    icon: ImageVector,
    color: Color,
    onConfirm: () -> Unit
) {
    var showSlider by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(GlassBg, CircleShape)
                .border(0.5.dp, GlassBorder, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { showSlider = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
        }

        if (showSlider) {
            // ✅ 关键：全屏遮罩 + 安全区域内边距
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.5f))
                    .pointerInput(Unit) {}
                    // ★ 避开所有挖孔/刘海/水滴区域
                    .windowInsetsPadding(
                        WindowInsets.displayCutout.union(WindowInsets.systemBars)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .background(Color(0xFF1E1E1E), RoundedCornerShape(32.dp))
                        .border(0.5.dp, GlassBorder, RoundedCornerShape(32.dp))
                        .padding(8.dp)
                ) {
                    // 关闭
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(0.1f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showSlider = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close, null,
                            tint = Color.White.copy(0.6f),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // 滑动条
                    SlideBar(color) {
                        showSlider = false
                        onConfirm()
                    }
                }
            }
        }
    }
}

@Composable
private fun SlideBar(color: Color, onConfirm: () -> Unit) {
    val density = LocalDensity.current
    val barW = 200.dp
    val handle = 46.dp
    val handlePx = with(density) { handle.toPx() }
    val maxPx = with(density) { (barW - handle).toPx() }

    var raw by remember { mutableStateOf(0f) }
    val anim by animateFloatAsState(
        raw, tween(if (raw == 0f) 250 else 0), label = ""
    )

    Box(
        modifier = Modifier
            .width(barW)
            .height(handle)
            .background(Color.Black, CircleShape)
            .border(0.5.dp, GlassBorder, CircleShape),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .width(with(density) { (anim + handlePx).toDp() })
                .fillMaxHeight()
                .background(color.copy(0.25f), CircleShape)
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(anim.roundToInt(), 0) }
                .size(handle)
                .padding(3.dp)
                .background(color, CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            if (raw >= maxPx * 0.85f) onConfirm()
                            raw = 0f
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            raw = (raw + drag.x).coerceIn(0f, maxPx)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.KeyboardArrowRight, null,
                tint = Color.White, modifier = Modifier.size(24.dp)
            )
        }
    }
}