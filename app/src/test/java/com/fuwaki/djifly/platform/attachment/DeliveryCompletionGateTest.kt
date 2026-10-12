package com.fuwaki.djifly.platform.attachment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class DeliveryCompletionGateTest {

    private fun item(phase: DeliverableItemPhase) = DeliverableItemUi(
        item = PendingDeliverable(
            id = "id",
            fileName = "a.jpg",
            contentType = "image/jpeg",
            sizeBytes = 1L,
            openStream = { ByteArrayInputStream(byteArrayOf(1)) }
        ),
        phase = phase
    )

    @Test
    fun cannot_confirm_without_uploaded_evidence() {
        assertFalse(DeliveryCompletionGate.canConfirmDelivery(emptyList()))
        assertFalse(DeliveryCompletionGate.canConfirmDelivery(listOf(item(DeliverableItemPhase.PENDING))))
        assertFalse(DeliveryCompletionGate.canConfirmDelivery(listOf(item(DeliverableItemPhase.UPLOADING))))
        assertFalse(DeliveryCompletionGate.canConfirmDelivery(listOf(item(DeliverableItemPhase.FAILED))))
    }

    @Test
    fun can_confirm_after_successful_upload_in_session() {
        assertTrue(DeliveryCompletionGate.canConfirmDelivery(listOf(item(DeliverableItemPhase.UPLOADED))))
    }

    @Test
    fun blocking_upload_detects_pending_failed_uploading() {
        assertTrue(DeliveryCompletionGate.hasBlockingUpload(listOf(item(DeliverableItemPhase.PENDING))))
        assertTrue(DeliveryCompletionGate.hasBlockingUpload(listOf(item(DeliverableItemPhase.FAILED))))
        assertFalse(DeliveryCompletionGate.hasBlockingUpload(listOf(item(DeliverableItemPhase.UPLOADED))))
    }

    @Test
    fun evidence_required_message_is_actionable_chinese() {
        assertEquals(
            "请先拍摄并上传送达凭证，再点击「确认送达」",
            DeliveryCompletionGate.REASON_EVIDENCE_REQUIRED
        )
    }
}
