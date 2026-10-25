package com.fuwaki.djifly.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 执飞状态门禁纯单元测试（TASK-DJIFLY-002 / ADR-0003 决定 4 双确认门禁）。
 *
 * 覆盖：
 * 1. 待执飞列表口径（首页只展示 CONFIRMED / IN_PROGRESS / PENDING_ACCEPTANCE）；
 * 2. 进入 FlightScreen 的放行与拒绝矩阵（含明确提示文案）；
 * 3. 派单唯一路径：PILOT_ARRIVED / IN_TRANSIT 放行，其它派单阶段拒绝；
 * 4. MatchStatus 解析（未知/null 值不崩溃，按未确认处理）。
 */
class FlightEntryGateTest {

    // ── 待执飞列表口径 ─────────────────────────────────────

    @Test
    fun confirmed_task_is_pending_flight() {
        assertTrue(FlightEntryGate.isPendingFlight("CONFIRMED", "IN_PROGRESS"))
        // 用户确认后、后端尚未推进 IN_PROGRESS 的瞬间
        assertTrue(FlightEntryGate.isPendingFlight("CONFIRMED", "IDLE"))
    }

    @Test
    fun in_progress_task_is_pending_flight_even_without_match_status() {
        assertTrue(FlightEntryGate.isPendingFlight(null, "IN_PROGRESS"))
        assertTrue(FlightEntryGate.isPendingFlight("IN_PROGRESS", "IN_PROGRESS"))
    }

    @Test
    fun delivered_task_stays_visible_until_closed() {
        assertTrue(FlightEntryGate.isPendingFlight("PENDING_ACCEPTANCE", "COMPLETED"))
        assertFalse(FlightEntryGate.isPendingFlight("CLOSED", "COMPLETED"))
    }

    @Test
    fun pre_confirmation_stages_are_not_pending_flight() {
        listOf(
            "SEEKING_RIDER", "NEGOTIATING", "AWAITING_PAYMENT", "AWAITING_RIDER_CONFIRM"
        ).forEach { stage ->
            assertFalse("撮合阶段 $stage 不应出现在待执飞列表", FlightEntryGate.isPendingFlight(stage, "IDLE"))
        }
    }

    // ── 进入飞行控制台矩阵 ────────────────────────────────

    @Test
    fun confirmed_or_in_progress_may_enter_flight() {
        assertTrue(FlightEntryGate.canEnterFlight("CONFIRMED", "IDLE"))
        assertTrue(FlightEntryGate.canEnterFlight("CONFIRMED", "IN_PROGRESS"))
        assertTrue(FlightEntryGate.canEnterFlight(null, "IN_PROGRESS"))
        assertNull(FlightEntryGate.denyReason("CONFIRMED", "IN_PROGRESS"))
    }

    @Test
    fun unconfirmed_task_is_rejected_with_actionable_reason() {
        listOf(
            "SEEKING_RIDER", "NEGOTIATING", "AWAITING_PAYMENT", "AWAITING_RIDER_CONFIRM"
        ).forEach { stage ->
            assertFalse("撮合阶段 $stage 不得进入飞行控制台", FlightEntryGate.canEnterFlight(stage, "IDLE"))
            val reason = FlightEntryGate.denyReason(stage, "IDLE")
            assertNotNull("拒绝时必须给出提示文案", reason)
            assertTrue("提示需说明双方尚未确认", reason!!.contains("尚未确认"))
        }
    }

    @Test
    fun awaiting_rider_confirm_names_current_stage() {
        val reason = FlightEntryGate.denyReason("AWAITING_RIDER_CONFIRM", "IDLE")
        assertNotNull(reason)
        assertTrue(reason!!.contains("待飞手确认"))
    }

