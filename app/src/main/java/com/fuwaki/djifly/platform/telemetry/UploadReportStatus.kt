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

    fun resolve(
        hasFlyableOrder: Boolean,
        aircraftConfirmed: Boolean,
        connection: LocalConnection?,
        pendingSamples: Int,
        lastError: String?,
    ): UploadReportStatus {
        val rejected = !lastError.isNullOrBlank() && (
            lastError.contains("拒绝") ||
                lastError.contains("不会上报") ||
                connection?.rejected != null
            )
        if (rejected) {
            return UploadReportStatus(
                phase = UploadReportPhase.REJECTED,
                phrase = "平台拒绝了这一趟（说明本趟轨迹不会上传，可重试或联系调度）",
            )
        }

        if (!aircraftConfirmed || connection == null) {
            val why = when {
                !aircraftConfirmed && !hasFlyableOrder -> "缺飞机 / 没有可飞的单"
                !aircraftConfirmed -> "缺飞机 / 缺机身号"
                else -> "没有可飞的单"
            }
            return UploadReportStatus(
                phase = UploadReportPhase.NOT_STARTED,
                phrase = "未开始记录（$why）",
            )
        }

        if (connection.serverId == null || pendingSamples > 0) {
            val pending = pendingSamples.coerceAtLeast(0)
            return UploadReportStatus(
                phase = UploadReportPhase.LOCAL_PENDING,
                phrase = if (pending > 0) {
                    "已在本机记录，等待交给平台（待传 $pending 条）"
                } else {
                    "已在本机记录，等待交给平台"
                },
            )
        }

        return UploadReportStatus(
            phase = UploadReportPhase.ACCEPTED,
            phrase = "平台已收下",
        )
    }
}
