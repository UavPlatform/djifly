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
    data class Durable(
        val progress: Stage1Progress?,
        val chainConflict: Boolean = false,
        /** True when the fixed receipt was committed but the dynamic progress block could not be trusted. */
        val progressInvalid: Boolean = false
    ) : Stage1UploadResult()
    object AlreadyDurable : Stage1UploadResult()
    data class RetryAfter(val delayMillis: Long, val status: Int?) : Stage1UploadResult()
    data class NotDue(val delayMillis: Long) : Stage1UploadResult()
    object AuthenticationRequired : Stage1UploadResult()
    /** Server refused this packet. Bytes are kept; resend only after an explicit [Stage1Uploader.clearIntervention]. */
    data class HttpRejected(val status: Int) : Stage1UploadResult()
    /** The remote answered, but not with a complete fixed receipt. Local intervention, never a blind resend loop. */
    data class InvalidResponse(val detail: String) : Stage1UploadResult()
    /**
     * This packet is parked by an earlier attempt (HTTP rejection or unusable confirmation). Nothing is resent until
     * the caller performs the explicit action: [Stage1Uploader.clearIntervention]. Restarting the process is not a reset.
     */
    data class InterventionRequired(val status: Int?, val errorCode: String?, val reason: String) : Stage1UploadResult()
    /** Chain-wide pause. Lifted only by an explicit [Stage1Uploader.clearChainFork] after out-of-band resolution. */
    object ChainForked : Stage1UploadResult()
}

/**
 * Explicit-opt-in blocking adapter; call off main thread. No worker, DI, real endpoint default, SDK or production activation.
 * One bounded attempt per invocation. Durable bytes/receipts survive restart; retry deadlines, progress and intervention
 * state are process-local and must be rebuilt by the next phase, never inferred from an empty pending list.
 *
 * Concurrency: [state] guards only local bookkeeping. Network I/O, response bodies and auth recovery run outside it, so a
 * slow in-flight request never blocks [latestProgress], [enqueue] or other packets and chains. Repeat attempts for the same
 * packet are serialized by a per-packet gate, so one packet is never sent twice concurrently.
 */
