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

        // 已确认飞机且有单，但尚未建本地连机：未开始（不要谎称没有可飞的单）。
        if (connection == null) {
            return UploadReportStatus(
                phase = UploadReportPhase.NOT_STARTED,
                phrase = "未开始记录",
            )
        }

        val pending = pendingSamples.coerceAtLeast(0)
        // 只有确实有待传采样时才说「已在本机记录」；serverId==null 且 pending==0 仍是没开始记。
        if (pending > 0) {
            return UploadReportStatus(
                phase = UploadReportPhase.LOCAL_PENDING,
                phrase = "已在本机记录，等待交给平台（待传 $pending 条）",
            )
        }

        if (connection.serverId == null) {
            return UploadReportStatus(
                phase = UploadReportPhase.NOT_STARTED,
                phrase = "未开始记录",
            )
        }

        return UploadReportStatus(
            phase = UploadReportPhase.ACCEPTED,
            phrase = "平台已收下",
        )
    }
}
