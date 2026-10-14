package com.fuwaki.djifly.platform.attachment

internal data class FpvFramePixels(val data: ByteArray, val width: Int, val height: Int) {
    fun scaled(maxEdge: Int): FpvFramePixels {
        require(maxEdge >= 2)
        if (maxOf(width, height) <= maxEdge) return this
        val scale = maxEdge.toDouble() / maxOf(width, height)
        val w = maxOf(2, (width * scale).toInt() / 2 * 2)
        val h = maxOf(2, (height * scale).toInt() / 2 * 2)
        val output = ByteArray(w * h * 3 / 2)
        for (y in 0 until h) for (x in 0 until w) {
            output[y * w + x] = data[(y.toLong() * height / h).toInt() * width + (x.toLong() * width / w).toInt()]
        }
        for (y in 0 until h / 2) for (x in 0 until w / 2) {
            val source = width * height + (y.toLong() * (height / 2) / (h / 2)).toInt() * width +
                (x.toLong() * (width / 2) / (w / 2)).toInt() * 2
            val target = w * h + y * w + x * 2
            output[target] = data[source]
            output[target + 1] = data[source + 1]
        }
        return FpvFramePixels(output, w, h)
    }

    companion object {
        fun copy(data: ByteArray, offset: Int, length: Int, width: Int, height: Int): FpvFramePixels? {
            if (width < 2 || height < 2 || width % 2 != 0 || height % 2 != 0) return null
            val pixels = width.toLong() * height
            if (pixels > 32L * 1024 * 1024 * 2 / 3) return null
            val required = pixels * 3 / 2
            if (required > 32 * 1024 * 1024 || offset < 0 || length < required ||
                offset.toLong() + length > data.size) return null
            return FpvFramePixels(data.copyOfRange(offset, offset + required.toInt()), width, height)
        }
    }
}
