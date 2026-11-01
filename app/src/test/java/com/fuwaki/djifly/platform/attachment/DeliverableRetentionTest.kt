package com.fuwaki.djifly.platform.attachment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliverableRetentionTest {

    private fun item(phase: DeliverableItemPhase, id: String = "d1") = DeliverableItemUi(
        item = PendingDeliverable(
            id = id,
            fileName = "$id.jpg",
            contentType = "image/jpeg",
            sizeBytes = 10,
            openStream = { null },
        ),
        phase = phase,
        objectKey = if (phase == DeliverableItemPhase.UPLOADED) "mat-$id" else null,
    )

    @Test
    fun keep_uploaded_only_drops_pending_and_failed() {
        val kept = DeliverableRetention.keepUploadedOnly(
            listOf(
                item(DeliverableItemPhase.PENDING, "p"),
                item(DeliverableItemPhase.UPLOADED, "u"),
                item(DeliverableItemPhase.FAILED, "f"),
                item(DeliverableItemPhase.UPLOADING, "x"),
            )
        )
        assertEquals(1, kept.size)
        assertEquals("u", kept.single().item.id)
        assertEquals("mat-u", kept.single().objectKey)
    }

    @Test
    fun discard_policy_spares_uploaded() {
        assertFalse(DeliverableRetention.shouldDiscardLocalCache(DeliverableItemPhase.UPLOADED))
        assertTrue(DeliverableRetention.shouldDiscardLocalCache(DeliverableItemPhase.PENDING))
        assertTrue(DeliverableRetention.shouldDiscardLocalCache(DeliverableItemPhase.FAILED))
        assertTrue(DeliverableRetention.shouldDiscardLocalCache(DeliverableItemPhase.UPLOADING))
    }
}
