package com.fuwaki.djifly.platform.eventstore.core

import java.io.Closeable
import java.security.MessageDigest

/** Local-only envelopes: no JSON DTO, canonicalization or production hash algorithm here. */
data class PacketInput(val packetId: String, val serialNumber: String, val body: ByteArray, val summary: ByteArray)
data class StoredPacket(val packetId: String, val serialNumber: String, val body: ByteArray?, val summary: ByteArray, val durableReceipt: ByteArray?, val localPosition: Long)
data class OperationSnapshot(val orderId: String, val cutoff: ByteArray?, val packets: List<StoredPacket>)
data class OperationRef(val operationId: String, val orderId: String)

/**
 * Isolated foundation, NOT yet wired to SDK callbacks or Retrofit.
 * body/summary/receipt/cutoff are contract-owned opaque bytes. Caller must validate a complete,
 * immutable-original + fixed-results durable receipt before acknowledgeDurable. HTTP 2xx alone
 * and a partial response are NOT such a receipt. No method here reports cloud synchronization.
 */
class EventPacketStore(private val sql: SqlSession, val recordingSourceId: String) : Closeable {
    companion object {
        private const val LOCAL_SCHEMA_VERSION = 2
        const val DEFAULT_PENDING_LIMIT = 64
        const val MAX_PENDING_LIMIT = 256
        fun initializeNew(sql: SqlSession, recordingSourceId: String): EventPacketStore {
            require(recordingSourceId.isNotBlank())
            sql.transaction {
                // No IF NOT EXISTS: never initialize over a recovered or partially initialized store.
                sql.execute("CREATE TABLE source (singleton INTEGER PRIMARY KEY CHECK(singleton=1), id TEXT NOT NULL)")
                sql.execute("INSERT INTO source VALUES (1, ?)", recordingSourceId)
                sql.execute("CREATE TABLE operation (id TEXT PRIMARY KEY, order_id TEXT NOT NULL, cutoff BLOB)")
                sql.execute("""CREATE TABLE packet (
                    local_position INTEGER PRIMARY KEY AUTOINCREMENT,
                    packet_id TEXT NOT NULL UNIQUE, operation_id TEXT NOT NULL REFERENCES operation(id),
                    serial TEXT NOT NULL, body BLOB, summary BLOB NOT NULL, receipt BLOB,
                    local_body_sha256 BLOB NOT NULL CHECK(length(local_body_sha256)=32),
                    CHECK(body IS NOT NULL OR receipt IS NOT NULL))""")
                sql.execute("CREATE INDEX packet_pending_position ON packet(operation_id, local_position) WHERE receipt IS NULL")
                sql.execute("""CREATE TABLE sealed_packet (
                    operation_id TEXT NOT NULL REFERENCES operation(id), ordinal INTEGER NOT NULL,
                    packet_id TEXT NOT NULL REFERENCES packet(packet_id),
                    PRIMARY KEY(operation_id, ordinal), UNIQUE(operation_id, packet_id))""")
                sql.execute("PRAGMA user_version=$LOCAL_SCHEMA_VERSION")
            }
            return EventPacketStore(sql, recordingSourceId)
        }
    }

    init {
        check((sql.query("PRAGMA user_version").single().single() as Number).toInt() == LOCAL_SCHEMA_VERSION) {
            "Unsupported local packet layout; preserve history, do not silently recreate it"
        }
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
        packets.forEach { insert(operationId, freeze(it)) }
    }

    /** Seal packets + ordered request manifest + cutoff atomically. Retry must match the same request. */
    @Synchronized fun seal(operationId: String, finalPackets: List<PacketInput>, cutoff: ByteArray) = sql.transaction {
        val fixedCutoff = cutoff.copyOf()
        val fixedPackets = finalPackets.map { freeze(it) }
        require(fixedCutoff.isNotEmpty())
        require(fixedPackets.map { it.packetId }.distinct().size == fixedPackets.size) { "Duplicate final packet" }
        val existing = operationRow(operationId)[1] as ByteArray?
        if (existing != null) {
            check(existing.contentEquals(fixedCutoff)) { "Cutoff cannot move during completion retry" }
            val originalIds = sql.query("SELECT packet_id FROM sealed_packet WHERE operation_id=? ORDER BY ordinal", operationId)
                .map { it[0] as String }
            check(originalIds == fixedPackets.map { it.packetId }) { "Final packet request cannot change during retry" }
            fixedPackets.forEach { verifyExisting(operationId, it) }
        } else {
            fixedPackets.forEachIndexed { index, packet ->
                insert(operationId, packet)
                sql.execute("INSERT INTO sealed_packet(operation_id, ordinal, packet_id) VALUES (?, ?, ?)", operationId, index, packet.packetId)
            }
            sql.execute("UPDATE operation SET cutoff=? WHERE id=?", fixedCutoff, operationId)
        }
    }

    /** Completion retry can read this token without resubmitting a seal request or reading history. */
    @Synchronized fun sealedCutoff(operationId: String): ByteArray =
        (operationRow(operationId)[1] as ByteArray?) ?: error("Operation recording is not sealed")

