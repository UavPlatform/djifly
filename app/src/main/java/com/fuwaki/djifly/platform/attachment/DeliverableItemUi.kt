package com.fuwaki.djifly.platform.attachment

import java.io.InputStream

/** 交付物上传阶段 */
enum class DeliverableItemPhase { PENDING, UPLOADING, UPLOADED, FAILED }

/**
 * 交付物条目的 ViewModel 展示态（纯数据，可 JVM 单测）。
 *
 * @param item 待上传交付物本体（含流工厂，仅在 UPLOADING 时被消费）
 * @param phase 上传阶段
 * @param message 阶段附注（拒绝原因/失败原因，可空）
 * @param objectKey 上传成功后的 materialId（派单物料 ID）
 */
data class DeliverableItemUi(
    val item: PendingDeliverable,
    val phase: DeliverableItemPhase,
    val message: String? = null,
    val objectKey: String? = null
)

/**
 * 从 ContentResolver 元数据构造待上传交付物的入参聚合（UI 层收集后调用）。
 * openStream 延迟求值：真正 PUT 时才打开流。
 */
data class DeliverablePick(
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val openStream: () -> InputStream?
)
