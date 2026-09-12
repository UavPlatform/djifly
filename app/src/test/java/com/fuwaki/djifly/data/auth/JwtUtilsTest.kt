package com.fuwaki.djifly.data.auth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * JwtUtils（本地 exp 预检）纯单元测试。
 * 用于 WS 鉴权失败分类：403 且本地已过期 → 先 refresh 再重试；未过期 → 直接长退避。
 * 测试侧用 java.util.Base64 编码构造 JWT fixture（主源码用手写 base64url 解码器以兼容 minSdk 24）。
 */
class JwtUtilsTest {

    private val fixedNowMillis = 1_700_000_000_000L          // 2023-11-14T22:13:20Z
    private val expValidSeconds = fixedNowMillis / 1000 + 60  // 一分钟后过期
    private val expExpiredSeconds = fixedNowMillis / 1000 - 10

    private fun jwt(payloadJson: String): String {
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payloadJson.toByteArray(Charsets.UTF_8))
        return "header.$payload.signature"
    }

    @Test
    fun unexpired_token_is_not_expired() {
        val token = jwt("""{"exp":$expValidSeconds}""")
        assertFalse(JwtUtils.isJwtExpired(token, nowMillis = fixedNowMillis))
        assertEquals(expValidSeconds, JwtUtils.parseJwtExpSeconds(token))
    }

    @Test
    fun expired_token_is_detected() {
        val token = jwt("""{"exp":$expExpiredSeconds}""")
        assertTrue(JwtUtils.isJwtExpired(token, nowMillis = fixedNowMillis))
        assertEquals(expExpiredSeconds, JwtUtils.parseJwtExpSeconds(token))
    }

    @Test
    fun blank_token_counts_as_expired() {
        assertTrue(JwtUtils.isJwtExpired(null, nowMillis = fixedNowMillis))
        assertTrue(JwtUtils.isJwtExpired("", nowMillis = fixedNowMillis))
        assertTrue(JwtUtils.isJwtExpired("   ", nowMillis = fixedNowMillis))
    }

    @Test
    fun non_jwt_or_missing_exp_is_not_treated_as_expired() {
        // 非 JWT / 无 exp：无法证明过期 → 返回 false（不触发无谓刷新），由 403 长退避兜底
        assertFalse(JwtUtils.isJwtExpired("not-a-jwt", nowMillis = fixedNowMillis))
        assertFalse(JwtUtils.isJwtExpired("only.two.parts.missing.exp", nowMillis = fixedNowMillis))
        assertNull(JwtUtils.parseJwtExpSeconds("not-a-jwt"))
        assertNull(JwtUtils.parseJwtExpSeconds(jwt("""{"sub":"no-exp"}""")))
    }

    @Test
    fun base64url_decoder_handles_url_safe_alphabet_and_padding() {
        assertArrayEquals(byteArrayOf(1, 2, 3), JwtUtils.decodeBase64Url("AQID"))
        assertArrayEquals(byteArrayOf(0xFB.toByte(), 0xFF.toByte()), JwtUtils.decodeBase64Url("-_8"))
        // 标准 base64 字符与 padding 也容忍
        assertArrayEquals(byteArrayOf(0xFB.toByte(), 0xFF.toByte()), JwtUtils.decodeBase64Url("+/8="))
    }

    @Test
    fun base64url_decoder_round_trips_fixtures() {
        val fixtures = listOf(
            byteArrayOf(0),
            byteArrayOf(
                0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(),
                0xFD.toByte(), 0xFE.toByte(), 0xFF.toByte()
            ),
            "中文payload".toByteArray(Charsets.UTF_8)
        )
        for (fixture in fixtures) {
            val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(fixture)
            assertArrayEquals(fixture, JwtUtils.decodeBase64Url(encoded))
        }
    }

    @Test
    fun decoder_rejects_illegal_characters() {
        assertNull(JwtUtils.decodeBase64Url(""))
        assertNull(JwtUtils.decodeBase64Url("a*b"))
    }
}
