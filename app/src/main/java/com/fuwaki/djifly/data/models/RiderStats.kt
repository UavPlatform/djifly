package com.fuwaki.djifly.data.models

/**
 * 飞手统计（后端 RiderStatsVO，GET /rider/stats，契约 C9）。
 *
 * 口径（后端 TaskServiceImpl.getRiderStats）：
 * - todayOrders：今日接单数（acceptTime 在今日零点之后）
 * - totalCompleted：总完成任务数（completeTime 非空）
 * - totalEarnings：总收益（元）——完成任务关联订单金额合计（COALESCE 0），可能为 null
 */
data class RiderStats(
    val todayOrders: Long = 0,
    val totalCompleted: Long = 0,
    val totalEarnings: Double? = null
)