    @Test
    fun delivered_or_closed_task_cannot_fly_again() {
        assertFalse(FlightEntryGate.canEnterFlight("PENDING_ACCEPTANCE", "COMPLETED"))
        assertTrue(
            FlightEntryGate.denyReason("PENDING_ACCEPTANCE", "COMPLETED")!!.contains("等待用户验收")
        )
        assertFalse(FlightEntryGate.canEnterFlight("CLOSED", "COMPLETED"))
        assertTrue(FlightEntryGate.denyReason("CLOSED", "COMPLETED")!!.contains("已结案"))
        assertFalse(FlightEntryGate.canEnterFlight(null, "COMPLETED"))
    }

    @Test
    fun missing_match_status_on_idle_task_is_rejected() {
        assertFalse(FlightEntryGate.canEnterFlight(null, "IDLE"))
        assertNotNull(FlightEntryGate.denyReason(null, "IDLE"))
    }

    // ── TaskVo 入口 ───────────────────────────────────────

    @Test
    fun task_vo_overloads_follow_same_matrix() {
        val confirmed = TaskVo(taskNum = "T-1", taskStatus = "IDLE", matchStatus = "CONFIRMED")
        val negotiating = TaskVo(taskNum = "T-2", taskStatus = "IDLE", matchStatus = "NEGOTIATING")

        assertTrue(FlightEntryGate.isPendingFlight(confirmed))
        assertTrue(FlightEntryGate.canEnterFlight(confirmed))
        assertFalse(FlightEntryGate.isPendingFlight(negotiating))
        assertFalse(FlightEntryGate.canEnterFlight(negotiating))
        assertNotNull(FlightEntryGate.denyReason(negotiating))
    }


    // ── 派单阶段（dispatchAsTask 把 stage 写入 taskStatus，matchStatus 为空）──

    @Test
    fun dispatch_flyable_stages_may_enter_flight() {
        listOf("PILOT_ARRIVED", "IN_TRANSIT").forEach { stage ->
            assertTrue("派单阶段 $stage 应可进入飞行", FlightEntryGate.canEnterFlight(null, stage))
            assertNull(FlightEntryGate.denyReason(null, stage))
            assertTrue(FlightEntryGate.isPendingFlight(null, stage))
            val task = TaskVo(taskNum = "ord-1", taskStatus = stage, matchStatus = null)
            assertTrue(FlightEntryGate.canEnterFlight(task))
            assertNull(FlightEntryGate.denyReason(task))
        }
    }

    @Test
    fun dispatch_non_flyable_stages_are_denied() {
        listOf(
            "AWAITING_DEPOSIT",
            "WAITING_ACCEPTANCE",
            "PILOT_PREPARING",
            "DELIVERY_CONFIRMATION",
            "COMPLETED",
            "CANCELLED",
        ).forEach { stage ->
            assertFalse("派单阶段 $stage 不得进入飞行", FlightEntryGate.canEnterFlight(null, stage))
            val reason = FlightEntryGate.denyReason(null, stage)
            assertNotNull("拒绝时必须给出提示文案", reason)
            // COMPLETED 走旧「任务已完成」分支；其余走派单阶段文案
            if (stage == "COMPLETED") {
                assertTrue(reason!!.contains("已完成") || reason.contains("不可执飞"))
            } else {
                assertTrue(
                    "提示需点明不可执飞阶段，实际=$reason",
                    reason!!.contains("不可执飞") || reason.contains("已完成") || reason.contains("已结案"),
                )
            }
            assertFalse(FlightEntryGate.isPendingFlight(null, stage))
        }
    }

    // ── MatchStatus 解析 ──────────────────────────────────

    @Test
    fun match_status_parses_known_values_and_tolerates_unknown() {
        assertEquals(MatchStatus.CONFIRMED, MatchStatus.fromValue("CONFIRMED"))
        assertEquals(MatchStatus.AWAITING_RIDER_CONFIRM, MatchStatus.fromValue("AWAITING_RIDER_CONFIRM"))
        assertNull(MatchStatus.fromValue("NOT_A_STATUS"))
        assertNull(MatchStatus.fromValue(null))
    }
}