    /** Explicit resume keeps every old packet and receipt; caller later seals a new cutoff. */
    @Synchronized fun resume(operationId: String) = sql.transaction {
        operationRow(operationId)
        sql.execute("DELETE FROM sealed_packet WHERE operation_id=?", operationId)
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

    /**
     * Bounded keyset page, NOT a drain-until-empty call. Advance afterPosition even if a row fails
     * upload, so an old bad/offline packet cannot hide newer packets; restart a later sweep at 0.
     * A cursor is local to this store/operation and is not a cloud height or synchronization proof.
     */
    @Synchronized fun pending(
        operationId: String, afterPosition: Long = 0L, limit: Int = DEFAULT_PENDING_LIMIT
    ): List<StoredPacket> = sql.transaction {
        require(afterPosition >= 0L) { "Negative pending cursor" }
        require(limit in 1..MAX_PENDING_LIMIT) { "Pending limit must be within 1..$MAX_PENDING_LIMIT" }
        operationRow(operationId) // Missing local operation must not become an empty success.
        sql.query("""SELECT packet_id, serial, body, summary, receipt, local_position FROM packet
            WHERE operation_id=? AND receipt IS NULL AND local_position>?
            ORDER BY local_position ASC LIMIT ?""", operationId, afterPosition, limit).map { storedPacket(it) }
    }

    /** Bounded lookup APIs for isolated upload adapters; never recover full operation history to send one packet. */
    @Synchronized fun operationOrderId(operationId: String): String = sql.transaction { operationRow(operationId)[0] as String }

    /** Small recovery index used by the process uploader; packet bodies remain the source of truth. */
    @Synchronized fun operations(): List<OperationRef> = sql.transaction {
        sql.query("SELECT id, order_id FROM operation ORDER BY rowid")
            .map { OperationRef(it[0] as String, it[1] as String) }
    }

    @Synchronized fun pendingCount(operationId: String): Int = sql.transaction {
        operationRow(operationId)
        (sql.query("SELECT COUNT(*) FROM packet WHERE operation_id=? AND receipt IS NULL", operationId)
            .single().single() as Number).toInt()
    }

    @Synchronized fun packet(operationId: String, packetId: String): StoredPacket = sql.transaction {
        operationRow(operationId)
        sql.query("SELECT packet_id, serial, body, summary, receipt, local_position FROM packet WHERE operation_id=? AND packet_id=?",
            operationId, packetId).singleOrNull()?.let(::storedPacket) ?: error("Packet not in local operation")
    }

    @Synchronized fun firstSummary(operationId: String): ByteArray? = sql.transaction {
        operationRow(operationId)
        firstSummaryRow(operationId)?.copyOf()
    }

    /** Atomic first-writer guard for adapter-owned operation attribution in the immutable first summary. */
    @Synchronized fun appendGuarded(operationId: String, packet: PacketInput, expectedFirstSummary: ByteArray?) {
        val expected = expectedFirstSummary?.copyOf()
        val fixed = freeze(packet)
        sql.transaction {
            requireActive(operationId)
            val actual = firstSummaryRow(operationId)
            check(if (expected == null) actual == null else actual != null && actual.contentEquals(expected)) {
                "Operation attribution changed; re-read before append"
            }
            insert(operationId, fixed)
        }
    }

    private fun firstSummaryRow(operationId: String): ByteArray? = sql.query(
        "SELECT summary FROM packet WHERE operation_id=? ORDER BY local_position LIMIT 1", operationId
    ).singleOrNull()?.single() as ByteArray?

    private fun requireActive(operationId: String) {
        check(operationRow(operationId)[1] == null) { "Operation recording already sealed" }
    }

    private fun insert(operationId: String, packet: PacketInput) {
        require(packet.packetId.isNotBlank() && packet.serialNumber.isNotBlank())
        require(packet.body.isNotEmpty() && packet.summary.isNotEmpty())
        if (sql.query("SELECT packet_id FROM packet WHERE packet_id=?", packet.packetId).isNotEmpty()) {
            verifyExisting(operationId, packet)
        } else {
            sql.execute("INSERT INTO packet(packet_id, operation_id, serial, body, summary, local_body_sha256) VALUES (?, ?, ?, ?, ?, ?)",
                packet.packetId, operationId, packet.serialNumber, packet.body, packet.summary, localBodyFingerprint(packet.body))
        }
    }

    private fun verifyExisting(operationId: String, packet: PacketInput) {
        val row = sql.query("SELECT operation_id, serial, local_body_sha256, summary FROM packet WHERE packet_id=?", packet.packetId).singleOrNull()
            ?: error("Unknown final packet on cutoff retry")
        // Raw-byte local fingerprint is retained on release; NOT protocol JCS contentDigest.
        // A matching retry never restores a released body or overwrites any receipt/summary.
        check(row[0] == operationId && row[1] == packet.serialNumber &&
            (row[2] as ByteArray).contentEquals(localBodyFingerprint(packet.body)) &&
            (row[3] as ByteArray).contentEquals(packet.summary)) { "Immutable packet conflict" }
    }

    private fun packets(operationId: String): List<StoredPacket> = sql.query(
        "SELECT packet_id, serial, body, summary, receipt, local_position FROM packet WHERE operation_id=? ORDER BY local_position", operationId
    ).map { storedPacket(it) }

    private fun storedPacket(row: List<Any?>) = StoredPacket(row[0] as String, row[1] as String,
        row[2] as ByteArray?, row[3] as ByteArray, row[4] as ByteArray?, (row[5] as Number).toLong())

    private fun freeze(packet: PacketInput) = packet.copy(body = packet.body.copyOf(), summary = packet.summary.copyOf())
    private fun localBodyFingerprint(body: ByteArray) = MessageDigest.getInstance("SHA-256").digest(body)

    @Synchronized override fun close() = sql.close()
}
