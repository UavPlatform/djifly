package com.fuwaki.djifly.platform.attachment

import com.fuwaki.djifly.platform.network.AttachmentUploadTicket
import com.fuwaki.djifly.platform.network.PlatformApiService
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import retrofit2.Response
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** 待上传交付物（UI 层从 ContentResolver 构造；openStream 每次调用返回新流，避免整文件驻留内存） */
data class PendingDeliverable(
    val id: String,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val openStream: () -> InputStream?,
    val onDiscard: () -> Unit = {}
)

/**
 * 交付物上传编排（1B-9b，t43 契约）：
 * 本地预检 → 请求 presigned PUT 凭证 → IO 线程直传 → 后端校验确认。
 *
 * 注意：直传必须使用独立 OkHttpClient——业务 client 的拦截器会注入 Authorization 头，
 * 而预签名 URL 的鉴权在查询参数上，携带额外 Authorization 会导致 S3 签名校验失败。
 * 失败一律返回 [DeliverableUploadOutcome.Failed]（附可重试提示），不向全局错误通道发射，
 * 避免多文件串行上传时重复弹全局提示。
 */
@Singleton
class DeliverableUploader @Inject constructor(
    private val api: PlatformApiService
) {

    // 预签名直传专用：无任何拦截器的干净客户端
    private val plainClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun upload(taskNum: String, deliverable: PendingDeliverable): DeliverableUploadOutcome {
        return withContext(Dispatchers.IO) { uploadOnIo(taskNum, deliverable) }
    }

    private suspend fun uploadOnIo(taskNum: String, deliverable: PendingDeliverable): DeliverableUploadOutcome {
        // 1. 本地预检（t43 类型/大小限制）
        when (val check = DeliverableRules.check(deliverable.contentType, deliverable.sizeBytes)) {
            is DeliverableCheck.Rejected -> return DeliverableUploadOutcome.Rejected(check.reason)
            is DeliverableCheck.Ok -> {}
        }

        // 2. 上传凭证（后端校验授权 + 复检限制，登记附件行）
        val ticketResponse: Response<com.fuwaki.djifly.data.models.ApiResult<AttachmentUploadTicket>> = try {
            api.requestAttachmentUploadUrl(
                taskNum = taskNum,
                fileName = deliverable.fileName,
                contentType = deliverable.contentType,
                sizeBytes = deliverable.sizeBytes
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFailure("ticket", taskNum, null, e)
            return DeliverableUploadMapper.networkFailure()
        }

        if (!ticketResponse.isSuccessful) {
            val raw = runCatching { ticketResponse.errorBody()?.string() }.getOrNull()
            return DeliverableUploadMapper.outcomeFromHttpResponse(ticketResponse.code(), raw)
        }

        val ticket: AttachmentUploadTicket = ticketResponse.body()?.takeIf { it.success }?.data
            ?: return DeliverableUploadMapper.ticketMissing()
        val uploadUrl = ticket.uploadUrl
            ?: return DeliverableUploadMapper.ticketMissing()

        val objectKey = ticket.objectKey?.takeIf { it.isNotBlank() }
            ?: return DeliverableUploadMapper.ticketMissing()

        // 3. 存储预签名 PUT（REQ-DJIFLY-001）：Content-Type 与签名一致，不携带业务 Token。
        try {
            val body = object : RequestBody() {
                override fun contentType() = deliverable.contentType.toMediaTypeOrNull()

                override fun contentLength(): Long = deliverable.sizeBytes

                override fun writeTo(sink: BufferedSink) {
                    val input: InputStream = deliverable.openStream()
                        ?: throw IOException("无法读取所选文件")
                    input.use { stream -> sink.writeAll(stream.source()) }
                }
            }
            val destination = uploadUrl.toHttpUrl()
            Log.i("DeliverableUpload", "stage=put task=$taskNum key=$objectKey size=${deliverable.sizeBytes} destination=${destination.scheme}://${destination.host}:${destination.port}${destination.encodedPath}")
            plainClient.newCall(
                Request.Builder().url(uploadUrl).put(body).build()
            ).execute().use { response ->
                Log.i("DeliverableUpload", "stage=put task=$taskNum key=$objectKey http=${response.code}")
                if (!response.isSuccessful) {
                    val raw = runCatching { response.peekBody(8192).string() }.getOrNull()
                    val storageCode = Regex("<Code>([A-Za-z0-9_-]+)</Code>")
                        .find(raw.orEmpty())?.groupValues?.get(1).orEmpty()
                    val requestId = Regex("<RequestId>([A-Za-z0-9_-]+)</RequestId>")
                        .find(raw.orEmpty())?.groupValues?.get(1).orEmpty()
                    Log.e("DeliverableUpload", "stage=put task=$taskNum key=$objectKey http=${response.code} code=$storageCode requestId=$requestId")
                    return DeliverableUploadOutcome.Failed("文件直传失败（HTTP ${response.code} $storageCode），请重试")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFailure("put", taskNum, objectKey, e)
            return DeliverableUploadMapper.putFailure()
        }

        // A successful PUT alone is insufficient; the server must verify the stored object.
        return try {
            val confirmation = api.confirmAttachmentUpload(taskNum, objectKey)
            Log.i("DeliverableUpload", "stage=confirm task=$taskNum key=$objectKey http=${confirmation.code()} success=${confirmation.body()?.success}")
            if (confirmation.isSuccessful && confirmation.body()?.success == true &&
                confirmation.body()?.data?.get("uploadStatus") == "UPLOADED" &&
                confirmation.body()?.data?.get("objectKey") == objectKey) {
                DeliverableUploadOutcome.Uploaded(objectKey, deliverable.fileName)
            } else {
                Log.e("DeliverableUpload", "stage=confirm task=$taskNum key=$objectKey http=${confirmation.code()}")
                DeliverableUploadOutcome.Failed("文件上传确认失败，请重试")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFailure("confirm", taskNum, objectKey, e)
            DeliverableUploadOutcome.Failed("文件上传确认失败，请重试")
        }
    }

    private fun logFailure(stage: String, taskNum: String, key: String?, error: Exception) {
        // Do not log exception messages: networking exceptions may contain signed URLs.
        Log.e("DeliverableUpload", "stage=$stage task=$taskNum key=$key exception=${error.javaClass.name}")
        error.stackTrace.forEach { Log.e("DeliverableUpload", "at $it") }
        error.cause?.let { cause ->
            Log.e("DeliverableUpload", "cause=${cause.javaClass.name}")
            cause.stackTrace.forEach { Log.e("DeliverableUpload", "at $it") }
        }
    }
}
