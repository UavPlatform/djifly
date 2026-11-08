package com.fuwaki.djifly.platform.eventstore.core

import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Use ONLY a child of Context.noBackupFilesDir. Not an Android ID, login ID or aircraft ID. */
class RecordingSourceFiles(private val directory: File) {
    val databaseFile: File get() = File(directory, "packets.sqlite")
    private val identityFile get() = File(directory, "recording-source")

    /**
     * Explicit first-install enrollment, never a fallback from failed recovery.
     *
     * [id] lets the caller supply a stable identity derived from the device instead of a fresh random one. That matters
     * because the backend binds a chain to orderId+recordingSourceId+airframe: a random id makes every reinstall look
     * like a brand-new terminal, which strands the existing chain. Callers that cannot derive a stable id keep the
     * random behaviour by omitting it.
     */
    fun createNew(id: String = UUID.randomUUID().toString()): String {
        check(UUID.fromString(id).toString() == id) { "Recording source must be a lowercase UUID" }
        check(!databaseFile.exists() && !identityFile.exists()) { "Recording source already exists or is incomplete" }
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create recording directory" }
        check(identityFile.createNewFile()) { "Recording source already exists" }
        FileOutputStream(identityFile).use { it.write(id.toByteArray(Charsets.UTF_8)); it.fd.sync() }
        // If initialization fails now, recovery deliberately blocks rather than silently minting a source.
        return id
    }

    fun recover(): String {
        check(identityFile.isFile && databaseFile.isFile) { "Recording history missing; cannot resume or claim complete sync" }
        val id = identityFile.readText(Charsets.UTF_8)
        check(UUID.fromString(id).toString() == id) { "Invalid recording source" }
        return id
    }
}
