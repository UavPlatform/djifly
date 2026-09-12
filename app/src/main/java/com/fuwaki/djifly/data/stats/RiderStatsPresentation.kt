package com.fuwaki.djifly.data.stats

import com.fuwaki.djifly.data.models.RiderStats
import java.util.Locale

/**
 * 飞手统计展示态（纯 Kotlin，可 JVM 单测，零 DJI/Android 依赖）。
 */
data class RiderStatsUiState(
    val todayOrdersText: String = ZERO_TEXT,
    val totalCompletedText: String = ZERO_TEXT,
    val totalEarningsText: String = ZERO_EARNINGS_TEXT,
    val earningsNote: String = EARNINGS_NOTE
) {
    companion object {
        const val ZERO_TEXT = "0"
        const val ZERO_EARNINGS_TEXT = "¥0.00"

        /**
         * 收益口径说明（裁决 Q4=A / 路线图 1B-6）：数值来自 /rider/stats，
         * 口径=用户确认完成（订单 COMPLETED）的订单金额合计；提现能力后续上线。
         */
        const val EARNINGS_NOTE = "收益为平台记录，提现后续上线"
    }
}

/**
 * /rider/stats 数值 → 展示文案的映射（纯函数）。
 * 字符串格式固定 Locale.US，避免地区设置导致小数点变逗号。
 */
object RiderStatsPresentation {

    fun todayOrdersText(todayOrders: Long?): String = "${todayOrders ?: 0L}"

    fun totalCompletedText(totalCompleted: Long?): String = "${totalCompleted ?: 0L}"

    fun totalEarningsText(totalEarnings: Double?): String =
        "¥" + "%.2f".format(Locale.US, totalEarnings ?: 0.0)

    fun toUiState(stats: RiderStats?): RiderStatsUiState = RiderStatsUiState(
        todayOrdersText = todayOrdersText(stats?.todayOrders),
        totalCompletedText = totalCompletedText(stats?.totalCompleted),
        totalEarningsText = totalEarningsText(stats?.totalEarnings),
        earningsNote = RiderStatsUiState.EARNINGS_NOTE
    )
}