@ExperimentalStage1Api
class Stage1Uploader(baseUrl: HttpUrl, client: OkHttpClient, private val store: EventPacketStore,
    private val pilotId: Long, private val authentication: Stage1Authentication,
    private val wallMillis: () -> Long = { System.currentTimeMillis() },
    private val elapsedMillis: () -> Long = { System.nanoTime() / 1000000 }) {
    private companion object {
        /** Retry-After is honored up to this single retry bound; a larger value is clamped, never an infinite pause. */
        const val MAX_RETRY_DELAY_MS = 3_600_000L
    }

    private val codec = Stage1Codec()
    private val base = baseUrl.also {
        require(it.encodedPath.endsWith("/") && it.query == null && it.fragment == null && it.username.isEmpty() && it.password.isEmpty())
        require(it.isHttps || it.host in setOf("localhost", "127.0.0.1", "::1")) { "HTTPS required outside local HTTP tests" }
    }
    private val http = client.newBuilder().authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE)
        .callTimeout(if (client.callTimeoutMillis == 0) 30000L else minOf(client.callTimeoutMillis.toLong(), 30000L), java.util.concurrent.TimeUnit.MILLISECONDS)
        .cookieJar(CookieJar.NO_COOKIES).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()

    private data class Send(val packetId: String, val chainId: String, val url: HttpUrl, val token: String,
        val body: ByteArray, val expected: ExpectedPacket)
    private sealed class Prepared {
        data class Ready(val send: Send) : Prepared()
        data class Done(val result: Stage1UploadResult) : Prepared()
    }
    private sealed class Remote {
        data class Unauthorized(val code: Int) : Remote()
        data class Retryable(val code: Int, val retryAfter: String?) : Remote()
        data class Rejected(val code: Int, val errorCode: String?) : Remote()
        /** 2xx but the body could not be read within the bounded limit: a local intervention problem, not a retry. */
        data class Unusable(val reason: String) : Remote()
        data class Success(val bytes: ByteArray) : Remote()
    }
    private data class RetryState(val count: Int, val due: Long)
    private data class Parked(val status: Int?, val errorCode: String?, val reason: String)

    /** Guards every mutable field below; never held across network I/O, body reads or auth callbacks. */
    private val state = Any()
    private val retries = HashMap<String, RetryState>()
    private val parked = HashMap<String, Parked>()
    private val forkedChains = HashSet<String>()
    private val progressByChain = HashMap<String, Stage1Progress>()
    private var authenticationBlocked = false
    private var serverDue: Long? = null

    /** Per-packet serialization gate, shared by concurrent attempts for the same packet only. */
    private class Gate { var users = 0 }
    private val gates = HashMap<String, Gate>()

    init {
        require(pilotId > 0); Stage1Json.uuid(store.recordingSourceId)
        require(client.interceptors.isEmpty() && client.networkInterceptors.isEmpty()) { "Use a dedicated client without body/auth-mutating interceptors" }
    }

    // Collection must not wait for the uploader lock while a network response is outstanding.
    // Journal transactions plus appendGuarded CAS protect attribution across concurrent writers.
    // Several airframe chains may share one operation: per-chain identity is proven by the anchor/chainHash inside the
    // packet and re-checked by the server (CHAIN_IDENTITY). This adapter pins order/assignment/source for the operation.
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
    fun authenticationChanged() {
        requireAccount(); require(!authentication.bearerToken().isNullOrBlank())
        synchronized(state) { authenticationBlocked = false }
    }

    fun latestProgress(chainId: String): Stage1Progress? = synchronized(state) { progressByChain[chainId] }

    /** Explicit action after investigating [Stage1UploadResult.InterventionRequired]. Never called automatically. */
    fun clearIntervention(packetId: String) {
        synchronized(state) { parked.remove(packetId); retries.remove(packetId) }
    }

    /** Explicit action once a fork is resolved out of band (for example by real reconciliation). Never automatic. */
    fun clearChainFork(chainId: String) {
        synchronized(state) { forkedChains.remove(chainId) }
    }

    fun attempt(operationId: String, packetId: String): Stage1UploadResult {
        val key = operationId + '/' + packetId
        val gate = enter(key)
        return synchronized(gate) {
            try {
                runAttempt(operationId, packetId)
            } finally {
                release(key, gate)
            }
        }
    }

    private fun runAttempt(operationId: String, packetId: String): Stage1UploadResult {
        val prepared = prepare(operationId, packetId)
        if (prepared is Prepared.Done) return prepared.result
        val send = (prepared as Prepared.Ready).send
        val request = Request.Builder().url(send.url).header("Authorization", "Bearer ${send.token}")
            .post(OriginalAttemptBody(send.body)).build()
        // Network work happens with no uploader lock held.
        val remote = try {
            http.newCall(request).execute().use { classify(it) }
        } catch (_: IOException) {
            return synchronized(state) { requireAccount(); retry(send.packetId, null, null) }
        }
        return complete(send, remote)
    }

    /** Local state only; store reads are unlocked (the store serializes itself). Returns what to send or a final result. */
    private fun prepare(operationId: String, packetId: String): Prepared {
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
            return Prepared.Done(Stage1UploadResult.AlreadyDurable)
        }
        require(row.body != null) { "Unconfirmed original bytes missing" }
        val token = authentication.bearerToken(); requireAccount()
        return synchronized(state) {
            if (expected.link.identity.chainId in forkedChains) return@synchronized Prepared.Done(Stage1UploadResult.ChainForked)
            parked[packetId]?.let { return@synchronized Prepared.Done(Stage1UploadResult.InterventionRequired(it.status, it.errorCode, it.reason)) }
            if (authenticationBlocked || token.isNullOrBlank()) return@synchronized Prepared.Done(Stage1UploadResult.AuthenticationRequired)
            val now = elapsedMillis()
            // A server-wide pause covers every chain: the backend rate limit is per endpoint, so honoring it only for
            // the rejected packet would keep hitting a closed gate.
            serverDue?.let { if (now < it) return@synchronized Prepared.Done(Stage1UploadResult.NotDue(it - now)) }
            retries[packetId]?.let { if (now < it.due) return@synchronized Prepared.Done(Stage1UploadResult.NotDue(it.due - now)) }
            val path = ApiPaths.DISPATCH_PILOT_ORDERS_EVENT_PACKETS.replace("{orderId}", expected.link.identity.orderId)
            Prepared.Ready(Send(packetId, expected.link.identity.chainId, base.newBuilder().addPathSegments(path).build(),
                requireNotNull(token), row.body, expected))
        }
    }

    /** Reads only what classification needs. Bounded body reads happen here, still unlocked. */
    private fun classify(response: Response): Remote {
        val code = response.code
        if (code == 401) return Remote.Unauthorized(code)
        if (code in setOf(408, 429) || code in 500..599) return Remote.Retryable(code, response.header("Retry-After"))
        if (!response.isSuccessful) return Remote.Rejected(code, errorCode(response))
        // Bodies are bounded before parsing. An oversized body is a broken deployment, not a transient fault:
        // retrying it would spin forever. IOException stays retryable because the connection broke mid-body.
        val body = try { readBody(response) } catch (e: IOException) { return Remote.Retryable(code, null) }
            catch (e: RuntimeException) { return Remote.Unusable(e.message ?: "Unusable success body") }
        return Remote.Success(body)
    }

    private fun complete(send: Send, remote: Remote): Stage1UploadResult = when (remote) {
        is Remote.Unauthorized -> {
            val recovered = try { authentication.recover() } catch (_: IOException) { false } catch (_: RuntimeException) { false }
            requireAccount() // Never release a former account's body after a login change while the request was in flight.
            synchronized(state) {
                authenticationBlocked = !recovered || authentication.bearerToken().isNullOrBlank()
                retry(send.packetId, remote.code, null)
            }
        }
        is Remote.Retryable -> synchronized(state) { requireAccount(); retry(send.packetId, remote.code, remote.retryAfter) }
        is Remote.Rejected -> synchronized(state) { requireAccount(); reject(send, remote.code, remote.errorCode) }
        is Remote.Unusable -> synchronized(state) { requireAccount(); park(send, null, null, remote.reason) }
        is Remote.Success -> applySuccess(send, remote.bytes)
    }

    private fun applySuccess(send: Send, bytes: ByteArray): Stage1UploadResult {
        // A 2xx body that is unreadable, oversized or not a complete fixed receipt is a local intervention problem,
        // not a transport one: retrying it would loop forever on a captive portal or a broken deployment.
        val ack = try { Stage1Receipt.acknowledgement(bytes, send.expected) }
            catch (e: Exception) { return synchronized(state) { requireAccount(); park(send, null, null, e.message ?: "Malformed confirmation") } }
        // Do not misclassify a local transaction failure as an invalid remote receipt. SQL failures
        // propagate to the caller; original bytes remain, or an already committed receipt recovers on the next attempt.
        store.acknowledgeDurable(send.packetId, ack.fixedReceipt)
        store.releaseBody(send.packetId)
        synchronized(state) {
            retries.remove(send.packetId)
            if (ack.chainConflict) forkedChains.add(send.chainId)
            ack.progress?.let { progress ->
                val old = progressByChain[send.chainId]
                if (old == null || progress.validationVersion > old.validationVersion) progressByChain[send.chainId] = progress
                if (progress.integrity == "FORKED") forkedChains.add(send.chainId)
            }
            return Stage1UploadResult.Durable(ack.progress, ack.chainConflict, ack.progressInvalid)
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

    private fun errorCode(response: Response): String? = try {
        Stage1Json.nullableText(Stage1Json.obj(Stage1Json.parse(readBody(response)))["errorCode"])
    } catch (_: Exception) { null }

    /** Caller holds [state]. */
    private fun reject(send: Send, status: Int, errorCode: String?): Stage1UploadResult {
        // Only a real chain conflict pauses the chain. Other 409s (for example DISPATCH_ASSIGNMENT_CHANGED) park this
        // packet alone, so a changed assignment cannot silently freeze unrelated chains in the same operation.
        if (status == 409 && errorCode == "EVENT_PACKET_CONFLICT") forkedChains.add(send.chainId)
        return park(send, status, errorCode, "HTTP $status" + (errorCode?.let { " $it" } ?: ""))
    }

    /** Caller holds [state]. Parks one packet and reports the matching public result. */
    private fun park(send: Send, status: Int?, errorCode: String?, reason: String): Stage1UploadResult {
        parked[send.packetId] = Parked(status, errorCode, reason)
        return if (status == null) Stage1UploadResult.InvalidResponse(reason) else Stage1UploadResult.HttpRejected(status)
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

    /** Caller holds [state]. */
    private fun retry(id: String, status: Int?, retryAfter: String?): Stage1UploadResult.RetryAfter {
        val count = (retries[id]?.count ?: 0).coerceAtMost(18)
        val backoff = (1000L shl count).coerceAtMost(300000L)
        val seconds = retryAfter?.trim()?.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }?.trimStart('0')
        val serverDelay = when {
            seconds == null -> dateDelay(retryAfter)
            seconds.isEmpty() -> 0L
            seconds.length > 19 -> MAX_RETRY_DELAY_MS
            else -> BigInteger(seconds).multiply(BigInteger.valueOf(1000))
                .min(BigInteger.valueOf(MAX_RETRY_DELAY_MS)).toLong()
        }
        val delay = maxOf(backoff, serverDelay)
        val now = elapsedMillis(); val due = now + delay
        retries[id] = RetryState(count + 1, due)
        if (status == 429 || serverDelay > 0) serverDue = maxOf(serverDue ?: due, due)
        return Stage1UploadResult.RetryAfter(delay, status)
    }

    private fun enter(key: String): Gate = synchronized(state) { gates.getOrPut(key) { Gate() }.also { it.users++ } }

    private fun release(key: String, gate: Gate) { synchronized(state) { if (--gate.users == 0) gates.remove(key) } }
}
