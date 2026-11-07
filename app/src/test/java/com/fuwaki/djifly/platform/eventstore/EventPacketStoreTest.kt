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
    private fun create(dir: File, stringQueryArgs: Boolean = false): EventPacketStore {
        val files = RecordingSourceFiles(dir)
        val id = files.createNew()
        return EventPacketStore.initializeNew(JdbcSqlSession(files.databaseFile, stringQueryArgs), id)
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
            store.append("work", listOf(packet())) // same bytes are idempotent, but never rehydrate
            assertNull(store.recoverOperation("work").packets.single().body)
            blocked { store.append("work", listOf(packet(body = "changed after release"))) }
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
            store.seal("work", listOf(packet("p2", "aircraft-b")), cutoff)
            assertArrayEquals(cutoff, store.sealedCutoff("work"))
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

    @Test fun identicalSealRetryAfterReleaseAndRestartStillSucceeds() {
        val dir = temp.newFolder()
        val finalPackets = listOf(packet("final-a"), packet("final-b", "aircraft-b"))
        val cutoff = bytes("fixed all-aircraft cutoff")
        create(dir).use { store ->
            store.beginOperation("work", "order")
            store.seal("work", finalPackets, cutoff)
            finalPackets.forEach {
                store.acknowledgeDurable(it.packetId, bytes("durable ${it.packetId}"))
                store.releaseBody(it.packetId)
            }
        }
        recover(dir).use { store ->
            store.seal("work", finalPackets, cutoff)
            assertTrue(store.recoverOperation("work").packets.all { it.body == null })
        }
    }

    @Test fun pendingReadsOnlySqlFilteredBoundedRowsNotConfirmedHistory() {
        val dir = temp.newFolder()
        val files = RecordingSourceFiles(dir)
        val id = files.createNew()
        val queries = mutableListOf<String>()
        val delegate = JdbcSqlSession(files.databaseFile)
        val session = object : SqlSession by delegate {
            override fun query(sql: String, vararg args: Any?): List<List<Any?>> {
                queries.add(sql)
                return delegate.query(sql, *args)
            }
        }
        EventPacketStore.initializeNew(session, id).use { store ->
            store.beginOperation("work", "order")
            store.append("work", listOf(packet("confirmed"), packet("pending")))
            store.acknowledgeDurable("confirmed", bytes("durable"))
            queries.clear()
            assertEquals(listOf("pending"), store.pending("work").map { it.packetId })
            val packetQueries = queries.filter { it.contains("FROM packet", ignoreCase = true) }
            assertTrue(packetQueries.isNotEmpty())
            assertTrue("pending must filter receipt IS NULL and LIMIT in SQL", packetQueries.all {
                it.contains("receipt IS NULL", ignoreCase = true) && it.contains("LIMIT", ignoreCase = true)
            })
        }
    }

    @Test fun sealedRequestCannotBeTamperedWithAfterReleaseOrMovedToOtherOperation() {
        val dir = temp.newFolder()
        val finals = listOf(packet("final-a"), packet("final-b", "aircraft-b"))
        val cutoff = bytes("all tips fixed")
        create(dir).use { store ->
            store.beginOperation("work", "order")
            store.beginOperation("same-order-other-work", "order")
            store.beginOperation("other-work", "other-order")
            store.append("work", listOf(packet("older")))
            store.seal("work", finals, cutoff)
            finals.forEach { store.acknowledgeDurable(it.packetId, bytes("durable")); store.releaseBody(it.packetId) }
        }
        recover(dir).use { store ->
            val changedBody = finals[0].copy(body = bytes("tampered chain/body"))
            val changedSummary = finals[0].copy(summary = bytes("tampered chain/summary"))
            val changedSerial = finals[0].copy(serialNumber = "other-aircraft")
            listOf(changedBody, changedSummary, changedSerial).forEach { changed ->
                blocked { store.seal("work", listOf(changed, finals[1]), cutoff) }
            }
            blocked { store.seal("work", emptyList(), cutoff) }
            blocked { store.seal("work", finals.take(1), cutoff) }
            blocked { store.seal("work", finals.reversed(), cutoff) }
            blocked { store.seal("work", listOf(packet("older"), finals[1]), cutoff) }
            blocked { store.seal("work", listOf(finals[0], finals[0]), cutoff) }
            blocked { store.seal("same-order-other-work", finals, cutoff) }
            blocked { store.seal("other-work", finals, cutoff) }
            assertNull(store.recoverOperation("same-order-other-work").cutoff)
            assertNull(store.recoverOperation("other-work").cutoff)
            store.seal("work", finals, cutoff)
            assertArrayEquals(cutoff, store.sealedCutoff("work"))
            assertTrue(store.recoverOperation("work").packets.filter { it.packetId.startsWith("final-") }.all { it.body == null })
        }
    }

    @Test fun boundedKeysetPaginationHasNoGapsDuplicatesOrMixedOrdersWithBothAdapterBindings() {
        listOf(false, true).forEach { stringify ->
            create(temp.newFolder(), stringify).use { store ->
                store.beginOperation("work", "order")
                store.beginOperation("other", "other-order")
                val expected = mutableListOf<String>()
                repeat(301) { i ->
                    store.append("work", listOf(packet("p$i")))
                    if (i % 7 == 0 && i != 0) store.acknowledgeDurable("p$i", bytes("durable"))
                    else expected.add("p$i")
                    if (i % 13 == 0) store.append("other", listOf(packet("other$i")))
                }
                assertEquals(EventPacketStore.DEFAULT_PENDING_LIMIT, store.pending("work").size)
                assertEquals(EventPacketStore.MAX_PENDING_LIMIT, store.pending("work", limit = EventPacketStore.MAX_PENDING_LIMIT).size)
                val seen = mutableListOf<String>()
                var cursor = 0L
                var firstPage = true
                while (true) {
                    val page = store.pending("work", cursor, 17)
                    if (page.isEmpty()) break
                    assertTrue(page.size <= 17)
                    assertTrue(page.all { it.localPosition > cursor && it.durableReceipt == null })
                    assertTrue(page.zipWithNext().all { (a, b) -> a.localPosition < b.localPosition })
                    seen.addAll(page.map { it.packetId })
                    cursor = page.last().localPosition
                    if (firstPage) {
                        // Retain p0 as a bad old hole; acknowledgements may shrink earlier pages.
                        store.acknowledgeDurable(page.last().packetId, bytes("durable during sweep"))
                        store.append("work", listOf(packet("new-during-sweep")))
                        expected.add("new-during-sweep")
                        firstPage = false
                    }
                }
                assertEquals(expected, seen)
                assertEquals(seen.size, seen.distinct().size)
                assertEquals("p0", store.pending("work", limit = 1).single().packetId)
                assertTrue(store.pending("work", Long.MAX_VALUE, 1).isEmpty())
            }
        }
    }

    @Test fun pendingLimitAndCursorValidationNeverAllowUnboundedReads() {
        create(temp.newFolder()).use { store ->
            store.beginOperation("work", "order")
            listOf(0, -1, Int.MIN_VALUE, EventPacketStore.MAX_PENDING_LIMIT + 1, Int.MAX_VALUE).forEach { limit ->
                blocked { store.pending("work", limit = limit) }
            }
            blocked { store.pending("work", afterPosition = -1) }
            blocked { store.pending("work", afterPosition = Long.MIN_VALUE) }
            blocked { store.pending("missing") }
            assertTrue(store.pending("work", limit = EventPacketStore.MAX_PENDING_LIMIT).isEmpty())
        }
    }

    @Test fun pendingCursorSurvivesRestartAndBodyReleaseWithoutOffsetSkipping() {
        val dir = temp.newFolder()
        var cursor = 0L
        create(dir).use { store ->
            store.beginOperation("work", "order")
            store.append("work", (0..8).map { packet("p$it") })
            val first = store.pending("work", limit = 3)
            cursor = first.last().localPosition
            first.forEach { store.acknowledgeDurable(it.packetId, bytes("durable")); store.releaseBody(it.packetId) }
            store.acknowledgeDurable("p5", bytes("out of order"))
        }
        recover(dir).use { store ->
            assertEquals(listOf("p3", "p4"), store.pending("work", cursor, 2).map { it.packetId })
            val second = store.pending("work", cursor, 2)
            assertEquals(listOf("p6", "p7", "p8"), store.pending("work", second.last().localPosition, 3).map { it.packetId })
        }
    }

    @Test fun unsupportedOldLocalLayoutFailsClosedWithoutRecreatingHistory() {
        val files = RecordingSourceFiles(temp.newFolder())
        val source = files.createNew()
        JdbcSqlSession(files.databaseFile).use { sql ->
            sql.execute("CREATE TABLE source(singleton INTEGER PRIMARY KEY, id TEXT NOT NULL)")
            sql.execute("INSERT INTO source VALUES (1, ?)", source)
            blocked { EventPacketStore(sql, source) }
            assertEquals(source, sql.query("SELECT id FROM source").single().single())
            assertEquals(0, (sql.query("PRAGMA user_version").single().single() as Number).toInt())
        }
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

private class JdbcSqlSession(file: File, private val stringQueryArgs: Boolean = false) : SqlSession {
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
            // Android rawQuery binds strings; run pagination with both binding modes.
            args.forEachIndexed { i, arg -> statement.setObject(i + 1, if (stringQueryArgs) arg?.toString() else arg) }
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
