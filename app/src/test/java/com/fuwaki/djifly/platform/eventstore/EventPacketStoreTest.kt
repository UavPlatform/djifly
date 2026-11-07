package com.fuwaki.djifly.platform.eventstore

import com.fuwaki.djifly.platform.eventstore.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.sql.DriverManager

class EventPacketStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private fun bytes(s: String) = s.toByteArray(Charsets.UTF_8)
    private fun packet(id: String = "p1", serial: String = "aircraft-a", body: String = "opaque original") =
        PacketInput(id, serial, bytes(body), bytes("opaque summary $id"))
    private fun create(dir: File): EventPacketStore {
        val files = RecordingSourceFiles(dir)
        val id = files.createNew()
        return EventPacketStore.initializeNew(JdbcSqlSession(files.databaseFile), id)
    }
    private fun recover(dir: File): EventPacketStore {
        val files = RecordingSourceFiles(dir)
        val source = files.recover()
        return EventPacketStore(JdbcSqlSession(files.databaseFile), source)
    }
    private fun blocked(block: () -> Unit) {
        try { block(); fail("Expected failure") } catch (e: Exception) { /* expected */ }
    }

    @Test fun originalsAndSourceSurviveRestartAndCallerMutation() {
        val dir = temp.newFolder()
        val input = packet(body = "\u0000\u0001中文\n")
        val original = input.body.copyOf()
        val source = create(dir).use { store ->
            store.beginOperation("work", "order")
            store.append("work", listOf(input))
            input.body.fill(0)
            store.pending("work").single().body!!.fill(0)
            store.recordingSourceId
        }
        recover(dir).use { store ->
            assertEquals(source, store.recordingSourceId)
            assertArrayEquals(original, store.pending("work").single().body)
            assertNull(store.recoverOperation("work").cutoff)
        }
    }

    @Test fun conflictingAppendRollsBackEntireTransaction() {
        val dir = temp.newFolder()
        create(dir).use { store ->
            store.beginOperation("work", "order")
            store.append("work", listOf(packet()))
            blocked { store.append("work", listOf(packet("p2"), packet(body = "changed"))) }
        }
        recover(dir).use { assertEquals(listOf("p1"), it.pending("work").map { p -> p.packetId }) }
    }

    @Test fun duplicateIsImmutableAndCannotMoveBetweenOperations() {
        create(temp.newFolder()).use { store ->
            store.beginOperation("work", "order")
            store.beginOperation("other-work", "order")
            store.append("work", listOf(packet(), packet()))
            assertEquals(1, store.pending("work").size)
            blocked { store.append("other-work", listOf(packet())) }
            blocked { store.append("work", listOf(packet(serial = "other-aircraft"))) }
            assertTrue(store.pending("other-work").isEmpty())
        }
    }

    @Test fun noConfirmationCannotReleaseOriginalIncludingSimulatedPartialResponse() {
        create(temp.newFolder()).use { store ->
            store.beginOperation("work", "order")
            store.append("work", listOf(packet()))
            // Partial result/401/408/429/5xx/timeout is deliberately not acknowledgeDurable.
            repeat(6) { blocked { store.releaseBody("p1") } }
            assertArrayEquals(packet().body, store.pending("work").single().body)
        }
    }

    @Test fun durableFixedPartialRejectionReceiptAndSummarySurviveBodyReleaseAndRestart() {
        val dir = temp.newFolder()
        val receipt = bytes("opaque full-original-persisted receipt: event-a accepted; event-b rejected:INVALID")
        create(dir).use { store ->
            store.beginOperation("work", "order")
            store.append("work", listOf(packet()))
            store.acknowledgeDurable("p1", receipt)
            store.acknowledgeDurable("p1", receipt)
            blocked { store.acknowledgeDurable("p1", bytes("changed result")) }
            store.releaseBody("p1")
        }
        recover(dir).use { store ->
            val p = store.recoverOperation("work").packets.single()
            assertNull(p.body)
            assertArrayEquals(packet().summary, p.summary)
            assertArrayEquals(receipt, p.durableReceipt)
            assertTrue(store.pending("work").isEmpty())
            blocked { store.append("work", listOf(packet())) } // no invented rehydration
        }
    }

    @Test fun laterConfirmationDoesNotHideOldHoleOrBlockNewPackets() {
        val dir = temp.newFolder()
        create(dir).use { store ->
            store.beginOperation("work", "order")
            store.append("work", listOf(packet(), packet("p2")))
            store.acknowledgeDurable("p2", bytes("durable p2"))
            store.releaseBody("p2")
        }
        recover(dir).use { store ->
            store.append("work", listOf(packet("p3")))
            assertEquals(listOf("p1", "p3"), store.pending("work").map { it.packetId })
        }
    }

    @Test fun sealIsAtomicAcrossAircraftAndRetryCannotChaseSensorTail() {
        val dir = temp.newFolder()
        val cutoff = bytes("opaque cutoff with ALL chain tips")
        create(dir).use { store ->
            store.beginOperation("work", "order")
            store.append("work", listOf(packet()))
            blocked { store.seal("work", listOf(packet("p2", "aircraft-b"), packet(body = "conflict")), cutoff) }
            assertNull(store.recoverOperation("work").cutoff)
            assertEquals(1, store.pending("work").size)
            store.seal("work", listOf(packet("p2", "aircraft-b")), cutoff)
        }
        recover(dir).use { store ->
            assertArrayEquals(cutoff, store.recoverOperation("work").cutoff)
            assertEquals(setOf("aircraft-a", "aircraft-b"), store.pending("work").map { it.serialNumber }.toSet())
            repeat(3) { blocked { store.append("work", listOf(packet("sensor-after-landing-$it"))) } }
            store.seal("work", emptyList(), cutoff)
            blocked { store.seal("work", emptyList(), bytes("moving cutoff")) }
            blocked { store.seal("work", listOf(packet("new-final")), cutoff) }
            store.resume("work")
            store.append("work", listOf(packet("p3")))
            store.seal("work", emptyList(), bytes("new cutoff"))
            assertEquals(3, store.recoverOperation("work").packets.size)
        }
    }

    @Test fun missingHistoryOrWrongSourceNeverBecomesEmptySyncedOperation() {
        val dir = temp.newFolder()
        create(dir).use { store ->
            blocked { store.recoverOperation("missing") }
            JdbcSqlSession(RecordingSourceFiles(dir).databaseFile).use { sql ->
                blocked { EventPacketStore(sql, "wrong-source") }
            }
        }
        assertTrue(RecordingSourceFiles(dir).databaseFile.delete())
        blocked { RecordingSourceFiles(dir).recover() }
        blocked { RecordingSourceFiles(dir).createNew() }
        blocked { RecordingSourceFiles(temp.newFolder()).recover() }
    }

    @Test fun secondTerminalHasDifferentIdentityAndNoPriorOperation() {
        val a = create(temp.newFolder())
        val b = create(temp.newFolder())
        a.use { first -> b.use { second ->
            first.beginOperation("work", "order")
            first.append("work", listOf(packet()))
            assertNotEquals(first.recordingSourceId, second.recordingSourceId)
            blocked { second.recoverOperation("work") }
        } }
    }

    @Test fun interruptedSqlTransactionIsRolledBackBeforeReopen() {
        val dir = temp.newFolder()
        create(dir).use { it.beginOperation("work", "order") }
        val db = RecordingSourceFiles(dir).databaseFile
        DriverManager.getConnection("jdbc:sqlite:${db.path}").use { connection ->
            connection.autoCommit = false
            connection.createStatement().use {
                it.executeUpdate("UPDATE operation SET cutoff=X'0102' WHERE id='work'")
            }
            // Closing without commit models an abandoned transaction (not a device power-loss test).
        }
        recover(dir).use { assertNull(it.recoverOperation("work").cutoff) }
    }
}

private class JdbcSqlSession(file: File) : SqlSession {
    private val connection = DriverManager.getConnection("jdbc:sqlite:${file.path}")
    init { execute("PRAGMA foreign_keys=ON"); execute("PRAGMA synchronous=FULL") }
    override fun execute(sql: String, vararg args: Any?) {
        connection.prepareStatement(sql).use { statement ->
            args.forEachIndexed { i, arg -> statement.setObject(i + 1, arg) }
            statement.execute()
        }
    }
    override fun query(sql: String, vararg args: Any?): List<List<Any?>> =
        connection.prepareStatement(sql).use { statement ->
            args.forEachIndexed { i, arg -> statement.setObject(i + 1, arg) }
            statement.executeQuery().use { rows -> buildList {
                while (rows.next()) add((1..rows.metaData.columnCount).map { rows.getObject(it) })
            } }
        }
    override fun <T> transaction(block: () -> T): T {
        connection.autoCommit = false
        try { return block().also { connection.commit() } }
        catch (e: Throwable) { connection.rollback(); throw e }
        finally { connection.autoCommit = true }
    }
    override fun close() = connection.close()
}
