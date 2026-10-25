package com.fuwaki.djifly.platform.attachment

import android.util.Log
import com.fuwaki.djifly.platform.network.PlatformApiService
import com.fuwaki.djifly.platform.network.generated.ApiDispatchMaterialUploadDTO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** 待上传交付物（UI 层从 ContentResolver 构造；openStream 每次调用返回新流） */
data class PendingDeliverable(
    val id: String,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val openStream: () -> InputStream?,
    val onDiscard: () -> Unit = {}
)

/**
 * 派单物料上传：本地预检 → upload-ticket → 预签名 PUT → confirm。
 * 直传使用独立 OkHttpClient，避免业务 Token 破坏预签名。
 */
@Singleton
class DeliverableUploader @Inject constructor(
    private val api: PlatformApiService
) {
    private val plainClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun upload(orderId: String, deliverable: PendingDeliverable): DeliverableUploadOutcome {
        return withContext(Dispatchers.IO) { uploadOnIo(orderId, deliverable) }
    }

    private suspend fun uploadOnIo(orderId: String, deliverable: PendingDeliverable): DeliverableUploadOutcome {
        when (val check = DeliverableRules.check(deliverable.contentType, deliverable.sizeBytes)) {
            is DeliverableCheck.Rejected -> return DeliverableUploadOutcome.Rejected(check.reason)
            is DeliverableCheck.Ok -> {}
        }

        val ticketResponse = try {
            api.requestMaterialUploadTicket(
                orderId = orderId,
                body = ApiDispatchMaterialUploadDTO(
                    contentType = deliverable.contentType,
                    fileName = deliverable.fileName,
                    purpose = "DELIVERY_PROOF",
                    sizeBytes = deliverable.sizeBytes
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFailure("ticket", orderId, null, e)
            return DeliverableUploadMapper.networkFailure()
        }

        if (!ticketResponse.isSuccessful) {
            val raw = runCatching { ticketResponse.errorBody()?.string() }.getOrNull()
            return DeliverableUploadMapper.outcomeFromHttpResponse(ticketResponse.code(), raw)
        }

        val ticket = ticketResponse.body()?.takeIf { it.success == true }?.data
            ?: return DeliverableUploadMapper.ticketMissing()
        val uploadUrl = ticket.uploadUrl ?: return DeliverableUploadMapper.ticketMissing()
        val materialId = ticket.materialId?.takeIf { it.isNotBlank() }
            ?: return DeliverableUploadMapper.ticketMissing()

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
            Log.i(
                "DeliverableUpload",
                "stage=put order=$orderId material=$materialId size=${deliverable.sizeBytes} destination=${destination.scheme}://${destination.host}:${destination.port}${destination.encodedPath}"
            )
            plainClient.newCall(Request.Builder().url(uploadUrl).put(body).build()).execute().use { response ->
                Log.i("DeliverableUpload", "stage=put order=$orderId material=$materialId http=${response.code}")
                if (!response.isSuccessful) {
                    return DeliverableUploadOutcome.Failed("文件直传失败（HTTP ${response.code}），请重试")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFailure("put", orderId, materialId, e)
            return DeliverableUploadMapper.putFailure()
        }

        return try {
            val confirmation = api.confirmMaterialUpload(orderId, materialId)
            Log.i(
                "DeliverableUpload",
                "stage=confirm order=$orderId material=$materialId http=${confirmation.code()} success=${confirmation.body()?.success}"
            )
            if (confirmation.isSuccessful && confirmation.body()?.success == true &&
                confirmation.body()?.data?.materialId == materialId
            ) {
                DeliverableUploadOutcome.Uploaded(materialId, deliverable.fileName)
            } else {
                DeliverableUploadOutcome.Failed("文件上传确认失败，请重试")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFailure("confirm", orderId, materialId, e)
            DeliverableUploadOutcome.Failed("文件上传确认失败，请重试")
        }
    }

    private fun logFailure(stage: String, orderId: String, key: String?, error: Exception) {
        Log.e("DeliverableUpload", "stage=$stage order=$orderId key=$key exception=${error.javaClass.name}")
    }
}
