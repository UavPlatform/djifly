package com.fuwaki.djifly.platform.telemetry

import com.fuwaki.djifly.platform.dispatch.DispatchStages
import com.fuwaki.djifly.platform.network.generated.ApiDispatchOrderVO
import com.fuwaki.djifly.platform.network.generated.ApiFlightConnectDTO
import com.fuwaki.djifly.platform.network.generated.ApiFlightConnectionVO
import com.fuwaki.djifly.platform.network.generated.ApiFlightSampleBatchDTO
import com.fuwaki.djifly.sdk.FlightDataState
import java.util.UUID

/** HTTP 结果；[code] 为 null 表示网络不可用。 */
data class ApiReply<T>(val code: Int?, val body: T? = null)

/** 上传协程用到的三个后端接口，便于 JVM 单测替换。 */
interface FlightApi {
    suspend fun connect(orderId: String, request: ApiFlightConnectDTO): ApiReply<ApiFlightConnectionVO>
    suspend fun upload(orderId: String, connectionId: String, batch: ApiFlightSampleBatchDTO): Int?
    suspend fun close(orderId: String, connectionId: String): Int?
}

/** 一次采样时刻的输入：SDK 状态 + 当前订单。 */
data class FlightSnapshot(
    val productConnected: Boolean,
    val order: ApiDispatchOrderVO?,
    val serialNumber: String?,
    val modelCode: String?,
    val modelName: String?,
    val flight: FlightDataState
)

data class RecorderStatus(
    val active: LocalConnection?,
    val pendingSamples: Int,
    val cacheWarning: String?,
    val error: String?
)

/**
 * 执飞记录的核心逻辑（ADR-0007 #6、ADR-0009），纯 Kotlin：
 * - [record]：采样协程调用，只读写本机文件，**从不访问网络**，所以上传慢或断网都不会拖慢 2 秒采样；
 * - [sync]：上传协程调用：先向后端登记本机连机记录（带本机开始时间），再补传采样，最后上报已结束的连接。
 */
class FlightRecorder(private val queue: TelemetryQueue, private val journal: ConnectionJournal) {
    private var sampler = TelemetrySampler()
    @Volatile private var cacheWarning: String? = null
    @Volatile private var error: String? = null
    private var unsaved = 0L

    init {
        // 进程重启后无法接上原来的序号，上一条未结束的连接就此结束，重新连机时开新记录。
        // 结束时间取这条连接最后一次采样的时间（没有采样则取开始时间），不用「现在」，避免把停机时段算进连机时长。
        val queued = queue.peek(queue.capacity).groupBy { it.connectionId }
            .mapValues { (_, v) -> v.mapNotNull { it.sample.capturedAt }.maxOrNull() }
        journal.all().filter { it.endedAt == null }.forEach { c ->
            val last = listOfNotNull(c.lastSampleAt, queued[c.localId]).maxOrNull() ?: c.startedAt
            journal.update(c.localId) { it.copy(endedAt = last) }
        }
    }

    internal fun journalForTest(): ConnectionJournal = journal

    fun active(): LocalConnection? = journal.all().lastOrNull { it.endedAt == null }

    fun status() = RecorderStatus(active(), queue.size(), cacheWarning, error)

    @Synchronized
    fun record(now: Long, s: FlightSnapshot) {
        val order = s.order
        val orderId = order?.orderId
        val ready = s.productConnected && order != null && orderId != null && DispatchStages.isFlyable(order) &&
            s.serialNumber != null && s.modelCode != null
        var current = active()
        if (current != null && (!ready || current.orderId != orderId || current.serialNumber != s.serialNumber)) {
            journal.update(current.localId) { it.copy(endedAt = now) }
            current = null
        }
        if (!ready) return
        if (current == null) {
            current = LocalConnection(UUID.randomUUID().toString(), orderId!!, order!!.assignmentVersion ?: 0L,
                UUID.randomUUID().toString(), s.serialNumber!!, s.modelCode!!, s.modelName?.takeIf { it.isNotBlank() } ?: s.modelCode!!,
                startedAt = now)
            journal.add(current)
            sampler = TelemetrySampler()
        }
        val sample = sampler.sample(s.flight, now, DispatchStages.cargoWeightKg(order))
        journal.markSample(current.localId, now)
        if (queue.add(QueuedSample(current.orderId, current.localId, sample))) {
            cacheWarning = if (queue.size() >= queue.capacity * 9 / 10)
                "离线遥测缓存即将用满（${queue.size()}/${queue.capacity}），请尽快联网补传" else null
        } else {
            unsaved++
            cacheWarning = "离线遥测缓存已满（${queue.capacity} 条），新的遥测无法保存（已有 $unsaved 条未保存），请立即联网补传"
        }
    }

