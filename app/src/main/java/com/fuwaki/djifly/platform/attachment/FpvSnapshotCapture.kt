package com.fuwaki.djifly.platform.attachment

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

/** 持有当前 FPV [View] 引用，供飞行页抓帧（Compose AndroidView 注册） */
class FpvSnapshotTarget {
    var fpvView: View? = null
}

/**
 * 从图传画面截取 JPEG 送达凭证（REQ-DJIFLY-001）。
 * 优先 Window [PixelCopy]（API 26+），否则回退 [View.draw]。
 */
object FpvSnapshotCapture {

    const val JPEG_CONTENT_TYPE = "image/jpeg"
    private const val JPEG_QUALITY = 90

    fun snapshotFileName(taskNum: String?): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val prefix = taskNum?.takeIf { it.isNotBlank() } ?: "delivery"
        return "${prefix}_evidence_$ts.jpg"
    }

    sealed interface CaptureResult {
        data class Ok(val jpegBytes: ByteArray, val fileName: String) : CaptureResult
        data class Failed(val reason: String) : CaptureResult
    }

    suspend fun captureFromFpv(
        target: FpvSnapshotTarget,
        activity: Activity,
        fileName: String
    ): CaptureResult {
        val view = target.fpvView
        if (view == null || view.width <= 0 || view.height <= 0) {
            return CaptureResult.Failed("图传画面未就绪，请稍后再拍摄")
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            capturePixelCopy(view, activity, fileName)
        } else {
            captureLegacy(view, fileName)
        }
    }

    private suspend fun capturePixelCopy(
        view: View,
        activity: Activity,
        fileName: String
    ): CaptureResult = suspendCancellableCoroutine { cont ->
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val loc = IntArray(2)
        view.getLocationInWindow(loc)
        val rect = Rect(loc[0], loc[1], loc[0] + view.width, loc[1] + view.height)
        try {
            PixelCopy.request(
                activity.window,
                rect,
                bitmap,
                { result ->
                    if (result == PixelCopy.SUCCESS) {
                        cont.resume(encodeJpeg(bitmap, fileName))
                    } else {
                        bitmap.recycle()
                        cont.resume(CaptureResult.Failed("截取图传画面失败，请重试"))
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (e: Exception) {
            bitmap.recycle()
            cont.resume(
                CaptureResult.Failed("截取图传画面失败：${e.localizedMessage ?: "未知错误"}")
            )
        }
    }

    private fun captureLegacy(view: View, fileName: String): CaptureResult {
        return try {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            encodeJpeg(bitmap, fileName)
        } catch (_: Exception) {
            CaptureResult.Failed("截取图传画面失败，请重试")
        }
    }

    private fun encodeJpeg(bitmap: Bitmap, fileName: String): CaptureResult {
        return try {
            ByteArrayOutputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                    CaptureResult.Failed("生成送达凭证失败，请重试")
                } else {
                    CaptureResult.Ok(out.toByteArray(), fileName)
                }
            }
        } finally {
            bitmap.recycle()
        }
    }
}
