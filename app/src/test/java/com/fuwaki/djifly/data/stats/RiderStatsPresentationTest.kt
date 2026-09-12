package com.fuwaki.djifly.data.stats

import com.fuwaki.djifly.data.models.RiderStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * /rider/stats 数值 → 展示文案映射纯单元测试（1B-6，零 DJI/Android 依赖）。
 * 格式固定 Locale.US，避免地区设置导致小数点变逗号。
 */
class RiderStatsPresentationTest {

    @Test
    fun null_stats_maps_to_zero_state_with_note() {
        val ui = RiderStatsPresentation.toUiState(null)

        assertEquals("0", ui.todayOrdersText)
        assertEquals("0", ui.totalCompletedText)
        assertEquals("¥0.00", ui.totalEarningsText)
        assertEquals("收益为平台记录，提现后续上线", ui.earningsNote)
    }

    @Test
    fun zero_stats_maps_to_zero_state() {
        val ui = RiderStatsPresentation.toUiState(
            RiderStats(todayOrders = 0, totalCompleted = 0, totalEarnings = 0.0)
        )

        assertEquals("0", ui.todayOrdersText)
        assertEquals("0", ui.totalCompletedText)
        assertEquals("¥0.00", ui.totalEarningsText)
    }

    @Test
    fun real_values_map_to_display_texts() {
        val ui = RiderStatsPresentation.toUiState(
            RiderStats(todayOrders = 3, totalCompleted = 12, totalEarnings = 128.5)
        )

        assertEquals("3", ui.todayOrdersText)
        assertEquals("12", ui.totalCompletedText)
        assertEquals("¥128.50", ui.totalEarningsText)
    }

    @Test
    fun null_earnings_falls_back_to_zero() {
        assertEquals(
            "¥0.00",
            RiderStatsPresentation.totalEarningsText(null)
        )
        assertEquals(
            "¥0.00",
            RiderStatsPresentation.totalEarningsText(
                RiderStats(todayOrders = 1, totalCompleted = 1, totalEarnings = null).totalEarnings
            )
        )
    }

    @Test
    fun earnings_format_is_locale_stable() {
        // 固定 Locale.US：小数点必须是点号，不受系统地区影响
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)   // 德语区小数点为逗号
            assertEquals(
                "¥128.50",
                RiderStatsPresentation.totalEarningsText(128.5)
            )
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun large_earnings_keep_two_decimals() {
        assertEquals("¥12345.68", RiderStatsPresentation.totalEarningsText(12345.678))
        assertEquals("¥0.10", RiderStatsPresentation.totalEarningsText(0.1))
    }

    @Test
    fun earnings_note_states_platform_record_and_withdrawal_pending() {
        // 口径文案必须包含平台记录与提现后续上线两层语义
        val note = RiderStatsUiState.EARNINGS_NOTE
        assertTrue(note.contains("收益为平台记录"))
        assertTrue(note.contains("提现后续上线"))
        assertEquals("收益为平台记录，提现后续上线", note)
    }
}
