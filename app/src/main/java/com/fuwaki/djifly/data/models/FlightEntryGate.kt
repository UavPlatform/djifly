package com.fuwaki.djifly.data.models

import com.fuwaki.djifly.platform.dispatch.DispatchStages

/**
 * 撮合子状态（ADR-0003 决定 6「状态表达」；与后端 `com.uav.server.enums.MatchStatus` 同名同值）。
 *
 * djifly 只关心其中的执飞相关子集：只有 [CONFIRMED]（或任务已在执行）才允许进入飞行控制台。
 * 派单唯一路径下，详情把 [DispatchStages] 阶段写入 TaskVo.taskStatus；门禁同时认
 * PILOT_ARRIVED / IN_TRANSIT 为可执飞（与首页 [DispatchStages.isFlyable] 同口径）。
 */
enum class MatchStatus(val label: String) {
    SEEKING_RIDER("招募飞手中"),
    NEGOTIATING("洽谈中"),
    AWAITING_PAYMENT("待支付"),
    AWAITING_RIDER_CONFIRM("待飞手确认"),
    CONFIRMED("双方已确认"),
    PENDING_ACCEPTANCE("待验收"),
    CLOSED("已结案");

    companion object {
        /** 后端返回枚举名（如 "CONFIRMED"）；未知/null → null（按「未确认」处理） */
        fun fromValue(value: String?): MatchStatus? = entries.firstOrNull { it.name == value }
    }
}

/**
 * 执飞状态门禁（TASK-DJIFLY-002 / ADR-0003 决定 4 双确认门禁）。
 *
 * 纯 Kotlin：StarterScreen/FlightScreen/TaskDetailScreen 的入口校验共用同一口径。
 * 派单路径下详情门禁与首页 [DispatchStages.isFlyable] 对齐；旧撮合 MatchStatus 仍保留兼容。
 */
object FlightEntryGate {

    const val TASK_STATUS_IDLE = "IDLE"
    const val TASK_STATUS_IN_PROGRESS = "IN_PROGRESS"
    const val TASK_STATUS_COMPLETED = "COMPLETED"

    /**
     * 首页「待执飞任务」列表口径：已双方确认（CONFIRMED）、已在执行（IN_PROGRESS）、
     * 或已交付待验收（PENDING_ACCEPTANCE，任务仍归属本飞手）。
     */
    fun isPendingFlight(matchStatus: String?, taskStatus: String?): Boolean =
        isPendingFlight(MatchStatus.fromValue(matchStatus), taskStatus)

    fun isPendingFlight(task: TaskVo): Boolean = isPendingFlight(task.matchStatus, task.taskStatus)

    /** 是否可进入飞行控制台：派单可执飞阶段，或 matchStatus=CONFIRMED / 任务已在执行。 */
    fun canEnterFlight(matchStatus: String?, taskStatus: String?): Boolean =
        denyReason(matchStatus, taskStatus) == null

    fun canEnterFlight(task: TaskVo): Boolean = canEnterFlight(task.matchStatus, task.taskStatus)

    /** 拒绝进入的原因；可进入时返回 null。文案可直接展示给飞手。 */
    fun denyReason(matchStatus: String?, taskStatus: String?): String? =
        denyReason(MatchStatus.fromValue(matchStatus), taskStatus)

    fun denyReason(task: TaskVo): String? = denyReason(task.matchStatus, task.taskStatus)

    private fun isPendingFlight(match: MatchStatus?, taskStatus: String?): Boolean =
        DispatchStages.isFlyableStage(taskStatus) ||
            match == MatchStatus.CONFIRMED ||
            match == MatchStatus.PENDING_ACCEPTANCE ||
            taskStatus == TASK_STATUS_IN_PROGRESS

    private fun denyReason(match: MatchStatus?, taskStatus: String?): String? = when {
        // 派单唯一路径：详情把 stage 写入 taskStatus，与 DispatchStages.isFlyable 同口径
        DispatchStages.isFlyableStage(taskStatus) -> null
        match == MatchStatus.CLOSED -> "任务已结案，不能执飞"
        taskStatus == TASK_STATUS_COMPLETED -> when (match) {
            MatchStatus.PENDING_ACCEPTANCE -> "任务已交付，等待用户验收，不能再次执飞"
            else -> "任务已完成，不能再次执飞"
        }
        match == MatchStatus.CONFIRMED -> null
        taskStatus == TASK_STATUS_IN_PROGRESS -> null
        // 其它已知派单阶段（待付定金/等待接单/准备中/送达确认/取消等）
        match == null && !taskStatus.isNullOrBlank() && taskStatus != TASK_STATUS_IDLE ->
            "当前派单阶段（${DispatchStages.label(taskStatus)}）不可执飞；仅「飞手到场」或「运输中」可进入飞行"
        match == null -> "任务撮合状态缺失，无法确认可执飞；请到 app 核对订单状态"
        else -> "双方尚未确认（当前：${match.label}），无法执飞；请到 app 完成下单与确认"
    }
}
