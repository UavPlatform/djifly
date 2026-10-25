package com.fuwaki.djifly.platform.telemetry

import com.fuwaki.djifly.platform.network.generated.ApiFlightSampleDTO

/** 上报结果：成功、可重试（断网 / 5xx / 401）、永久拒绝（4xx：订单已不归本人、连接不存在等）。 */
sealed class UploadOutcome {
    data object Sent : UploadOutcome()
    data object Retry : UploadOutcome()
    data object Discard : UploadOutcome()
}

fun interface BatchSender {
    suspend fun send(orderId: String, connectionId: String, samples: List<ApiFlightSampleDTO>): UploadOutcome
}

/**
 * 把持久队列按（订单, 连接）分批补传，每批最多 [batchSize] 条（后端上限 500）。
 * 只有确认送达或被永久拒绝的条目才从队列删除；遇到可重试错误（含连机记录尚未登记到服务端）立即停止，下次再试，保证不丢。
 * 队列里的 connectionId 是本机连机记录的 localId，由 [BatchSender] 换成服务端 connectionId。
 */
class TelemetryUploader(
    private val queue: TelemetryQueue,
    private val sender: BatchSender,
    private val batchSize: Int = 200
) {
    init { require(batchSize in 1..500) }

    /** @return 本轮成功送达的条数。 */
    suspend fun flush(maxBatches: Int = 20): Int {
        var delivered = 0
        repeat(maxBatches) {
            val head = queue.peek(batchSize)
            if (head.isEmpty()) return delivered
            val key = head.first().orderId to head.first().connectionId
            val batch = head.takeWhile { (it.orderId to it.connectionId) == key }
            // 队列只支持按头部顺序删除，batch 一定是头部前缀。
            when (sender.send(key.first, key.second, batch.map { it.sample })) {
                UploadOutcome.Sent -> { queue.removeHead(batch.size); delivered += batch.size }
                UploadOutcome.Discard -> queue.removeHead(batch.size)
                UploadOutcome.Retry -> return delivered
            }
        }
        return delivered
    }
}
