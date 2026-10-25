package com.fuwaki.djifly.platform.telemetry

import com.google.gson.Gson
import java.io.File

/**
 * 本机连机记录（ADR-0009）：连上飞机时先在本机记下机型、机身号和开始时间，不依赖网络；
 * 联网后再向后端登记（[serverId]），断开时记下 [endedAt]，再上报结束（[closed]）。
 */
data class LocalConnection(
    val localId: String,
    val orderId: String,
    val assignmentVersion: Long,
    val requestKey: String,
    val serialNumber: String,
    val modelCode: String,
    val modelName: String,
    val startedAt: Long,
    val serverId: String? = null,
    val endedAt: Long? = null,
    val closed: Boolean = false,
    val closeFailures: Int = 0,
    /** 后端永久拒绝登记的原因（例如订单已不归本人）；这条连接的采样不再上报，并提示飞手。 */
    val rejected: String? = null,
    val overweight: Boolean = false,
    /** 登记连续收到 409 的次数（有限重试）。 */
    val connectConflicts: Int = 0,
    /** 最后一次采样时间（重启时作为未结束连接的结束时间）。 */
    val lastSampleAt: Long? = null
)

interface ConnectionJournal {
    fun all(): List<LocalConnection>
    fun get(localId: String): LocalConnection?
    fun add(c: LocalConnection)
    /** 原子更新：基于最新记录修改，采样协程与上传协程并发更新同一条不会互相覆盖。 */
    fun update(localId: String, change: (LocalConnection) -> LocalConnection): LocalConnection?
    /** 记下最后一次采样时间；高频调用，不追加日志行。 */
    fun markSample(localId: String, at: Long)
}

/** 追加写的 JSON Lines：每次变更追加一行完整记录，读取时同一 localId 以最后一行为准；启动时整理并清掉已完结的旧记录。 */
class FileConnectionJournal(private val file: File, private val gson: Gson = Gson()) : ConnectionJournal {
    private val records = LinkedHashMap<String, LocalConnection>()
    /** 最后采样时间单独存一个小文件（「localId 时间」整体覆盖），不让 2 秒一次的采样撑大日志。 */
    private val lastFile = File(file.parentFile, file.name + ".last")

    init {
        file.parentFile?.mkdirs()
        if (file.exists()) {
            file.forEachLine { line ->
                runCatching { gson.fromJson(line, LocalConnection::class.java) }.getOrNull()
                    ?.takeIf { it.localId != null && it.orderId != null }
                    ?.let { records[it.localId] = it }
            }
        }
        runCatching { lastFile.readText().trim().split(' ') }.getOrNull()
            ?.takeIf { it.size == 2 }
            ?.let { (id, at) -> at.toLongOrNull()?.let { t -> records[id]?.let { records[id] = it.copy(lastSampleAt = maxOf(t, it.lastSampleAt ?: t)) } } }
        val cutoff = System.currentTimeMillis() - KEEP_DONE_MS
        records.values.removeAll { it.closed && (it.endedAt ?: it.startedAt) < cutoff }
        file.writeText(records.values.joinToString("") { gson.toJson(it) + "\n" })
    }

    @Synchronized override fun all(): List<LocalConnection> = records.values.toList()
    @Synchronized override fun get(localId: String): LocalConnection? = records[localId]

    @Synchronized override fun add(c: LocalConnection) {
        records[c.localId] = c
        file.appendText(gson.toJson(c) + "\n")
    }

    @Synchronized override fun update(localId: String, change: (LocalConnection) -> LocalConnection): LocalConnection? {
        val next = change(records[localId] ?: return null)
        add(next)
        return next
    }

    @Synchronized override fun markSample(localId: String, at: Long) {
        val c = records[localId] ?: return
        records[localId] = c.copy(lastSampleAt = at)
        lastFile.writeText("$localId $at")
    }

    private companion object {
        const val KEEP_DONE_MS = 7L * 24 * 3600 * 1000
    }
}
