package com.fuwaki.djifly.platform.attachment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 交付物本地预检规则纯单元测试（1B-9b，t43 契约：类型/大小限制）。
 * 覆盖验收标准「类型/大小限制」：白名单类型、大小上限、边界与拒绝文案。
 */
class DeliverableRulesTest {

    @Test
    fun allowed_image_types_pass() {
        for (type in listOf("image/jpeg", "image/png", "image/webp")) {
            val check = DeliverableRules.check(type, sizeBytes = 1024L)
            assertTrue("$type 应通过", check is DeliverableCheck.Ok)
            assertEquals(DeliverableKind.IMAGE, (check as DeliverableCheck.Ok).kind)
        }
    }

    @Test
    fun allowed_video_types_pass() {
        for (type in listOf("video/mp4", "video/quicktime")) {
            val check = DeliverableRules.check(type, sizeBytes = 1024L * 1024)
            assertTrue("$type 应通过", check is DeliverableCheck.Ok)
            assertEquals(DeliverableKind.VIDEO, (check as DeliverableCheck.Ok).kind)
        }
    }

    @Test
    fun content_type_case_insensitive() {
        val check = DeliverableRules.check("IMAGE/JPEG", sizeBytes = 1024L)
        assertTrue(check is DeliverableCheck.Ok)
    }

    @Test
    fun unknown_type_rejected_with_reason() {
        val check = DeliverableRules.check("application/pdf", sizeBytes = 1024L)
        assertTrue(check is DeliverableCheck.Rejected)
        assertEquals(DeliverableRules.REASON_TYPE, (check as DeliverableCheck.Rejected).reason)
    }

    @Test
    fun null_or_blank_type_rejected() {
        assertTrue(DeliverableRules.check(null, sizeBytes = 1024L) is DeliverableCheck.Rejected)
        assertTrue(DeliverableRules.check("  ", sizeBytes = 1024L) is DeliverableCheck.Rejected)
    }

    @Test
    fun image_over_20mb_rejected() {
        val over = DeliverableRules.MAX_IMAGE_BYTES + 1
        val check = DeliverableRules.check("image/jpeg", sizeBytes = over)
        assertTrue(check is DeliverableCheck.Rejected)
        assertEquals(DeliverableRules.REASON_SIZE_IMAGE, (check as DeliverableCheck.Rejected).reason)
    }

    @Test
    fun image_at_20mb_boundary_passes() {
        val check = DeliverableRules.check("image/jpeg", sizeBytes = DeliverableRules.MAX_IMAGE_BYTES)
        assertTrue(check is DeliverableCheck.Ok)
    }

    @Test
    fun video_over_500mb_rejected() {
        val over = DeliverableRules.MAX_VIDEO_BYTES + 1
        val check = DeliverableRules.check("video/mp4", sizeBytes = over)
        assertTrue(check is DeliverableCheck.Rejected)
        assertEquals(DeliverableRules.REASON_SIZE_VIDEO, (check as DeliverableCheck.Rejected).reason)
    }

    @Test
    fun video_at_500mb_boundary_passes() {
        val check = DeliverableRules.check("video/mp4", sizeBytes = DeliverableRules.MAX_VIDEO_BYTES)
        assertTrue(check is DeliverableCheck.Ok)
    }

    @Test
    fun non_positive_or_unknown_size_rejected() {
        assertTrue(DeliverableRules.check("image/jpeg", sizeBytes = 0L) is DeliverableCheck.Rejected)
        assertTrue(DeliverableRules.check("image/jpeg", sizeBytes = null) is DeliverableCheck.Rejected)
        assertTrue(DeliverableRules.check("image/jpeg", sizeBytes = -5L) is DeliverableCheck.Rejected)
    }
}
