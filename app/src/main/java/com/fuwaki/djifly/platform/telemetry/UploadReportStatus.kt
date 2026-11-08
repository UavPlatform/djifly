package com.fuwaki.djifly.platform.telemetry

/**
 * 壳层「数据交没交」四态人话（DS-DJIFLY-002）。
 * 本轮只暴露状态，不改计费与上报采样策略。
 */
enum class UploadReportPhase {
    NOT_STARTED,
    LOCAL_PENDING,
    ACCEPTED,
    REJECTED,
}

data class UploadReportStatus(
    val phase: UploadReportPhase,
    val phrase: String,
)

object UploadReportStatusResolver {

    /**
     * Only a whole-trip rejection means the trace will not be uploaded. A single packet being refused (HTTP 409 on one
     * packet, a chain conflict, an auth pause) is a retryable transport condition and must not be reported as
     * "this trip was rejected": that wording tells the pilot the flight was discarded, which is false.
     */
    private val WHOLE_TRIP_REJECTION_MARKERS = listOf(
        "本趟被拒绝",
        "订单被拒绝",
    )

    fun resolve(
        hasFlyableOrder: Boolean,
        aircraftConfirmed: Boolean,
        recordingActive: Boolean,
        pendingPackets: Int,
        lastError: String?,
    ): UploadReportStatus {
        val rejected = !lastError.isNullOrBlank() &&
            WHOLE_TRIP_REJECTION_MARKERS.any { lastError.contains(it) }
        if (rejected) {
            return UploadReportStatus(
                phase = UploadReportPhase.REJECTED,
                phrase = "平台拒绝了这一趟（说明本趟轨迹不会上传，可重试或联系调度）",
            )
        }

        // 缺飞机或没有可飞的单：未开始；括号只写真实缺失项，不把「无 connection」谎称成没单。
        if (!aircraftConfirmed || !hasFlyableOrder) {
            val reasons = buildList {
                if (!aircraftConfirmed) add("缺飞机 / 缺机身号")
                if (!hasFlyableOrder) add("没有可飞的单")
            }
            return UploadReportStatus(
                phase = UploadReportPhase.NOT_STARTED,
                phrase = "未开始记录（${reasons.joinToString(" / ")}）",
            )
        }

        if (!recordingActive) {
            return UploadReportStatus(
                phase = UploadReportPhase.NOT_STARTED,
                phrase = "未开始记录",
            )
        }

        val pending = pendingPackets.coerceAtLeast(0)
        // Local capture is intact even when uploads are stuck; say so instead of implying data loss.
        if (!lastError.isNullOrBlank() && pending > 0) {
            return UploadReportStatus(
                phase = UploadReportPhase.LOCAL_PENDING,
                phrase = "已在本机记录 $pending 包，上传暂未成功：$lastError",
            )
        }
        if (pending > 0) {
            return UploadReportStatus(
                phase = UploadReportPhase.LOCAL_PENDING,
                phrase = "已在本机记录，等待上传（待传 $pending 包）",
            )
        }

        if (!lastError.isNullOrBlank()) {
            return UploadReportStatus(
                phase = UploadReportPhase.LOCAL_PENDING,
                phrase = "正在本机记录，上传暂未成功：$lastError",
            )
        }

        return UploadReportStatus(
            phase = UploadReportPhase.LOCAL_PENDING,
            phrase = "正在本机记录",
        )
    }
}
