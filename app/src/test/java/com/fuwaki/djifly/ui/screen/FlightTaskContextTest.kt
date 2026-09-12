package com.fuwaki.djifly.ui.screen

import com.fuwaki.djifly.data.models.TaskVo
import com.fuwaki.djifly.data.models.WaypointVo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * 飞行链路任务上下文解析纯单元测试（1B-8）。
 * 覆盖验收标准：路由参数解析（taskNum → 状态分支：自由飞行/加载/就绪）与
 * 任务上下文状态格式化（任务名/航点/赏金/距离/actionHint）。
 */
class FlightTaskContextTest {

    private fun taskVo(
        taskName: String = "巡检任务 A",
        totalAmount: BigDecimal? = BigDecimal("128.5"),
        totalDistance: BigDecimal? = BigDecimal("1500"),
        orderStatus: String? = "IN_PROGRESS",
        actionHint: String? = "任务执行中，完成后可标记交付",
        waypoints: List<WaypointVo> = listOf(
            WaypointVo(id = 1, orderIndex = 2, longitude = 121.4740, latitude = 31.2310),
            WaypointVo(id = 2, orderIndex = 0, longitude = 121.4737, latitude = 31.2304),
            WaypointVo(id = 3, orderIndex = 1, longitude = 121.4739, latitude = 31.2308)
        )
    ) = TaskVo(
        taskNum = "T-100",
        taskName = taskName,
        totalAmount = totalAmount,
        totalDistance = totalDistance,
        orderStatus = orderStatus,
        actionHint = actionHint,
        waypoints = waypoints
    )

    @Test
    fun blank_or_null_task_num_resolves_free_flight() {
        // 自由飞行模式：不经任务进入，行为完全不受影响
        assertEquals(FlightTaskContext.FreeFlight, FlightTaskContext.resolve(null, taskVo(), false))
        assertEquals(FlightTaskContext.FreeFlight, FlightTaskContext.resolve("", taskVo(), false))
        assertEquals(FlightTaskContext.FreeFlight, FlightTaskContext.resolve("   ", taskVo(), false))
        assertEquals(FlightTaskContext.FreeFlight, FlightTaskContext.resolve(null, null, false))
    }

    @Test
    fun task_num_without_detail_resolves_loading() {
        assertEquals(
            FlightTaskContext.Loading,
            FlightTaskContext.resolve("T-100", null, detailLoading = true)
        )
        assertEquals(
            FlightTaskContext.Loading,
            FlightTaskContext.resolve("T-100", null, detailLoading = false)
        )
    }

    @Test
    fun ready_context_maps_task_card_fields() {
        val context = FlightTaskContext.resolve("T-100", taskVo(), detailLoading = false)

        assertTrue(context is FlightTaskContext.Ready)
        context as FlightTaskContext.Ready
        assertEquals("T-100", context.taskNum)
        assertEquals("巡检任务 A", context.taskName)
        assertEquals("IN_PROGRESS", context.orderStatus)
        assertEquals("¥128.50", context.rewardText)
        assertEquals("1.50 km", context.totalDistanceText)
        assertEquals("任务执行中，完成后可标记交付", context.actionHint)
    }

    @Test
    fun waypoint_preview_sorts_by_order_index_and_takes_endpoints() {
        val context = FlightTaskContext.resolve("T-100", taskVo(), detailLoading = false)

        context as FlightTaskContext.Ready
        assertEquals(3, context.waypointCount)
        // 输入乱序（2,0,1），按 orderIndex 排序后取首末：0 → (31.2304,121.4737)，2 → (31.2310,121.4740)
        assertEquals(
            "3 个航点 · 起 (31.23040, 121.47370) → 终 (31.23100, 121.47400)",
            context.waypointPreview
        )
    }

    @Test
    fun empty_waypoints_map_to_no_reference_text() {
        val detail = taskVo(waypoints = emptyList(), totalDistance = null)
        val context = FlightTaskContext.resolve("T-100", detail, detailLoading = false)

        context as FlightTaskContext.Ready
        assertEquals("无航点参考", context.waypointPreview)
        assertNull(context.totalDistanceText)
    }

    @Test
    fun single_waypoint_preview_shows_coordinate() {
        val detail = taskVo(waypoints = listOf(WaypointVo(orderIndex = 0, latitude = 31.23, longitude = 121.47)))
        val context = FlightTaskContext.resolve("T-100", detail, detailLoading = false)

        context as FlightTaskContext.Ready
        assertEquals("1 个航点 (31.23000, 121.47000)", context.waypointPreview)
    }

    @Test
    fun blank_task_name_falls_back_to_task_num() {
        val detail = taskVo(taskName = "")
        val context = FlightTaskContext.resolve("T-100", detail, detailLoading = false)

        context as FlightTaskContext.Ready
        assertEquals("T-100", context.taskName)
    }

    @Test
    fun distance_formats_meters_below_one_km() {
        assertEquals("850 m", FlightTaskContext.formatDistance(BigDecimal("850")))
        assertEquals("0 m", FlightTaskContext.formatDistance(BigDecimal("0")))
    }

    @Test
    fun null_reward_formats_as_zero() {
        assertEquals("¥0.00", FlightTaskContext.formatReward(null))
        assertEquals("¥0.10", FlightTaskContext.formatReward(BigDecimal("0.1")))
    }
}
