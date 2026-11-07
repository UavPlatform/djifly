package com.fuwaki.djifly.probe

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import org.erdtman.jcs.JsonCanonicalizer
import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Candidate evaluation only. Nothing in this module is shipped in the app. */
class JcsFeasibilityTest {
    private fun jcs(json: String) = JsonCanonicalizer(json).encodedString
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Expected strict rejection") } catch (e: Exception) { /* expected */ }
    }

    @Test fun libraryCanonicalizesNegativeZeroButDoesNotPreserveItsSign() {
        assertEquals("{\"n\":0}", jcs("{\"n\":-0.0}"))
    }
    @Test fun rfcSortingIsUtf16NotUnicodeCodePointSorting() {
        assertEquals("{\"1\":1,\"€\":2,\"😀\":3,\"דּ\":4}", jcs("{\"דּ\":4,\"😀\":3,\"€\":2,\"1\":1}"))
    }
    @Test fun canonicalizerPreservesArrayOrderAndDoesNotNormalizeUnicode() {
        assertEquals("{\"a\":[2,1]}", jcs("{ \"a\" : [2,1] }"))
        assertNotEquals(jcs("{\"s\":\"é\"}"), jcs("{\"s\":\"e\\u0301\"}"))
        assertEquals(jcs("{\"s\":\"é\"}"), jcs("{\"s\":\"\\u00e9\"}"))
    }
    @Test fun missingIsNotNullAndUnknownFieldsAreNotSchemaValidated() {
        assertNotEquals(jcs("{}"), jcs("{\"s\":null}"))
        assertEquals("{\"unknown\":1}", jcs("{\"unknown\":1}"))
        rejected { StrictIntegerProfile.validate("{\"unknown\":1}".toByteArray(), setOf("known")) }
    }
    @Test fun libraryHasDuplicateNullKeyBypassSoCannotBeTrustedAsStrictIngress() {
        assertEquals("{\"a\":1}", jcs("{\"a\":null,\"a\":1}"))
        rejected { StrictIntegerProfile.validate("{\"a\":null,\"a\":1}".toByteArray()) }
        rejected { StrictIntegerProfile.validate("{\"a\":null,\"\\u0061\":1}".toByteArray()) }
        rejected { StrictIntegerProfile.validate("{\"outer\":[{\"a\":null,\"a\":1}]}".toByteArray()) }
    }
    @Test fun libraryAcceptsLeadingZeroButStrictScannerRejectsIt() {
        assertEquals("{\"a\":1}", jcs("{\"a\":01}"))
        rejected { StrictIntegerProfile.validate("{\"a\":01}".toByteArray()) }
    }
    @Test fun strictScannerRejectsLoneSurrogatesInKeysAndValues() {
        // Library emits a lone surrogate string; UTF-8 encoding silently replaces it on the JVM.
        assertTrue(jcs("{\"s\":\"\\ud800\"}").contains('\ud800'))
        rejected { StrictIntegerProfile.validate("{\"s\":\"\\ud800\"}".toByteArray()) }
        rejected { StrictIntegerProfile.validate("{\"\\udc00\":1}".toByteArray()) }
        StrictIntegerProfile.validate("{\"s\":\"\\ud83d\\ude00\"}".toByteArray())
    }
    @Test fun strictUtf8MustRunBeforeJsonLibraryByteConstructor() {
        val bad = byteArrayOf(123,34,115,34,58,34,0xc3.toByte(),0x28,34,125)
        assertTrue(JsonCanonicalizer(bad).encodedString.contains('\ufffd'))
        rejected { StrictIntegerProfile.validate(bad) }
        rejected { StrictIntegerProfile.validate(byteArrayOf(0xef.toByte(),0xbb.toByte(),0xbf.toByte()) + "{}".toByteArray()) }
    }
    @Test fun strictIntegerProfileRejectsUnsafeOrFractionalValuesBeforeDoubleRounding() {
        StrictIntegerProfile.validate("{\"n\":9007199254740991,\"t\":1794039540000}".toByteArray())
        rejected { StrictIntegerProfile.validate("{\"n\":9007199254740992}".toByteArray()) }
        rejected { StrictIntegerProfile.validate("{\"n\":9007199254740993}".toByteArray()) }
        rejected { StrictIntegerProfile.validate("{\"n\":9223372036854775807}".toByteArray()) }
        rejected { StrictIntegerProfile.validate("{\"n\":9007199254740990.5}".toByteArray()) }
        assertEquals(jcs("{\"n\":9007199254740992}"), jcs("{\"n\":9007199254740993}"))
    }
    @Test fun strictIntegerProfileRejectsNonfiniteExponentOverflowAndSubnormalInputs() {
        listOf("NaN", "Infinity", "-Infinity", "1e309", "1e-320", "1e-315").forEach { token ->
            rejected { StrictIntegerProfile.validate("{\"n\":$token}".toByteArray()) }
        }
        // Dangerous floating-point inputs never reach the candidate library. No hang test in-process.
    }
    @Test fun strictScannerRejectsTrailingValuesAndLenientExtensions() {
        listOf("{} {}", "{\"a\":1,}", "{a:1}", "{\"a\":1/*x*/}").forEach {
            rejected { StrictIntegerProfile.validate(it.toByteArray()) }
        }
    }
    @Test fun strictProfilePreservesValidRawStringsAndIntegerLexemesForCanonicalizer() {
        val raw = "{\"s\":\"e\\u0301\",\"n\":1e3}"
        StrictIntegerProfile.validate(raw.toByteArray(), setOf("s", "n"))
        assertEquals("{\"n\":1000,\"s\":\"é\"}", jcs(raw))
        // Whether exponent/decimal lexemes for integral values are allowed is still a contract choice.
    }
}

