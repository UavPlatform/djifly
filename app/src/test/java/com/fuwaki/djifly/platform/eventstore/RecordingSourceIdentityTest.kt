package com.fuwaki.djifly.platform.eventstore

import com.fuwaki.djifly.platform.eventstore.core.RecordingSourceIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * The recording source must survive reinstall, because the backend binds a chain to
 * orderId + recordingSourceId + airframe. A random-per-install id strands the chain the order already owns.
 */
class RecordingSourceIdentityTest {

    @Test
    fun the_same_device_id_always_derives_the_same_recording_source() {
        // This is the whole point: reinstalling on the same device must not look like a new terminal.
        val first = RecordingSourceIdentity.derive("9774d56d682e549c")
        val second = RecordingSourceIdentity.derive("9774d56d682e549c")
        assertEquals(first, second)
    }

    @Test
    fun a_different_device_derives_a_different_recording_source() {
        assertNotEquals(
            RecordingSourceIdentity.derive("9774d56d682e549c"),
            RecordingSourceIdentity.derive("0000000000000000"),
        )
    }

    @Test
    fun derived_value_is_a_canonical_lowercase_uuid() {
        val id = RecordingSourceIdentity.derive("9774d56d682e549c")!!
        assertTrue(id, Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(id))
        // Round-tripping through UUID also proves it is well formed.
        assertEquals(id, UUID.fromString(id).toString())
    }

    @Test
    fun absent_platform_id_yields_null_so_callers_fall_back_instead_of_faking_stability() {
        assertNull(RecordingSourceIdentity.derive(null))
        assertNull(RecordingSourceIdentity.derive(""))
        assertNull(RecordingSourceIdentity.derive("   "))
    }

    @Test
    fun the_raw_platform_id_is_not_embedded_in_the_result() {
        val raw = "9774d56d682e549c"
        val id = RecordingSourceIdentity.derive(raw)!!
        assertTrue("raw device id leaked into recording source", !id.contains(raw))
    }
}
