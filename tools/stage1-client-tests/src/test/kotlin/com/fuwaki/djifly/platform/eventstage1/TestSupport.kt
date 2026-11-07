@file:OptIn(ExperimentalStage1Api::class)
package com.fuwaki.djifly.platform.eventstage1

import com.google.gson.*
import com.fuwaki.djifly.platform.eventstore.core.SqlSession
import java.sql.DriverManager

internal object Fixture {
    val doc: JsonObject = JsonParser.parseString(javaClass.getResourceAsStream("/backend-d10fc1b/golden-vectors.json")!!.use { it.readBytes().toString(Charsets.UTF_8) }).asJsonObject
    fun vector(index: Int) = doc.getAsJsonArray("vectors")[index].asJsonObject
    fun unhex(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    fun raw(index: Int = 0) = unhex(vector(index)["inputUtf8Hex"].asString)
    fun packet(index: Int = 0): Stage1Packet {
        val bytes = raw(index); val order = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject["orderId"].asString
        return Stage1Codec().decode(bytes, order)
    }
    fun ack(packet: Stage1Packet, outcome: String = "ACCEPTED", version: Long = packet.link.height, contiguous: Long = packet.link.height,
        highest: Long = packet.link.height, integrity: String = "OPEN"): JsonObject {
        val receipt = JsonObject().also { r ->
            r.addProperty("receiptVersion",1); r.addProperty("packetId",packet.link.packetId)
            r.addProperty("contentDigest",packet.contentDigest);r.addProperty("chainHash",packet.link.chainHash)
            r.addProperty("durability","RAW_AND_RESULTS_COMMITTED");r.addProperty("decidedAt",1794048000000L)
            r.add("results",JsonArray().also { a -> packet.eventIds.forEach { id ->
                a.add(JsonObject().also {
                    it.addProperty("eventId",id);it.addProperty("outcome",outcome)
                    it.add("reason",if(outcome=="REJECTED")JsonPrimitive("PAYLOAD_INVALID") else JsonNull.INSTANCE)
                    it.add("sourcePacketId",JsonNull.INSTANCE)
                })
            } })
        }
        val progress = JsonObject().also { p ->
            p.addProperty("validationVersion",version);p.addProperty("contiguousHeight",contiguous)
            p.addProperty("highestReceivedHeight",highest);p.addProperty("contiguousHash",if(contiguous==0L)Stage1Codec().anchor(packet.link.identity) else packet.link.chainHash)
            p.addProperty("integrity",integrity);p.addProperty("projectionRevision",0)
            p.add("missingCursor",JsonNull.INSTANCE);p.add("missingRanges",JsonArray().also { a ->
                if(contiguous+1<highest) a.add(JsonObject().also { it.addProperty("fromHeight",contiguous+1);it.addProperty("toHeight",highest-1) })
            })
        }
        return JsonObject().also { e ->
            e.addProperty("success",true);e.addProperty("code",200);e.add("errorCode",JsonNull.INSTANCE);e.addProperty("message","操作成功")
            e.add("data",JsonObject().also { it.addProperty("schemaVersion",1);it.add("receipt",receipt);it.add("progress",progress) })
        }
    }
    fun wire(json: JsonElement) = json.toString().toByteArray(Charsets.UTF_8)
}

internal class JdbcSession(path: String) : SqlSession {
    private val connection = DriverManager.getConnection("jdbc:sqlite:$path").also { it.createStatement().use { s -> s.execute("PRAGMA foreign_keys=ON") } }
    override fun execute(sql: String, vararg args: Any?) { connection.prepareStatement(sql).use { s -> args.forEachIndexed { i,v -> s.setObject(i+1,v) };s.executeUpdate() } }
    override fun query(sql: String, vararg args: Any?): List<List<Any?>> = connection.prepareStatement(sql).use { s ->
        args.forEachIndexed { i,v -> s.setObject(i+1,v) };s.executeQuery().use { r ->
            buildList { while(r.next()) add((1..r.metaData.columnCount).map { r.getObject(it) }) }
        }
    }
    override fun <T> transaction(block: () -> T): T {
        check(connection.autoCommit);connection.autoCommit=false
        return try { block().also { connection.commit() } } catch(t:Throwable) { connection.rollback();throw t } finally { connection.autoCommit=true }
    }
    override fun close() = connection.close()
}
internal class TestAuthentication(var pilot: Long? = 7L, var token: String? = "fixture-expired") : Stage1Authentication {
    var recoveries=0; var recoveryWorks=true
    override fun currentPilotId()=pilot
    override fun bearerToken()=token
    override fun recover():Boolean { recoveries++;if(recoveryWorks)token="fixture-refreshed";return recoveryWorks }
}

internal class FailingSql(private val delegate: SqlSession) : SqlSession by delegate {
    var failPrefix: String? = null
    override fun execute(sql: String, vararg args: Any?) {
        if (failPrefix?.let(sql::startsWith) == true) throw IllegalStateException("Simulated local transaction write failure")
        delegate.execute(sql,*args)
    }
}
