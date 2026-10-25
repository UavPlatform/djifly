package com.fuwaki.djifly.platform.telemetry

import com.fuwaki.djifly.platform.dispatch.FlightSample
import com.google.gson.Gson
import java.io.File

/** 待上报的一条采样，记住它属于哪个订单与连接，补传时按连接分批。 */
data class QueuedSample(val orderId: String, val connectionId: String, val sample: FlightSample)

/** 离线缓存：先落盘再上报，断网或进程重启都不丢（ADR-0009）。 */
interface TelemetryQueue {
    fun add(item: QueuedSample)
    fun peek(max: Int): List<QueuedSample>
    fun remove(items: Collection<QueuedSample>)
    fun size(): Int
}

/**
 * 基于 JSON Lines 文件的持久队列。每次变更整体重写临时文件再原子替换，
 * 数据量以「单架次 2 秒一条」计很小；超过 [capacity] 时丢弃最旧的条目并计数。
 */
class FileTelemetryQueue(
    private val file: File,
    private val capacity: Int = 50_000,
    private val gson: Gson = Gson()
) : TelemetryQueue {
    private val items = ArrayList<QueuedSample>()
    var droppedForCapacity: Long = 0
        private set

    init {
        if (file.exists()) {
            file.forEachLine { line ->
                if (line.isNotBlank()) {
                    runCatching { gson.fromJson(line, QueuedSample::class.java) }.getOrNull()
                        ?.takeIf { it.orderId != null && it.connectionId != null && it.sample != null }
                        ?.let { items.add(it) }
                }
            }
        }
    }

    @Synchronized
    override fun add(item: QueuedSample) {
        items.add(item)
        while (items.size > capacity) {
            items.removeAt(0); droppedForCapacity++
        }
        persist()
    }

    @Synchronized
    override fun peek(max: Int): List<QueuedSample> = items.take(max).toList()

    @Synchronized
    override fun remove(items: Collection<QueuedSample>) {
        if (items.isEmpty()) return
        val gone = items.toHashSet()
        this.items.removeAll { it in gone }
        persist()
    }

    @Synchronized
    override fun size(): Int = items.size

    private fun persist() {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.bufferedWriter().use { w -> items.forEach { w.write(gson.toJson(it)); w.newLine() } }
        if (!tmp.renameTo(file)) {
            file.delete(); tmp.renameTo(file)
        }
    }
}
