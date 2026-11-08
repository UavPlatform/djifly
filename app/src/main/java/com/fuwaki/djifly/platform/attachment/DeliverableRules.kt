package com.fuwaki.djifly.platform.attachment

import java.util.Locale

/** 交付物类别 */
enum class DeliverableKind { IMAGE, VIDEO }

/**
 * `DispatchMaterialUploadDTO.purpose` 的契约取值。
 *
 * 契约 pattern 为 `CARGO|START_SITE|END_SITE|PILOT_DELIVERY|SHIPPER_RECEIPT`；飞手送达凭证用
 * [PILOT_DELIVERY]。历史上这里发过不在枚举内的 `DELIVERY_PROOF`，后端直接 400，别再用字面量。
 */
object DeliverablePurpose {
    const val CARGO = "CARGO"
    const val START_SITE = "START_SITE"
    const val END_SITE = "END_SITE"
    const val PILOT_DELIVERY = "PILOT_DELIVERY"
    const val SHIPPER_RECEIPT = "SHIPPER_RECEIPT"

    /** 与契约 pattern 保持一致的合法值集合，供测试与校验使用。 */
    val ALL = setOf(CARGO, START_SITE, END_SITE, PILOT_DELIVERY, SHIPPER_RECEIPT)
}

/** 类型/大小限制校验结果（t43 契约：image/jpeg|png|webp ≤20MB、video/mp4|quicktime ≤500MB） */
sealed interface DeliverableCheck {
    data class Ok(val kind: DeliverableKind) : DeliverableCheck
    data class Rejected(val reason: String) : DeliverableCheck
}

/**
 * 交付物本地预检规则（纯 Kotlin，可 JVM 单测）。
 * 与后端 AttachmentService.validateFile 对齐；本地先拦可省一次无效往返，
 * 后端仍会复检（本规则不是安全边界，只是体验优化）。
 */
object DeliverableRules {

    val ALLOWED_IMAGE_CONTENT_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    val ALLOWED_VIDEO_CONTENT_TYPES = setOf("video/mp4", "video/quicktime")

    const val MAX_IMAGE_BYTES = 20L * 1024 * 1024
    const val MAX_VIDEO_BYTES = 500L * 1024 * 1024

    const val REASON_TYPE = "仅支持 JPEG/PNG/WebP 图片或 MP4/MOV 视频"
    const val REASON_SIZE_IMAGE = "图片不能超过 20MB"
    const val REASON_SIZE_VIDEO = "视频不能超过 500MB"

    fun check(contentType: String?, sizeBytes: Long?): DeliverableCheck {
        val type = contentType?.trim()?.lowercase(Locale.US).orEmpty()
        val isImage = type in ALLOWED_IMAGE_CONTENT_TYPES
        val isVideo = type in ALLOWED_VIDEO_CONTENT_TYPES
        if (!isImage && !isVideo) return DeliverableCheck.Rejected(REASON_TYPE)

        val size = sizeBytes ?: return DeliverableCheck.Rejected(REASON_TYPE)
        if (size <= 0) return DeliverableCheck.Rejected(REASON_TYPE)
        return when {
            isImage && size > MAX_IMAGE_BYTES -> DeliverableCheck.Rejected(REASON_SIZE_IMAGE)
            isVideo && size > MAX_VIDEO_BYTES -> DeliverableCheck.Rejected(REASON_SIZE_VIDEO)
            isImage -> DeliverableCheck.Ok(DeliverableKind.IMAGE)
            else -> DeliverableCheck.Ok(DeliverableKind.VIDEO)
        }
    }
}
