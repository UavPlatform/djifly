package com.fuwaki.djifly.platform.dispatch

import com.fuwaki.djifly.platform.network.generated.ApiDispatchOrderVO

/**
 * 派单阶段与运单字段的读取规则（ADR-0007 / ADR-0009）。订单模型使用契约生成的 [ApiDispatchOrderVO]，这里不另写模型。
 * 飞手在 djifly 能执飞的阶段只有「飞手到场」和「运输中」。
 */
object DispatchStages {
    const val PILOT_ARRIVED = "PILOT_ARRIVED"
    const val IN_TRANSIT = "IN_TRANSIT"
    private val FLYABLE = setOf(PILOT_ARRIVED, IN_TRANSIT)

    fun isFlyable(order: ApiDispatchOrderVO): Boolean = order.orderId != null && order.stage in FLYABLE

    /** 当前订单：运输中优先，其次飞手到场；其他阶段不展示为可执飞。 */
    fun current(orders: List<ApiDispatchOrderVO>): ApiDispatchOrderVO? =
        orders.filter { it.orderId != null }.let { list ->
            list.firstOrNull { it.stage == IN_TRANSIT } ?: list.firstOrNull { it.stage == PILOT_ARRIVED }
        }

    /**
     * 载重来源（ADR-0009）：DJI MSDK 没有实时挂载重量传感器 Key，所以采样里的 payloadKg 取当前订单运单的货物重量，
     * 即需求方填写的 `weightMax`（缺失时用 `weightMin`），与后端超重判断一致。两者都没有则为 null，不填 0。
     */
    fun cargoWeightKg(order: ApiDispatchOrderVO?): Double? {
        val fields = order?.fields ?: return null
        return (weight(fields["weightMax"]) ?: weight(fields["weightMin"]))?.takeIf { it.isFinite() && it >= 0 }
    }

    private fun weight(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.trim().toDoubleOrNull()
        else -> null
    }

    fun label(stage: String?): String = when (stage) {
        "AWAITING_DEPOSIT" -> "待付定金"
        "WAITING_ACCEPTANCE" -> "等待接单"
        "PILOT_PREPARING" -> "飞手准备中"
        PILOT_ARRIVED -> "飞手到场"
        IN_TRANSIT -> "运输中"
        "DELIVERY_CONFIRMATION" -> "送达确认中"
        "COMPLETED" -> "已结单"
        "CANCELLED" -> "已取消"
        else -> stage ?: "未知"
    }
}
