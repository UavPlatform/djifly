package com.fuwaki.djifly.ui.screen

import com.fuwaki.djifly.data.models.TaskVo
import com.fuwaki.djifly.data.models.WaypointVo
import java.math.BigDecimal
import java.util.Locale

/**
 * 飞行链路任务上下文（1B-8，纯 Kotlin，可 JVM 单测）。
 *
 * 路由参数（starter/flight 的 taskNum）→ 任务上下文状态的解析：
 * - taskNum 空 → [FreeFlight]：自由飞行模式，行为与无任务上下文完全一致；
 * - taskNum 有值但任务详情未加载 → [Loading]（加载中/加载失败均呈现加载态，飞行页其余功能不受影响）；
 * - 详情就绪 → [Ready]：任务卡所需展示字段（任务名/赏金/航点/距离/操作提示）在此完成格式化。
 */
sealed interface FlightTaskContext {

    /** 自由飞行：不经任务进入，无任务卡/状态栏 */
    data object FreeFlight : FlightTaskContext

    /** 任务上下文加载中（或加载失败后的重试等待） */
    data object Loading : FlightTaskContext

    /** 任务上下文就绪：展示层直接可用的格式化字段 */
    data class Ready(
        val taskNum: String,
        val taskName: String,
        val orderStatus: String?,
        val rewardText: String,
        val totalDistanceText: String?,
        val waypointCount: Int,
        val waypointPreview: String,
        val actionHint: String?
    ) : FlightTaskContext

    companion object {

        /** 路由参数 + 任务详情 → 上下文状态 */
        fun resolve(taskNum: String?, detail: TaskVo?, detailLoading: Boolean): FlightTaskContext {
            if (taskNum.isNullOrBlank()) return FreeFlight
            if (detail == null) return Loading
            return Ready(
                taskNum = taskNum,
                taskName = detail.taskName.ifBlank { taskNum },
                orderStatus = detail.orderStatus,
                rewardText = formatReward(detail.totalAmount),
                totalDistanceText = formatDistance(detail.totalDistance),
                waypointCount = detail.waypoints.size,
                waypointPreview = waypointPreview(detail.waypoints),
                actionHint = detail.actionHint
            )
        }

        /** 赏金（订单金额，托管支付语义）→ "¥128.50" */
        fun formatReward(amount: BigDecimal?): String =
            "¥" + "%.2f".format(Locale.US, amount?.toDouble() ?: 0.0)

        /** 距离（米）→ "850 m" / "1.50 km"；null 不展示 */
        fun formatDistance(totalDistanceMeters: BigDecimal?): String? {
            val meters = totalDistanceMeters?.toDouble() ?: return null
            return if (meters >= 1000) {
                "%.2f km".format(Locale.US, meters / 1000)
            } else {
                "%.0f m".format(Locale.US, meters)
            }
        }

        /**
         * 航点预览文案：按 orderIndex 排序后取首末坐标；
         * 无航点 → "无航点参考"；单航点 → 坐标；多航点 → 数量 + 起/终。
         */
        fun waypointPreview(waypoints: List<WaypointVo>): String {
            if (waypoints.isEmpty()) return "无航点参考"
            val sorted = waypoints.sortedBy { it.orderIndex }
            val first = sorted.first()
            return if (sorted.size == 1) {
                "1 个航点 (${coord(first)})"
            } else {
                val last = sorted.last()
                "%d 个航点 · 起 (%s) → 终 (%s)".format(
                    Locale.US,
                    sorted.size,
                    coord(first),
                    coord(last)
                )
            }
        }

        private fun coord(waypoint: WaypointVo): String =
            "%.5f, %.5f".format(Locale.US, waypoint.latitude, waypoint.longitude)
    }
}
