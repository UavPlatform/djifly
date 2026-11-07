@file:OptIn(ExperimentalStage1Api::class)
package com.fuwaki.djifly.platform.eventstage1

import com.fuwaki.djifly.platform.eventstore.core.EventPacketStore
import com.fuwaki.djifly.platform.eventstore.core.PacketInput
import com.fuwaki.djifly.platform.network.generated.ApiPaths
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okio.BufferedSink
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.io.IOException
import java.math.BigInteger

/** Caller supplies thread-safe actual logged-in account binding, not a packet-derived authorization claim. */
@ExperimentalStage1Api
interface Stage1Authentication {
    fun currentPilotId(): Long?
    fun bearerToken(): String?
    /** At most once per received 401. No resend loop here; integrate the existing auth layer explicitly later. */
    fun recover(): Boolean
}

@ExperimentalStage1Api
sealed class Stage1UploadResult {
    data class Durable(val progress: Stage1Progress?, val chainConflict: Boolean = false) : Stage1UploadResult()
    object AlreadyDurable : Stage1UploadResult()
    data class RetryAfter(val delayMillis: Long, val status: Int?) : Stage1UploadResult()
    data class NotDue(val delayMillis: Long) : Stage1UploadResult()
    object AuthenticationRequired : Stage1UploadResult()
    data class HttpRejected(val status: Int) : Stage1UploadResult()
    data class InvalidResponse(val detail: String) : Stage1UploadResult()
    object ChainForked : Stage1UploadResult()
}

/**
 * Explicit-opt-in blocking adapter; call off main thread. No worker, DI, real endpoint default, SDK or production activation.
 * One bounded attempt per invocation. Durable bytes/receipts survive restart; retry deadlines/progress are process-local.
 */
