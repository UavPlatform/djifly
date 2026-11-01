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
import com.fuwaki.djifly.platform.telemetry.UploadReportStatus

/** 作业壳飞机条：连哪架 + 平台登记短状态 + 上报四态（DS-DJIFLY-002）。机身号全文不截断。 */
data class AircraftBarUi(
    val connected: Boolean,
    val recognizing: Boolean = false,
    val modelName: String? = null,
    /** 机身号全文；未确认时为 null */
    val serialNumber: String? = null,
    val batteryPercent: Int? = null,
    val platformLabel: String,
    val nextStepHint: String? = null,
    val uploadStatus: UploadReportStatus,
)

@Composable
fun AircraftBar(
    ui: AircraftBarUi,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val title = when {
                ui.connected && !ui.serialNumber.isNullOrBlank() ->
                    listOfNotNull(
                        ui.modelName?.takeIf { it.isNotBlank() },
                        ui.serialNumber,
                        ui.batteryPercent?.let { "电量 $it%" },
                    ).joinToString(" · ")
                ui.recognizing -> "正在识别飞机"
                else -> "未连接飞机"
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "平台：${ui.platformLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ui.nextStepHint?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "上报：${ui.uploadStatus.phrase}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
