package com.fuwaki.djifly.platform.attachment

import com.fuwaki.djifly.platform.network.AttachmentUploadTicket
import com.fuwaki.djifly.platform.network.PlatformApiService
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
    val openStream: () -> InputStream?
)

/**
 * 交付物上传编排（1B-9b，t43 契约）：
 * 本地预检 → 请求 presigned PUT 凭证 → OkHttp 直传 MinIO。
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
        } catch (e: Exception) {
            return DeliverableUploadMapper.networkFailure()
        }

        if (!ticketResponse.isSuccessful) {
            val raw = runCatching { ticketResponse.errorBody()?.string() }.getOrNull()
            return DeliverableUploadMapper.outcomeFromHttpResponse(ticketResponse.code(), raw)
        }

        val ticket: AttachmentUploadTicket = ticketResponse.body()?.data
            ?: return DeliverableUploadMapper.ticketMissing()
        val uploadUrl = ticket.uploadUrl
            ?: return DeliverableUploadMapper.ticketMissing()

        // 3. presigned PUT 直传（流式写入；预签名不签 Content-Type，无需特殊头）
        return try {
            val body = object : RequestBody() {
                override fun contentType() = "application/octet-stream".toMediaTypeOrNull()

                override fun contentLength(): Long = deliverable.sizeBytes

                override fun writeTo(sink: BufferedSink) {
                    val input: InputStream = deliverable.openStream()
                        ?: throw IOException("无法读取所选文件")
                    input.use { stream -> sink.writeAll(stream.source()) }
                }
            }
            plainClient.newCall(
                Request.Builder().url(uploadUrl).put(body).build()
            ).execute().use { response ->
                if (response.isSuccessful) {
                    DeliverableUploadOutcome.Uploaded(
                        objectKey = ticket.objectKey ?: "",
                        fileName = deliverable.fileName
                    )
                } else {
                    val raw = runCatching { response.body?.string() }.getOrNull()
                    DeliverableUploadOutcome.Failed(
                        DeliverableUploadMapper.outcomeFromHttpResponse(response.code, raw).reason
                    )
                }
            }
        } catch (e: Exception) {
            DeliverableUploadMapper.putFailure()
        }
    }
}
