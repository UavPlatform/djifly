package com.fuwaki.djifly.platform.dispatch

/**
 * 派单执行所需的最小契约子集（ADR-0007 / ADR-0009）。字段名与 backend OpenAPI 一致；
 * 未用到的字段不声明，Gson 会忽略。
 */
data class DispatchOrder(
    val orderId: String,
    val stage: String? = null,
    val assignmentVersion: Long = 0,
    val fields: Map<String, String>? = null,
    val executedDrone: DispatchExecutedDrone? = null
)

data class DispatchExecutedDrone(
    val serialNumber: String? = null,
    val modelCode: String? = null,
    val modelName: String? = null
)

data class FlightConnectRequest(
    val assignmentVersion: Long,
    val requestKey: String,
    val serialNumber: String,
    val modelCode: String,
    val modelName: String
)

data class FlightConnection(
    val connectionId: String,
    val orderId: String? = null,
    val serialNumber: String? = null,
    val modelCode: String? = null,
    val modelName: String? = null,
    val maxPayloadKg: Double? = null,
    val overweight: Boolean = false
)

/** 一条 SDK 采样；拿不到的值为 null，绝不填 0。 */
data class FlightSample(
    val sequence: Long,
    val capturedAt: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val speed: Double? = null,
    val battery: Int? = null,
    val flying: Boolean? = null,
    val payloadKg: Double? = null
)

data class FlightSampleBatchRequest(val samples: List<FlightSample>)

data class FlightSampleBatchResult(val accepted: Int = 0, val duplicates: Int = 0, val rejected: Int = 0)

/** 派单阶段：飞手在 djifly 能执飞的只有「飞手到场」「运输中」。 */
object DispatchStages {
    const val PILOT_ARRIVED = "PILOT_ARRIVED"
    const val IN_TRANSIT = "IN_TRANSIT"
    private val FLYABLE = setOf(PILOT_ARRIVED, IN_TRANSIT)

    fun isFlyable(order: DispatchOrder): Boolean = order.stage in FLYABLE

    /** 当前订单：运输中优先，其次飞手到场；其他阶段不展示为可执飞。 */
    fun current(orders: List<DispatchOrder>): DispatchOrder? =
        orders.firstOrNull { it.stage == IN_TRANSIT } ?: orders.firstOrNull { it.stage == PILOT_ARRIVED }

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
