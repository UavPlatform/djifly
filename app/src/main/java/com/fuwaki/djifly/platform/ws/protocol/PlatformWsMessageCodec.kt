package com.fuwaki.djifly.platform.ws.protocol

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlatformWsMessageCodec @Inject constructor(
    private val gson: Gson
) {

    fun decode(rawText: String): PlatformWsInboundEnvelope? {
        val json = runCatching {
            gson.fromJson(rawText.trim(), JsonObject::class.java)
        }.getOrNull() ?: return null

        val typeRaw = json.stringField("type")?.uppercase(Locale.US)
        val nameRaw = json.stringField("name")?.uppercase(Locale.US)

        return PlatformWsInboundEnvelope(
            rawText = rawText.trim(),
            id = json.stringField("id"),
            typeRaw = typeRaw,
            nameRaw = nameRaw,
            type = typeRaw?.let { runCatching { PlatformWsType.valueOf(it) }.getOrNull() },
            name = nameRaw?.let { runCatching { PlatformWsName.valueOf(it) }.getOrNull() },
            replyTo = json.stringField("replyTo"),
            deviceId = json.stringField("deviceId"),
            timestamp = json.longField("timestamp"),
            success = json.booleanField("success"),
            code = json.stringField("code"),
            message = json.stringField("message"),
            data = json.get("data")
        )
    }

    fun encode(message: Any): String = gson.toJson(message)

    private fun JsonObject.stringField(name: String): String? {
        return primitiveField(name) { it.asString }
    }

    private fun JsonObject.longField(name: String): Long? {
        return primitiveField(name) { it.asLong }
    }

    private fun JsonObject.booleanField(name: String): Boolean? {
        return primitiveField(name) { it.asBoolean }
    }

    private fun <T> JsonObject.primitiveField(
        name: String,
        reader: (JsonElement) -> T
    ): T? {
        val element = get(name)?.takeUnless { it.isJsonNull } ?: return null
        return runCatching { reader(element) }.getOrNull()
    }
}
