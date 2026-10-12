package com.fuwaki.djifly.platform.attachment

/**
 * 送达完成门禁（REQ-DJIFLY-001 / 后端 DELIVERY_EVIDENCE_REQUIRED）：
 * 本次飞行页会话内须至少有一次交付物上传成功，才可调用 complete。
 */
object DeliveryCompletionGate {

    const val REASON_EVIDENCE_REQUIRED =
        "请先拍摄并上传送达凭证，再点击「确认送达」"

    fun canConfirmDelivery(deliverables: List<DeliverableItemUi>): Boolean =
        deliverables.any { it.phase == DeliverableItemPhase.UPLOADED }

    fun hasBlockingUpload(deliverables: List<DeliverableItemUi>): Boolean =
        deliverables.any {
            it.phase == DeliverableItemPhase.PENDING ||
                it.phase == DeliverableItemPhase.UPLOADING ||
                it.phase == DeliverableItemPhase.FAILED
        }
}
