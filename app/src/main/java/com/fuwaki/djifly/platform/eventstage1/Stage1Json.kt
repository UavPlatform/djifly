package com.fuwaki.djifly.platform.eventstage1

import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import org.erdtman.jcs.JsonCanonicalizer
import java.io.StringReader
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

@RequiresOptIn(message = "Unfrozen Stage1 adapter: not wired to production collection or DI")
annotation class ExperimentalStage1Api

/** Strict integer ingress BEFORE the candidate JCS serializer. Never use its permissive parser directly. */
internal object Stage1Json {
    const val SAFE = 9007199254740991L
    private val maximum = BigInteger.valueOf(SAFE)
    fun parse(bytes: ByteArray): JsonElement {
        require(bytes.size in 1..262144) { "JSON size" }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        require(!text.startsWith('\ufeff')) { "UTF8 BOM" }
        lexicalStrings(text)
        return JsonReader(StringReader(text)).use { reader ->
            reader.isLenient = false
            val result = read(reader, 0)
            require(reader.peek() == JsonToken.END_DOCUMENT) { "Trailing JSON" }
            result
        }
    }
    private fun read(r: JsonReader, depth: Int): JsonElement {
        require(depth <= 8) { "JSON depth" }
        return when (r.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                r.beginObject(); val o = JsonObject(); val keys = HashSet<String>()
                while (r.hasNext()) {
                    val name = r.nextName(); unicode(name)
                    require(keys.add(name) && keys.size <= 32) { "Duplicate key/object limit" }
                    o.add(name, read(r, depth + 1))
                }
                r.endObject(); o
            }
            JsonToken.BEGIN_ARRAY -> {
                r.beginArray(); val a = JsonArray()
                while (r.hasNext()) { require(a.size() < 500); a.add(read(r, depth + 1)) }
                r.endArray(); a
            }
            JsonToken.STRING -> JsonPrimitive(r.nextString().also(::unicode))
            JsonToken.NUMBER -> {
                val token = r.nextString()
                require(token.length <= 17 && token != "-0" && token.matches(Regex("-?(0|[1-9][0-9]*)"))) { "Integer token" }
                val number = BigInteger(token); require(number.abs() <= maximum) { "Unsafe integer" }; JsonPrimitive(number)
            }
            JsonToken.BOOLEAN -> JsonPrimitive(r.nextBoolean())
            JsonToken.NULL -> { r.nextNull(); JsonNull.INSTANCE }
            else -> error("Invalid JSON token")
        }
    }
    private fun unicode(s: String) {
        require(s.length <= 1024); var i = 0
        while (i < s.length) {
            val c = s[i++]
            if (Character.isHighSurrogate(c)) require(i < s.length && Character.isLowSurrogate(s[i++])) { "Orphan surrogate" }
            else require(!Character.isLowSurrogate(c)) { "Orphan surrogate" }
        }
    }
    private fun lexicalStrings(s: String) {
        var quoted = false; var i = 0
        while (i < s.length) {
            val c = s[i++]
            if (c == '"') quoted = !quoted
            else if (quoted) {
                require(c.code >= 32) { "Unescaped control" }
                if (c == '\\') {
                    require(i < s.length)
                    when (s[i++]) {
                        'u' -> { require(i + 4 <= s.length); repeat(4) { require(s[i++] in "0123456789abcdefABCDEF") { "Non-ASCII escape hex" } } }
                        '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> Unit
                        else -> error("JSON escape")
                    }
                }
            }
        }
    }
    fun canonical(value: JsonElement): ByteArray {
        // Revalidate even trees constructed internally; no mutable/unvalidated Double can reach JCS.
        val checked = parse(value.toString().toByteArray(Charsets.UTF_8))
        return JsonCanonicalizer(checked.toString()).encodedUTF8
    }
    fun sha(vararg parts: ByteArray): String = MessageDigest.getInstance("SHA-256").let { digest ->
        parts.forEach { digest.update(it) }; digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    fun unhex(s: String): ByteArray { require(s.matches(Regex("[0-9a-f]{64}"))); return s.chunked(2).map { it.toInt(16).toByte() }.toByteArray() }
    fun obj(value: JsonElement?): JsonObject { require(value != null && value.isJsonObject); return value.asJsonObject }
    fun text(value: JsonElement?): String { require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString); return value.asString }
    fun long(value: JsonElement?): Long { require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber); return value.asLong.also { require(it in -SAFE..SAFE) } }
    fun uuid(value: String): String = value.also { require(it.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))) }
    fun nullableText(value: JsonElement?): String? = if (value == null || value.isJsonNull) null else text(value)
    fun nullableLong(value: JsonElement?): Long? = if (value == null || value.isJsonNull) null else long(value)
}
