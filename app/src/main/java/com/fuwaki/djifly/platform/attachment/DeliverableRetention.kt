package com.fuwaki.djifly.platform.attachment

/**
 * 送达凭证保留策略（DS-DJIFLY-002）：已上传项不因离页 / ViewModel 清理作废。
 */
object DeliverableRetention {
    fun keepUploadedOnly(items: List<DeliverableItemUi>): List<DeliverableItemUi> =
        items.filter { it.phase == DeliverableItemPhase.UPLOADED }

    fun shouldDiscardLocalCache(phase: DeliverableItemPhase): Boolean =
        phase != DeliverableItemPhase.UPLOADED
}
