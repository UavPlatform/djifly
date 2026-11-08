package com.fuwaki.djifly.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fuwaki.djifly.platform.dispatch.DispatchStages
import com.fuwaki.djifly.platform.telemetry.DispatchFlightState

/** 当前派单一单 + 一个主按钮（DS-DJIFLY-002）。不再提供旧「详情」入口。 */
@Composable
fun DispatchOrderCard(
    state: DispatchFlightState,
    modifier: Modifier = Modifier,
    uploadPhrase: String? = null,
    primaryEnabled: Boolean = true,
    primaryDisabledReason: String? = null,
    onStart: (() -> Unit)? = null,
) {
    val order = state.currentOrder ?: return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "当前订单 · ${DispatchStages.label(order.stage)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "订单 ${order.orderId.orEmpty()}",
                style = MaterialTheme.typography.bodySmall
            )
            uploadPhrase?.let {
                Spacer(Modifier.height(4.dp))
                Text(text = "上报：$it", style = MaterialTheme.typography.bodyMedium)
            }
            state.cacheWarning?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            state.lastError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (onStart != null) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onStart,
                    enabled = primaryEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (primaryEnabled) "进入飞行" else "进入飞行（不可用）")
                }
                if (!primaryEnabled && !primaryDisabledReason.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = primaryDisabledReason,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