    /**
     * 结束 → 登记 → 补传。先上报已结束的旧连接，避免旧连接仍占着机身号时新连接登记被 409。
     * 任何一步遇到网络问题都停下，下一轮再试；不丢本机记录。
     */
    suspend fun sync(api: FlightApi) {
        var problem: String? = null
        for (c in journal.all().filter { it.endedAt != null && it.serverId != null && !it.closed }) {
            val code = runCatching { api.close(c.orderId, c.serverId!!) }.getOrNull()
            when {
                code in 200..299 -> journal.update(c.localId) { it.copy(closed = true) }
                retryable(code) -> {
                    val failures = journal.update(c.localId) { it.copy(closeFailures = it.closeFailures + 1) }?.closeFailures ?: 0
                    if (failures >= CLOSE_WARN_AFTER) problem = "结束连机上报失败（已重试 $failures 次），会继续自动重试"
                }
                else -> {
                    journal.update(c.localId) { it.copy(closed = true) }
                    problem = "结束连机被平台拒绝（$code），请联系调度核对本单连机时长"
                }
            }
        }
        for (c in journal.all().filter { it.serverId == null && it.rejected == null }) {
            val reply = runCatching {
                api.connect(c.orderId, ApiFlightConnectDTO(c.assignmentVersion, c.modelCode, c.modelName, c.requestKey,
                    c.serialNumber, c.startedAt))
            }.getOrElse { ApiReply(null) }
            val serverId = reply.body?.connectionId
            when {
                reply.code in 200..299 && serverId != null ->
                    journal.update(c.localId) { it.copy(serverId = serverId, overweight = reply.body?.overweight == true) }
                retryable(reply.code) -> { problem = problem ?: "连机记录已在本机保存，联网后自动上报"; break }
                // 409（订单阶段刚变化、旧连接尚未结束等）可能是暂时的：保留本机记录和采样，有限次重试后才放弃。
                reply.code == 409 && c.connectConflicts + 1 < CONNECT_CONFLICT_LIMIT -> {
                    journal.update(c.localId) { it.copy(connectConflicts = it.connectConflicts + 1) }
                    problem = problem ?: "连机记录暂未被平台接受（409），会继续重试"
                }
                else -> {
                    journal.update(c.localId) { it.copy(rejected = "连机记录被拒绝（${reply.code}）", closed = true) }
                    problem = "连机记录被平台拒绝（${reply.code}），本次飞行的遥测不会上报，请检查订单状态"
                }
            }
        }
        TelemetryUploader(queue, BatchSender { orderId, localId, samples ->
            val c = journal.get(localId)
            when {
                c == null || c.rejected != null -> UploadOutcome.Discard
                c.serverId == null -> UploadOutcome.Retry
                else -> runCatching { api.upload(orderId, c.serverId, ApiFlightSampleBatchDTO(samples)) }.getOrNull()
                    ?.let(TelemetryHttp::outcome) ?: UploadOutcome.Retry
            }
        }).flush()
        error = problem
    }

    private fun retryable(code: Int?) = code == null || code == 401 || code == 408 || code == 429 || code >= 500

    companion object {
        const val CLOSE_WARN_AFTER = 3
        /** 登记连续 409 达到此次数才视为永久拒绝（上传协程每 5 秒一轮，约 10 分钟）。 */
        const val CONNECT_CONFLICT_LIMIT = 120
    }
}
