package com.fuwaki.djifly.platform.attachment

import org.junit.Assert.*
import org.junit.Test

class FpvFramePixelsTest {
    @Test fun copiesOnlyFrameRangeAndOwnsBuffer() {
        val input = byteArrayOf(99, 1, 2, 3, 4, 5, 6, 98)
        val frame = FpvFramePixels.copy(input, 1, 6, 2, 2)!!
        input[1] = 42
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), frame.data)
    }
    @Test fun rejectsTruncatedOddAndOverflowFrames() {
        assertNull(FpvFramePixels.copy(ByteArray(6), 1, 6, 2, 2))
        assertNull(FpvFramePixels.copy(ByteArray(6), 0, 5, 2, 2))
        assertNull(FpvFramePixels.copy(ByteArray(6), 0, 6, 3, 2))
        assertNull(FpvFramePixels.copy(ByteArray(6), 0, 6, Int.MAX_VALUE - 1, Int.MAX_VALUE - 1))
    }
    @Test fun scalesLumaAndPreservesVuPairs() {
        val frame = FpvFramePixels(ByteArray(24) { it.toByte() }, 4, 4).scaled(2)
        assertEquals(2, frame.width)
        assertEquals(2, frame.height)
        assertArrayEquals(byteArrayOf(0, 2, 8, 10, 16, 17), frame.data)
    }
    @Test fun capsLongEdgeWithEvenAspectDimensions() {
        val frame = FpvFramePixels(ByteArray(1920 * 1080 * 3 / 2), 1920, 1080).scaled(1280)
        assertEquals(1280, frame.width)
        assertEquals(720, frame.height)
        assertEquals(1280 * 720 * 3 / 2, frame.data.size)
    }
}
