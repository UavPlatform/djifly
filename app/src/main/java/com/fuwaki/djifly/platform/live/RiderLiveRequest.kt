package com.fuwaki.djifly.platform.live

import com.fuwaki.djifly.data.error.AppError
import com.fuwaki.djifly.data.models.LiveStartVo

/**
 * 飞手主动开播请求状态（纯 Kotlin，可 JVM 单测）。
 */
sealed interface RiderLiveRequestState {
    data object Idle : RiderLiveRequestState
    data object Loading : RiderLiveRequestState

    /** 200 分支（LiveStartVO 四分支中的三个成功/等待态） */
    data class Success(
        val userMessage: String,
        val liveState: String?,
        val alreadyRunning: Boolean
    ) : RiderLiveRequestState

    /** 失败（HTTP 403/404/409/429 等，已映射为用户可读文案） */
    data class Error(val userMessage: String) : RiderLiveRequestState
}

/**
 * 飞手主动开播的结果映射（纯函数，t24 错误语义契约）。
 *
 * 200 分支（LiveStartVO.code）：LIVE_STARTED / LIVE_START_PENDING / LIVE_ALREADY_RUNNING。
 * 错误分支：403 NO_PERMISSION（未绑定）/ 404 UAV_NOT_FOUND（未注册）/
 * 409 UAV_NOT_CONNECTED、LIVE_ALREADY_STARTING（离线、启动中）/ 429（限流）。
 */
object RiderLiveRequestMapper {

    /** LiveStartVO 200 分支 → 用户提示文案 */
    fun successMessage(vo: LiveStartVo): String = when (vo.code) {
        "LIVE_ALREADY_RUNNING" -> "图传已在运行中"
        "LIVE_START_PENDING" -> "开播命令已发送，等待设备确认"
        else -> "设备已确认启动图传，直播已开始"
    }

    /** 是否已在运行（幂等成功，不需要重复进入开播等待） */
    fun isAlreadyRunning(vo: LiveStartVo): Boolean = vo.code == "LIVE_ALREADY_RUNNING"

    /** 错误 → 用户提示文案（t24 错误语义契约） */
    fun userMessageFor(error: AppError?): String {
        if (error == null) return "开播失败"
        val errorCode = (error as? AppError.Business)?.errorCode
        return when {
            error is AppError.RateLimited -> "操作过于频繁，请稍后重试"
            errorCode == "NO_PERMISSION" -> "设备未绑定到当前飞手，无法开播"
            errorCode == "UAV_NOT_FOUND" || (error is AppError.Http && error.code == 404) ->
                "设备未注册，请先绑定并注册设备"
            errorCode == "UAV_NOT_CONNECTED" ||
                (error is AppError.Http && error.code == 409) -> "设备离线（未连接平台），无法开播"
            errorCode == "LIVE_ALREADY_STARTING" -> "图传正在启动中，请稍候"
            errorCode != null && errorCode.startsWith("LIVE_") ->
                "开播失败：${error.message}"
            else -> error.message
        }
    }
}
