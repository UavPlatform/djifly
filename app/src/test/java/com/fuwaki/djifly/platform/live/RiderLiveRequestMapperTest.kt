package com.fuwaki.djifly.platform.live

import com.fuwaki.djifly.data.error.AppError
import com.fuwaki.djifly.data.models.LiveStartVo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 飞手主动开播结果映射纯单元测试（1B-4b，t24 错误语义契约）。
 * 覆盖验收标准②③：LiveStartVO 200 分支文案、四类错误（403/404/409/429）用户提示。
 */
class RiderLiveRequestMapperTest {

    // ── 200 分支（LiveStartVO） ──

    @Test
    fun live_started_branch_maps_to_confirmed_message() {
        val vo = LiveStartVo(requestId = "r1", roomId = "drone_SN-1", ackConfirmed = true, liveState = "RUNNING", code = "LIVE_STARTED")

        assertEquals("设备已确认启动图传，直播已开始", RiderLiveRequestMapper.successMessage(vo))
        assertFalse(RiderLiveRequestMapper.isAlreadyRunning(vo))
    }

    @Test
    fun pending_branch_maps_to_waiting_message() {
        val vo = LiveStartVo(requestId = "r1", roomId = "drone_SN-1", ackConfirmed = false, liveState = "STARTING", code = "LIVE_START_PENDING")

        assertEquals("开播命令已发送，等待设备确认", RiderLiveRequestMapper.successMessage(vo))
        assertFalse(RiderLiveRequestMapper.isAlreadyRunning(vo))
    }

    @Test
    fun already_running_branch_maps_to_idempotent_message() {
        val vo = LiveStartVo(roomId = "drone_SN-1", ackConfirmed = true, liveState = "RUNNING", code = "LIVE_ALREADY_RUNNING")

        assertEquals("图传已在运行中", RiderLiveRequestMapper.successMessage(vo))
        assertTrue(RiderLiveRequestMapper.isAlreadyRunning(vo))
    }

    // ── 错误分支（t24 错误语义契约） ──

    @Test
    fun forbidden_no_permission_maps_to_binding_hint() {
        // 403：设备未绑定到当前飞手
        val error = AppError.Business("NO_PERMISSION", "设备未绑定到当前飞手")

        assertEquals("设备未绑定到当前飞手，无法开播", RiderLiveRequestMapper.userMessageFor(error))
    }

    @Test
    fun not_found_uav_maps_to_register_hint() {
        // 404：设备未注册（UAV_NOT_FOUND 经 errorCode 透传）
        val error = AppError.Business("UAV_NOT_FOUND", "无人机不存在")

        assertEquals("设备未注册，请先绑定并注册设备", RiderLiveRequestMapper.userMessageFor(error))
    }

    @Test
    fun http_404_without_error_code_also_maps_to_register_hint() {
        val error = AppError.Http(404, "请求失败 (404)")

        assertEquals("设备未注册，请先绑定并注册设备", RiderLiveRequestMapper.userMessageFor(error))
    }

    @Test
    fun conflict_uav_not_connected_maps_to_offline_hint() {
        // 409：设备离线
        val error = AppError.Business("UAV_NOT_CONNECTED", "无人机未建立 WebSocket 连接")

        assertEquals("设备离线（未连接平台），无法开播", RiderLiveRequestMapper.userMessageFor(error))
    }

    @Test
    fun http_409_without_error_code_also_maps_to_offline_hint() {
        val error = AppError.Http(409, "请求失败 (409)")

        assertEquals("设备离线（未连接平台），无法开播", RiderLiveRequestMapper.userMessageFor(error))
    }

    @Test
    fun already_starting_maps_to_wait_hint() {
        val error = AppError.Business("LIVE_ALREADY_STARTING", "图传正在启动中")

        assertEquals("图传正在启动中，请稍候", RiderLiveRequestMapper.userMessageFor(error))
    }

    @Test
    fun rate_limited_maps_to_retry_hint() {
        val error = AppError.RateLimited("操作过于频繁")

        assertEquals("操作过于频繁，请稍后重试", RiderLiveRequestMapper.userMessageFor(error))
    }

    @Test
    fun live_prefixed_business_error_passes_message_through() {
        // 设备拒绝开播等 LIVE_* 业务错误：透传后端 message
        val error = AppError.Business("LIVE_REQUEST_SEND_FAILED", "开播命令下发失败")

        assertEquals("开播失败：开播命令下发失败", RiderLiveRequestMapper.userMessageFor(error))
    }

    @Test
    fun unknown_error_passes_message_through_and_null_gives_fallback() {
        assertEquals("网络异常", RiderLiveRequestMapper.userMessageFor(AppError.Network("网络异常")))
        assertEquals("开播失败", RiderLiveRequestMapper.userMessageFor(null))
    }
}
