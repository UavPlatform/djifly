package com.fuwaki.djifly.platform.eventstore.core

import java.io.Closeable

/** Local-only envelopes: no JSON DTO, canonicalization or production hash algorithm here. */
data class PacketInput(val packetId: String, val serialNumber: String, val body: ByteArray, val summary: ByteArray)
data class StoredPacket(val packetId: String, val serialNumber: String, val body: ByteArray?, val summary: ByteArray, val durableReceipt: ByteArray?)
data class OperationSnapshot(val orderId: String, val cutoff: ByteArray?, val packets: List<StoredPacket>)

/**
 * Isolated foundation, NOT yet wired to SDK callbacks or Retrofit.
 * body/summary/receipt/cutoff are contract-owned opaque bytes. Caller must validate a complete,
 * immutable-original + fixed-results durable receipt before acknowledgeDurable. HTTP 2xx alone
 * and a partial response are NOT such a receipt. No method here reports cloud synchronization.
 */
class EventPacketStore(private val sql: SqlSession, val recordingSourceId: String) : Closeable {
    companion object {
        fun initializeNew(sql: SqlSession, recordingSourceId: String): EventPacketStore {
            require(recordingSourceId.isNotBlank())
            sql.transaction {
                // No IF NOT EXISTS: never initialize over a recovered or partially initialized store.
                sql.execute("CREATE TABLE source (singleton INTEGER PRIMARY KEY CHECK(singleton=1), id TEXT NOT NULL)")
                sql.execute("INSERT INTO source VALUES (1, ?)", recordingSourceId)
                sql.execute("CREATE TABLE operation (id TEXT PRIMARY KEY, order_id TEXT NOT NULL, cutoff BLOB)")
                sql.execute("""CREATE TABLE packet (
                    packet_id TEXT PRIMARY KEY, operation_id TEXT NOT NULL REFERENCES operation(id),
                    serial TEXT NOT NULL, body BLOB, summary BLOB NOT NULL, receipt BLOB,
                    CHECK(body IS NOT NULL OR receipt IS NOT NULL))""")
            }
            return EventPacketStore(sql, recordingSourceId)
        }
    }

    init {
        val source = sql.query("SELECT id FROM source WHERE singleton=1").singleOrNull()?.singleOrNull()
        check(source == recordingSourceId) { "Recording source/history mismatch; cannot claim complete sync" }
    }

    @Synchronized fun beginOperation(operationId: String, orderId: String) = sql.transaction {
        require(operationId.isNotBlank() && orderId.isNotBlank())
        // Explicit new operation, not upsert: a missing old operation must not become an empty success.
        sql.execute("INSERT INTO operation(id, order_id) VALUES (?, ?)", operationId, orderId)
    }

    @Synchronized fun recoverOperation(operationId: String): OperationSnapshot = sql.transaction {
        val row = operationRow(operationId)
        OperationSnapshot(row[0] as String, row[1] as ByteArray?, packets(operationId))
    }

    private fun operationRow(operationId: String): List<Any?> =
        sql.query("SELECT order_id, cutoff FROM operation WHERE id=?", operationId).singleOrNull()
            ?: error("Local operation history missing; cannot claim complete sync")

    /** All inputs in one transaction. Failure leaves neither a partial tail nor a cutoff. */
    @Synchronized fun append(operationId: String, packets: List<PacketInput>) = sql.transaction {
        requireActive(operationId)
        packets.forEach { insert(operationId, it) }
    }

    /** Seal final packets AND a fixed cutoff atomically; repeated close only reuses that exact cutoff. */
    @Synchronized fun seal(operationId: String, finalPackets: List<PacketInput>, cutoff: ByteArray) = sql.transaction {
        require(cutoff.isNotEmpty())
        val existing = operationRow(operationId)[1] as ByteArray?
        if (existing != null) {
            check(existing.contentEquals(cutoff)) { "Cutoff cannot move during completion retry" }
            finalPackets.forEach { verifyExisting(operationId, it) }
        } else {
            finalPackets.forEach { insert(operationId, it) }
            sql.execute("UPDATE operation SET cutoff=? WHERE id=?", cutoff, operationId)
        }
    }

    /** Explicit resume keeps every old packet and receipt; caller later seals a new cutoff. */
    @Synchronized fun resume(operationId: String) = sql.transaction {
        operationRow(operationId)
        sql.execute("UPDATE operation SET cutoff=NULL WHERE id=?", operationId)
    }

    /** Full durable confirmation, not event-level acceptance. Stored receipt is immutable. */
    @Synchronized fun acknowledgeDurable(packetId: String, receipt: ByteArray) = sql.transaction {
        require(receipt.isNotEmpty())
        val old = sql.query("SELECT receipt FROM packet WHERE packet_id=?", packetId).singleOrNull()
            ?: error("Unknown packet")
        val fixed = old[0] as ByteArray?
        check(fixed == null || fixed.contentEquals(receipt)) { "Fixed packet result conflict" }
        if (fixed == null) sql.execute("UPDATE packet SET receipt=? WHERE packet_id=?", receipt, packetId)
    }

    /** Physical BLOB release; summaries and fixed rejection results in receipt survive restart. */
    @Synchronized fun releaseBody(packetId: String) = sql.transaction {
        val row = sql.query("SELECT receipt FROM packet WHERE packet_id=?", packetId).singleOrNull()
            ?: error("Unknown packet")
        check(row[0] != null) { "Original body must remain until full durable confirmation" }
        sql.execute("UPDATE packet SET body=NULL WHERE packet_id=?", packetId)
    }

    /** No head-of-line gate: later packets stay eligible even with an older unconfirmed hole. */
    @Synchronized fun pending(operationId: String): List<StoredPacket> = recoverOperation(operationId).packets.filter { it.durableReceipt == null }

    private fun requireActive(operationId: String) {
        check(operationRow(operationId)[1] == null) { "Operation recording already sealed" }
    }

    private fun insert(operationId: String, packet: PacketInput) {
        require(packet.packetId.isNotBlank() && packet.serialNumber.isNotBlank())
        require(packet.body.isNotEmpty() && packet.summary.isNotEmpty())
        if (sql.query("SELECT packet_id FROM packet WHERE packet_id=?", packet.packetId).isNotEmpty()) {
            verifyExisting(operationId, packet)
        } else {
            sql.execute("INSERT INTO packet(packet_id, operation_id, serial, body, summary) VALUES (?, ?, ?, ?, ?)",
                packet.packetId, operationId, packet.serialNumber, packet.body.copyOf(), packet.summary.copyOf())
        }
    }

    private fun verifyExisting(operationId: String, packet: PacketInput) {
        val row = sql.query("SELECT operation_id, serial, body, summary FROM packet WHERE packet_id=?", packet.packetId).singleOrNull()
            ?: error("Unknown final packet on cutoff retry")
        // A released body cannot be verified locally; never rehydrate it from a new caller's bytes.
        check(row[0] == operationId && row[1] == packet.serialNumber &&
            (row[2] as ByteArray?)?.contentEquals(packet.body) == true &&
            (row[3] as ByteArray).contentEquals(packet.summary)) { "Immutable packet conflict or body already released" }
    }

    private fun packets(operationId: String): List<StoredPacket> = sql.query(
        "SELECT packet_id, serial, body, summary, receipt FROM packet WHERE operation_id=? ORDER BY rowid", operationId
    ).map { StoredPacket(it[0] as String, it[1] as String, it[2] as ByteArray?, it[3] as ByteArray, it[4] as ByteArray?) }

    @Synchronized override fun close() = sql.close()
}
