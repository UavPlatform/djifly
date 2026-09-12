package com.fuwaki.djifly.data.models

/**
 * 开播请求响应（后端 LiveStartVO record，/live/req 与 /live/rider/req 同构）。
 *
 * 四分支（200，Result.success）：
 * - code=LIVE_STARTED（ackConfirmed=true）：设备已确认启动图传；
 * - code=LIVE_START_PENDING（ackConfirmed=false）：命令已下发，等待设备 ACK（超时兜底由后端处理）；
 * - code=LIVE_ALREADY_RUNNING（ackConfirmed=true）：图传已在运行中（幂等）；
 * - 错误分支走 HTTP 4xx：403 NO_PERMISSION / 404 UAV_NOT_FOUND / 409 UAV_NOT_CONNECTED、LIVE_ALREADY_STARTING / 429。
 */
data class LiveStartVo(
    val requestId: String? = null,
    val roomId: String? = null,
    val ackConfirmed: Boolean = false,
    val liveState: String? = null,
    val code: String? = null
)
