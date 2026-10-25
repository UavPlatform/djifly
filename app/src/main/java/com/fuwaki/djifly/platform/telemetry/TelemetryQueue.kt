package com.fuwaki.djifly.platform.telemetry

import com.fuwaki.djifly.platform.network.generated.ApiFlightSampleDTO
import com.google.gson.Gson
import java.io.File

/** 待上报的一条采样：属于哪个订单、哪条本机连机记录（localId），补传时按连接分批。 */
data class QueuedSample(val orderId: String, val connectionId: String, val sample: ApiFlightSampleDTO)

/** 离线缓存：先落盘再上报，断网或进程重启都不丢（ADR-0009）。按先进先出从头部删除。 */
interface TelemetryQueue {
    /** @return false 表示缓存已满、这条没有存下（调用方必须提示飞手，不能静默丢）。 */
    fun add(item: QueuedSample): Boolean
    fun peek(max: Int): List<QueuedSample>
    /** 删除头部的 [count] 条（已送达或被永久拒绝）。 */
    fun removeHead(count: Int)
    fun size(): Int
    val capacity: Int
}

/**
 * 追加写的 JSON Lines 持久队列：
 * - 每条采样只在文件末尾追加一行，不会每次采样都重写整个文件；
 * - 已上报的头部只记一个偏移量（小文件 `.head`）；队列清空时截断，或已上报部分超过一半且很多时才整理一次；
 * - 满了（[capacity]）就拒绝写入并返回 false，由上层提醒飞手，不丢弃旧数据。
 */
class FileTelemetryQueue(
    private val file: File,
    override val capacity: Int = 50_000,
    private val gson: Gson = Gson()
) : TelemetryQueue {
    private val headFile = File(file.parentFile, file.name + ".head")
    private val items = ArrayDeque<QueuedSample>()
    private var consumedLines = 0L

    init {
        file.parentFile?.mkdirs()
        val skip = runCatching { headFile.readText().trim().toLong() }.getOrDefault(0L)
        var line = 0L
        if (file.exists()) {
            file.forEachLine { text ->
                if (line++ >= skip && text.isNotBlank()) {
                    runCatching { gson.fromJson(text, QueuedSample::class.java) }.getOrNull()
                        ?.takeIf { it.orderId != null && it.connectionId != null && it.sample != null }
                        ?.let { items.addLast(it) }
                }
            }
        }
        consumedLines = minOf(skip, line)
        // 启动时整理一次：只保留未上报的行。
        if (consumedLines > 0) compact()
    }

    @Synchronized
    override fun add(item: QueuedSample): Boolean {
        if (items.size >= capacity) return false
        file.appendText(gson.toJson(item) + "\n")
        items.addLast(item)
        return true
    }

    @Synchronized
    override fun peek(max: Int): List<QueuedSample> = items.take(max)

    @Synchronized
    override fun removeHead(count: Int) {
        val n = minOf(count, items.size)
        if (n <= 0) return
        repeat(n) { items.removeFirst() }
        consumedLines += n
        when {
            items.isEmpty() -> { file.writeText(""); consumedLines = 0; headFile.writeText("0") }
            consumedLines > COMPACT_AFTER && consumedLines > items.size -> compact()
            else -> headFile.writeText(consumedLines.toString())
        }
    }

    @Synchronized
    override fun size(): Int = items.size

    private fun compact() {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.bufferedWriter().use { w -> items.forEach { w.write(gson.toJson(it)); w.newLine() } }
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        consumedLines = 0
        headFile.writeText("0")
    }

    private companion object {
        const val COMPACT_AFTER = 5_000L
    }
}
