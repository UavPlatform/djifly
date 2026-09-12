package com.fuwaki.djifly.ui.screen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AssignmentReturn
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.ui.graphics.vector.ImageVector
import com.fuwaki.djifly.data.models.ChatEnvelope
import com.fuwaki.djifly.data.models.MessageVO

/**
 * 系统消息卡片元数据（1B-8 收敛包，对齐 app 侧 SystemMessageCard 风格）。
 */
data class SystemMessageMeta(
    val icon: ImageVector,
    val title: String
)

/**
 * t23 载荷事件名 → 图标/标题映射（纯 Kotlin，可 JVM 单测）。
 *
 * 六类事件与 app 侧 SystemMessageCard 一一对应；未知事件（或 payload.name 缺失）
 * 回退通用样式：msgType=2（ORDER）→「订单动态」，否则「系统通知」。
 */
object SystemMessageMetaMapper {

    private val META: Map<String, SystemMessageMeta> = mapOf(
        "ORDER_PAID" to SystemMessageMeta(Icons.Outlined.Payments, "支付成功"),
        "TASK_ACCEPTED" to SystemMessageMeta(Icons.Outlined.AssignmentTurnedIn, "任务已被接单"),
        "TASK_CANCELLED" to SystemMessageMeta(Icons.Outlined.AssignmentReturn, "接单已取消"),
        "TASK_COMPLETED" to SystemMessageMeta(Icons.Outlined.TaskAlt, "任务已完成"),
        "ORDER_WAITING_CONFIRM" to SystemMessageMeta(Icons.Outlined.HourglassTop, "订单待验收"),
        "ORDER_CONFIRMED" to SystemMessageMeta(Icons.Outlined.Verified, "订单已确认完成")
    )

    private val FALLBACK_ORDER = SystemMessageMeta(Icons.Outlined.NotificationsActive, "订单动态")
    private val FALLBACK_NOTICE = SystemMessageMeta(Icons.Outlined.NotificationsActive, "系统通知")

    /** 判定是否系统消息：msgType=1/2 或 fromUserId=0（t23 载荷契约） */
    fun isSystemMessage(message: MessageVO): Boolean =
        message.msgType == 1 || message.msgType == 2 || message.fromUserId == 0L

    /** ChatEnvelope → 是否系统消息（WS 实时与补拉同构） */
    fun isSystemEnvelope(envelope: ChatEnvelope): Boolean {
        val typeValue = when (val t = envelope.msgType) {
            is Number -> t.toInt()
            is String -> when (t.uppercase()) {
                "NOTICE", "ORDER" -> 1
                else -> 0
            }
            else -> 0
        }
        return typeValue == 1 || typeValue == 2 || envelope.fromUserId == 0L
    }

    fun from(payloadName: String?, msgType: Int): SystemMessageMeta =
        META[payloadName] ?: if (msgType == 2) FALLBACK_ORDER else FALLBACK_NOTICE

    fun from(message: MessageVO): SystemMessageMeta = from(message.payloadName, message.msgType)
}