/**
 * TEST-ONLY integer-profile feasibility scanner, not a production schema validator.
 * Unknown fields checked only in the toy root; real typed payload schemas remain unfrozen.
 */
private object StrictIntegerProfile {
    private val max = BigInteger("9007199254740991")
    fun validate(bytes: ByteArray, allowedRoot: Set<String>? = null) {
        require(bytes.size <= 65536) // test harness bound, NOT a proposed production packet limit
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = decoder.decode(ByteBuffer.wrap(bytes)).toString()
        require(!text.startsWith('\ufeff'))
        JsonReader(StringReader(text)).use { reader ->
            reader.isLenient = false
            require(reader.peek() == JsonToken.BEGIN_OBJECT)
            value(reader, allowedRoot, 0)
            require(reader.peek() == JsonToken.END_DOCUMENT)
        }
    }
    private fun value(r: JsonReader, allowed: Set<String>?, depth: Int) {
        require(depth <= 32) // test bound, production limits must be specified separately
        when (r.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                val seen = HashSet<String>()
                r.beginObject()
                while (r.hasNext()) {
                    val name = r.nextName()
                    unicode(name)
                    require(seen.add(name))
                    require(allowed == null || name in allowed)
                    value(r, null, depth + 1)
                }
                r.endObject()
            }
            JsonToken.BEGIN_ARRAY -> { r.beginArray(); while (r.hasNext()) value(r, null, depth + 1); r.endArray() }
            JsonToken.STRING -> unicode(r.nextString())
            JsonToken.BOOLEAN -> r.nextBoolean()
            JsonToken.NULL -> r.nextNull()
            JsonToken.NUMBER -> {
                val token = r.nextString()
                require(token.length <= 64)
                val decimal = BigDecimal(token)
                require(decimal.scale() in -32..32) // reject huge exponents before BigInteger allocation
                val integer = decimal.toBigIntegerExact()
                require(integer.abs() <= max)
            }
            else -> error("Not strict JSON")
        }
    }
    private fun unicode(s: String) {
        var i = 0
        while (i < s.length) {
            val c = s[i++]
            if (Character.isHighSurrogate(c)) {
                require(i < s.length && Character.isLowSurrogate(s[i++])); continue
            }
            require(!Character.isLowSurrogate(c))
        }
    }
}
