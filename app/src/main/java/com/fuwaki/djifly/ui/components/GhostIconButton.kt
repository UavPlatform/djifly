package com.fuwaki.djifly.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val GhostBorder = Color.White.copy(alpha = 0.22f)
private val GhostFill = Color.White.copy(alpha = 0.06f)

@Composable
fun GhostIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    enabled: Boolean = true,
    showBorder: Boolean = true,
    tint: Color = Color.White,
    loading: Boolean = false,
    highlighted: Boolean = false,
) {
    val alpha = when {
        !enabled -> 0.38f
        highlighted -> 1f
        else -> 0.92f
    }
    Surface(
        modifier = modifier
            .alpha(alpha)
            .semantics {
                role = Role.Button
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled && !loading,
                onClick = onClick
            ),
        shape = CircleShape,
        color = if (highlighted) Color.White.copy(alpha = 0.14f) else GhostFill,
        border = if (showBorder) BorderStroke(1.dp, GhostBorder) else null
    ) {
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = tint
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun GhostLabeledIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    highlighted: Boolean = false,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        GhostIconButton(
            icon = icon,
            onClick = onClick,
            contentDescription = label,
            enabled = enabled,
            loading = loading,
            highlighted = highlighted
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = if (enabled) 0.78f else 0.4f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .alpha(0f)
        )
    }
}
