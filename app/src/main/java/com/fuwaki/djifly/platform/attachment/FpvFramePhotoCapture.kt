package com.fuwaki.djifly.platform.attachment

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.manager.datacenter.MediaDataCenter
import dji.v5.manager.interfaces.ICameraStreamManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Fresh decoded FPV evidence (REQ-DJIFLY-001); never copies the Android window. */
class FpvFramePhotoCapture(context: Context) {
    private val directory = File(context.applicationContext.cacheDir, "delivery-frames")
    private val mutableProgress = MutableStateFlow("")
    val progress: StateFlow<String> = mutableProgress

    suspend fun capture(taskNum: String): FpvPhotoResult = captureMutex.withLock {
        withContext(Dispatchers.IO) {
            var file: File? = null
            try {
                mutableProgress.value = "正在获取图传画面…"
                val frame = awaitFrame()
                mutableProgress.value = "正在压缩图传照片…"
                val jpeg = encode(frame)
                check(directory.isDirectory || directory.mkdirs()) { "无法创建凭证缓存目录" }
                val task = taskNum.replace(Regex("[^A-Za-z0-9_-]"), "_").take(80)
                file = File(directory, "${task}_fpv_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
                file.writeBytes(jpeg)
                Log.i(TAG, "stage=ready source=${frame.width}x${frame.height} size=${jpeg.size}")
                FpvPhotoResult.Ok(file, file.name)
            } catch (e: TimeoutCancellationException) {
                file?.delete()
                Log.w(TAG, "stage=timeout", e)
                FpvPhotoResult.Failed("未收到图传画面，请连接无人机并确认图传正常后重试")
            } catch (e: CancellationException) {
                file?.delete()
                throw e
            } catch (e: Exception) {
                file?.delete()
                Log.e(TAG, "stage=failed", e)
                FpvPhotoResult.Failed("图传照片获取失败：${e.message ?: e.javaClass.simpleName}")
            } finally {
                mutableProgress.value = ""
            }
        }
    }

    private suspend fun awaitFrame(): FpvFramePixels {
        val manager = MediaDataCenter.getInstance().cameraStreamManager
        val result = CompletableDeferred<FpvFramePixels>()
        val claimed = AtomicBoolean(false)
        val listener = object : ICameraStreamManager.CameraFrameListener {
            override fun onFrame(data: ByteArray, offset: Int, length: Int, width: Int, height: Int,
                                 format: ICameraStreamManager.FrameFormat) {
                if (format != ICameraStreamManager.FrameFormat.NV21 || result.isCompleted ||
                    !claimed.compareAndSet(false, true)) return
                val frame = FpvFramePixels.copy(data, offset, length, width, height)
                if (frame == null) claimed.set(false) else result.complete(frame)
            }
        }
        try {
            manager.addFrameListener(ComponentIndexType.LEFT_OR_MAIN, ICameraStreamManager.FrameFormat.NV21, listener)
            return withTimeout(15_000) { result.await() }
        } finally {
            result.cancel()
            manager.removeFrameListener(listener)
        }
    }

    private fun encode(source: FpvFramePixels): ByteArray {
        var frame = source.scaled(1280)
        while (true) {
            for (quality in intArrayOf(80, 65, 50)) {
                val output = ByteArrayOutputStream()
                check(YuvImage(frame.data, ImageFormat.NV21, frame.width, frame.height, null)
                    .compressToJpeg(Rect(0, 0, frame.width, frame.height), quality, output)) { "JPEG 压缩失败" }
                val bytes = output.toByteArray()
                if (bytes.size <= MAX_BYTES) {
                    Log.i(TAG, "stage=encoded dimensions=${frame.width}x${frame.height} quality=$quality size=${bytes.size}")
                    return bytes
                }
            }
            val edge = maxOf(frame.width, frame.height)
            check(edge > 320) { "图传照片超过 512 KB，请重试" }
            frame = frame.scaled(maxOf(320, edge * 3 / 4))
        }
    }

    companion object {
        private const val TAG = "FpvFrameCapture"
        private const val MAX_BYTES = 512 * 1024
        private val captureMutex = Mutex()
    }
}
