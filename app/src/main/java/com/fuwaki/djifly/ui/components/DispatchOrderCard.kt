package com.fuwaki.djifly.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fuwaki.djifly.platform.dispatch.DispatchStages
import com.fuwaki.djifly.platform.telemetry.DispatchFlightState

/** 当前派单订单：阶段、连机状态（机型 · 机身号，自动记录）、待补传遥测条数。 */
@Composable
fun DispatchOrderCard(state: DispatchFlightState, modifier: Modifier = Modifier) {
    val order = state.currentOrder ?: return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "当前派单 · ${DispatchStages.label(order.stage)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text("订单 ${order.orderId.take(8)}", style = MaterialTheme.typography.bodySmall)
            val c = state.connection
            Text(
                text = if (c != null) "已连机：${c.modelName ?: c.modelCode ?: "未知机型"} · ${c.serialNumber ?: "未知"}（自动记录）"
                else "连上飞机后自动记录机型与机身号",
                style = MaterialTheme.typography.bodyMedium
            )
            if (c?.overweight == true) {
                Text("货物重量超过该机型登记载重，请注意飞行安全", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            if (state.pendingSamples > 0) {
                Text("待补传遥测 ${state.pendingSamples} 条（联网后自动上传）", style = MaterialTheme.typography.bodySmall)
            }
            state.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