@ExperimentalStage1Api
class Stage1Uploader(baseUrl: HttpUrl, client: OkHttpClient, private val store: EventPacketStore,
    private val pilotId: Long, private val authentication: Stage1Authentication,
    private val wallMillis: () -> Long = { System.currentTimeMillis() },
    private val elapsedMillis: () -> Long = { System.nanoTime() / 1000000 }) {
    private val codec = Stage1Codec()
    private val base = baseUrl.also {
        require(it.encodedPath.endsWith("/") && it.query == null && it.fragment == null && it.username.isEmpty() && it.password.isEmpty())
        require(it.isHttps || it.host in setOf("localhost", "127.0.0.1", "::1")) { "HTTPS required outside local HTTP tests" }
    }
    private val http = client.newBuilder().authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE)
        .callTimeout(if (client.callTimeoutMillis == 0) 30000L else minOf(client.callTimeoutMillis.toLong(), 30000L), java.util.concurrent.TimeUnit.MILLISECONDS)
        .cookieJar(CookieJar.NO_COOKIES).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    private data class RetryState(val count: Int, val due: Long)
    private val retries = HashMap<String, RetryState>()
    private val stopped = HashSet<String>()
    private val forkedChains = HashSet<String>()
    private val progressByChain = HashMap<String, Stage1Progress>()
    private var authenticationBlocked = false
    private var serverDue: Long? = null
    init {
        require(pilotId > 0); Stage1Json.uuid(store.recordingSourceId)
        require(client.interceptors.isEmpty() && client.networkInterceptors.isEmpty()) { "Use a dedicated client without body/auth-mutating interceptors" }
    }

    // Collection must not wait for the uploader monitor while a network response is outstanding.
    // Journal transactions plus appendGuarded CAS protect attribution across concurrent writers.
    fun enqueue(operationId: String, packet: Stage1Packet) {
        requireAccount()
        val order = store.operationOrderId(operationId)
        val validated = codec.decode(packet.originalBytes(), order)
        require(validated.link.identity.recordingSourceId == store.recordingSourceId)
        val first = store.firstSummary(operationId)
        if (first != null) {
            val original = Stage1Receipt.readSummary(first, pilotId).link.identity
            require(original.orderId == order && original.assignmentVersion == validated.link.identity.assignmentVersion &&
                original.recordingSourceId == validated.link.identity.recordingSourceId) { "Cannot rewrite the operation assignment/source" }
        }
        val summary = Stage1Receipt.summary(Stage1Receipt.expected(validated), pilotId)
        store.appendGuarded(operationId, PacketInput(validated.link.packetId, validated.link.identity.serialNumber, validated.originalBytes(), summary), first)
    }
    /** Auth event, not a polling loop. A failed recovery keeps requests paused until a real account/token event. */
    @Synchronized fun authenticationChanged() { requireAccount(); require(!authentication.bearerToken().isNullOrBlank()); authenticationBlocked = false }
    @Synchronized fun latestProgress(chainId: String): Stage1Progress? = progressByChain[chainId]

    @Synchronized fun attempt(operationId: String, packetId: String): Stage1UploadResult {
        requireAccount()
        val row = store.packet(operationId, packetId)
        val expected = Stage1Receipt.readSummary(row.summary, pilotId)
        require(expected.link.packetId == packetId && expected.link.identity.orderId == store.operationOrderId(operationId) &&
            expected.link.identity.recordingSourceId == store.recordingSourceId && expected.link.identity.serialNumber == row.serialNumber)
        if (row.body != null) {
            val packet = codec.decode(row.body, expected.link.identity.orderId)
            require(Stage1Receipt.summary(Stage1Receipt.expected(packet), pilotId).contentEquals(row.summary)) { "Immutable summary/body mismatch" }
        }
        if (row.durableReceipt != null) {
            Stage1Receipt.stored(row.durableReceipt, expected)
            if (row.body != null) store.releaseBody(packetId) // crash after receipt commit, before release
            return Stage1UploadResult.AlreadyDurable
        }
        require(row.body != null) { "Unconfirmed original bytes missing" }
        if (expected.link.identity.chainId in forkedChains) return Stage1UploadResult.ChainForked
        if (packetId in stopped) return Stage1UploadResult.InvalidResponse("Explicit intervention required")
        val token = authentication.bearerToken(); requireAccount()
        if (authenticationBlocked || token.isNullOrBlank()) return Stage1UploadResult.AuthenticationRequired
        val now = elapsedMillis()
        serverDue?.let { if (now < it) return Stage1UploadResult.NotDue(it - now) }
        retries[packetId]?.let { if (now < it.due) return Stage1UploadResult.NotDue(it.due - now) }
        val path = ApiPaths.DISPATCH_PILOT_ORDERS_EVENT_PACKETS.replace("{orderId}", expected.link.identity.orderId)
        val url = base.newBuilder().addPathSegments(path).build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token")
            .post(OriginalAttemptBody(row.body)).build()
        val response = try { http.newCall(request).execute() } catch (_: IOException) { return retry(packetId, null, null) }
        response.use {
            requireAccount() // Never release a former account's body after a login change while the request was in flight.
            if (it.code == 401) {
                val recovered = try { authentication.recover() } catch (_: IOException) { false } catch (_: RuntimeException) { false }
                requireAccount()
                authenticationBlocked = !recovered || authentication.bearerToken().isNullOrBlank()
                return retry(packetId, 401, null)
            }
            if (it.code in setOf(408, 429) || it.code in 500..599) return retry(packetId, it.code, it.header("Retry-After"))
            if (!it.isSuccessful) {
                if (it.code == 409) {
                    val conflict = try {
                        Stage1Json.nullableText(Stage1Json.obj(Stage1Json.parse(readBody(it)))["errorCode"]) == "EVENT_PACKET_CONFLICT"
                    } catch (_: Exception) { false }
                    if (conflict) forkedChains.add(expected.link.identity.chainId)
                }
                stopped.add(packetId); return Stage1UploadResult.HttpRejected(it.code)
            }
            val ack = try { Stage1Receipt.acknowledgement(readBody(it), expected) }
                catch (_: IOException) { return retry(packetId, it.code, null) }
                catch (e: RuntimeException) {
                    stopped.add(packetId); return Stage1UploadResult.InvalidResponse(e.message ?: "Malformed confirmation")
                }
            // Do not misclassify a local transaction failure as an invalid remote receipt. SQL failures
            // propagate to the caller; original bytes remain, or an already committed receipt recovers below.
            store.acknowledgeDurable(packetId, ack.fixedReceipt)
            store.releaseBody(packetId)
            retries.remove(packetId)
            if (ack.chainConflict) forkedChains.add(expected.link.identity.chainId)
            ack.progress?.let { progress ->
                val old = progressByChain[expected.link.identity.chainId]
                if (old == null || progress.validationVersion > old.validationVersion) progressByChain[expected.link.identity.chainId] = progress
                if (progress.integrity == "FORKED") forkedChains.add(expected.link.identity.chainId)
            }
            return Stage1UploadResult.Durable(ack.progress, ack.chainConflict)
        }
    }
    /** One-shot per HTTP call prevents OkHttp's separate 503 Retry-After:0 follow-up path.
     * A later explicit adapter attempt creates a fresh body from the SAME persisted bytes. */
    private class OriginalAttemptBody(bytes: ByteArray) : RequestBody() {
        private val fixed = bytes.copyOf()
        override fun contentType() = "application/json; charset=utf-8".toMediaType()
        override fun contentLength() = fixed.size.toLong()
        override fun isOneShot() = true
        override fun writeTo(sink: BufferedSink) { sink.write(fixed) }
    }
    private fun readBody(response: Response): ByteArray {
        val body = requireNotNull(response.body)
        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        val input = body.byteStream()
        while (true) { val n = input.read(buffer); if (n < 0) break; require(out.size() + n <= 262144); out.write(buffer, 0, n) }
        return out.toByteArray()
    }
    private fun requireAccount() { require(authentication.currentPilotId() == pilotId) { "Account binding changed; do not continue this local operation" } }
    private fun dateDelay(header: String?): Long {
        if (header == null || header.length > 128) return 0L
        val position = ParsePosition(0)
        val date = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
            isLenient = false; timeZone = TimeZone.getTimeZone("GMT")
        }.parse(header, position) ?: return 0L
        if (position.index != header.length) return 0L
        return (date.time - wallMillis()).coerceAtLeast(0L)
    }
    private fun retry(id: String, status: Int?, retryAfter: String?): Stage1UploadResult.RetryAfter {
        val count = (retries[id]?.count ?: 0).coerceAtMost(18)
        val backoff = (1000L shl count).coerceAtMost(300000L)
        val seconds = retryAfter?.trim()?.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }?.trimStart('0')
        val serverDelay = when {
            seconds == null -> dateDelay(retryAfter)
            seconds.isEmpty() -> 0L
            seconds.length > 19 -> Long.MAX_VALUE
            else -> BigInteger(seconds).multiply(BigInteger.valueOf(1000)).min(BigInteger.valueOf(Long.MAX_VALUE)).toLong()
        }
        val delay = maxOf(backoff, serverDelay)
        val now = elapsedMillis(); val due = if (now > Long.MAX_VALUE - delay) Long.MAX_VALUE else now + delay
        retries[id] = RetryState(count + 1, due)
        if (status == 429 || serverDelay > 0) serverDue = maxOf(serverDue ?: due, due)
        return Stage1UploadResult.RetryAfter(delay, status)
    }
}
