package com.fuwaki.djifly.platform.eventstore.core

import java.security.MessageDigest
import java.util.UUID

/**
 * Derives the recording-source identity from a platform-provided device id.
 *
 * Why this exists: the backend binds an event chain to
 * `orderId + recordingSourceId + serialNumber + serialSource + modelCode`. A freshly random recording source per
 * install therefore makes every reinstall look like a brand-new terminal, which strands the chain the order and
 * airframe already own — the client cannot even discover it, because reconciliation is keyed by chainId.
 *
 * Deriving the id from a device-stable value keeps the same terminal identity across reinstalls. A factory reset or a
 * genuinely different device still yields a different id, which is correct: that IS a different terminal.
 *
 * The raw platform id never leaves the device; only its digest is used.
 */
object RecordingSourceIdentity {
    private const val DOMAIN = "djifly-event-source:"

    /**
     * Stable, canonical lowercase UUID derived from [platformId], or null when the platform gave nothing usable.
     *
     * Callers must treat null as "cannot promise stability" and fall back to a random id rather than inventing one.
     */
    fun derive(platformId: String?): String? {
        val value = platformId?.trim()
        if (value.isNullOrEmpty()) return null
        val digest = MessageDigest.getInstance("SHA-256").digest((DOMAIN + value).toByteArray(Charsets.UTF_8))
        // Canonical UUID layout; the protocol only requires lowercase hex in 8-4-4-4-12 form.
        digest[6] = ((digest[6].toInt() and 0x0F) or 0x40).toByte()
        digest[8] = ((digest[8].toInt() and 0x3F) or 0x80).toByte()
        return UUID.nameUUIDFromBytes(digest).toString()
    }
}
