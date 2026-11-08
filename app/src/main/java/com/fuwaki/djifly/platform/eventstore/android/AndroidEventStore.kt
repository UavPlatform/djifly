package com.fuwaki.djifly.platform.eventstore.android

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.fuwaki.djifly.platform.eventstore.core.EventPacketStore
import com.fuwaki.djifly.platform.eventstore.core.RecordingSourceFiles
import com.fuwaki.djifly.platform.eventstore.core.RecordingSourceIdentity
import com.fuwaki.djifly.platform.eventstore.core.SqlSession
import java.io.File

/** Opt-in factory; not installed into DI until the network contract and capture adapter are ready. */
object AndroidEventStore {
    private fun files(context: Context) = RecordingSourceFiles(File(context.noBackupFilesDir, "event-packets"))

    /**
     * Stable across reinstalls of the same device: the backend binds an event chain to
     * orderId + recordingSourceId + airframe, so a fresh random id per install makes every reinstall look like a new
     * terminal and strands the chain the order already owns.
     *
     * ANDROID_ID is per-app-signing-key and per-user, and survives app reinstall (a factory reset changes it, which is
     * a genuinely new device). See [RecordingSourceIdentity] for the derivation itself.
     */
    private fun stableSourceId(context: Context): String {
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
        )
        // No stable platform id: fall back to a random one rather than inventing stability we do not have.
        return RecordingSourceIdentity.derive(androidId) ?: java.util.UUID.randomUUID().toString()
    }

    fun initializeNew(context: Context): EventPacketStore {
        val files = files(context)
        val id = files.createNew(stableSourceId(context))
        return open(files.databaseFile) { EventPacketStore.initializeNew(it, id) }
    }

    fun recover(context: Context): EventPacketStore {
        val files = files(context)
        val id = files.recover() // Must happen before SQLite can create any empty file.
        return open(files.databaseFile) { EventPacketStore(it, id) }
    }

    private fun open(file: File, factory: (SqlSession) -> EventPacketStore): EventPacketStore {
        val session = AndroidSqlSession(SQLiteDatabase.openDatabase(file.path, null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY))
        return try { factory(session) } catch (e: Throwable) { session.close(); throw e }
    }
}

private class AndroidSqlSession(private val db: SQLiteDatabase) : SqlSession {
    init {
        db.setForeignKeyConstraintsEnabled(true)
        db.execSQL("PRAGMA synchronous=FULL")
    }
    override fun execute(sql: String, vararg args: Any?) = db.execSQL(sql, args)
    override fun query(sql: String, vararg args: Any?): List<List<Any?>> =
        db.rawQuery(sql, args.map { it?.toString() }.toTypedArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add((0 until cursor.columnCount).map { index ->
                    when (cursor.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(index)
                        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                        else -> cursor.getString(index)
                    }
                })
            }
        }
    override fun <T> transaction(block: () -> T): T {
        db.beginTransaction()
        return try { block().also { db.setTransactionSuccessful() } } finally { db.endTransaction() }
    }
    override fun close() = db.close()
}
