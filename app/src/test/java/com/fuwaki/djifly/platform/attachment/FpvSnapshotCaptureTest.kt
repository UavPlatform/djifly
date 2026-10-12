package com.fuwaki.djifly.platform.attachment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FpvSnapshotCaptureTest {

    @Test
    fun snapshot_file_name_uses_task_num_and_jpg_extension() {
        val name = FpvSnapshotCapture.snapshotFileName("T-10086")
        assertTrue(name.startsWith("T-10086_evidence_"))
        assertTrue(name.endsWith(".jpg"))
    }

    @Test
    fun snapshot_file_name_without_task_uses_delivery_prefix() {
        val name = FpvSnapshotCapture.snapshotFileName(null)
        assertTrue(name.startsWith("delivery_evidence_"))
    }
}
