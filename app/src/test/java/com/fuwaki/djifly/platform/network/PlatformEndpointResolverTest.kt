package com.fuwaki.djifly.platform.network

import com.fuwaki.djifly.BuildConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 平台 WS 地址构造纯单元测试（QA-D1 P0：network 请求构造/WS URL 构造）。
 *
 * PlatformWsClient（遥测会话通道）的握手地址由
 * PlatformEndpointResolver 从 DRONE_BACKEND_BASE_URL 推导：http(s)→ws(s) 协议转换、
 * base 路径前缀保留（如 /api）、deviceId 查询参数拼接与编码。
 * 期望值一律从 BuildConfig 配置推导，配置变更（host/path/协议）不会破坏测试有效性。
 */
class PlatformEndpointResolverTest {

    private val resolver = PlatformEndpointResolver()

    /** 配置基址的期望 ws 方案：https→wss，http→ws */
    private val expectedWsScheme =
        if (BuildConfig.DRONE_BACKEND_BASE_URL.trim().startsWith("https", ignoreCase = true)) {
            "wss"
        } else {
            "ws"
        }

    private fun parse(url: String): HttpUrl =
        url.toHttpUrlOrNull().also { assertNotNull("构造出的地址必须是合法 URL: $url", it) }!!

    /**
     * OkHttp HttpUrl 仅接受 http/https，无法直接解析 ws(s)://。
     * 先断言 ws 方案，再把方案折回 http(s) 做结构断言。
     */
    private fun parseWsUrl(url: String): HttpUrl {
        assertTrue("应为 ws/wss URL: $url", url.startsWith("ws://") || url.startsWith("wss://"))
        return url
            .replaceFirst("ws://", "http://", ignoreCase = true)
            .replaceFirst("wss://", "https://", ignoreCase = true)
            .toHttpUrlOrNull()
            .also { assertNotNull("ws URL 折回 http 后必须可解析: $url", it) }!!
    }

    @Test
    fun drone_ws_url_converts_scheme_keeps_base_path_and_appends_device_query() {
        val url = resolver.resolveDroneWebSocketUrl("SN-001")
        val parsed = parseWsUrl(url)

        assertEquals(expectedWsScheme, url.substringBefore(':'))

        // base 路径前缀（如 /api）必须保留，且精确拼接 /ws/drone
        val basePath = parsed.encodedPath.trimEnd('/').removeSuffix("/ws/drone")
        val configuredBasePath = BuildConfig.DRONE_BACKEND_BASE_URL.toHttpUrlOrNull()!!
            .encodedPath.trimEnd('/')
        assertEquals(configuredBasePath, basePath)
        assertTrue("路径应以 /ws/drone 结尾: ${parsed.encodedPath}", parsed.encodedPath.endsWith("/ws/drone"))

        assertEquals("SN-001", parsed.queryParameter("deviceId"))
    }

    @Test
    fun drone_ws_url_encodes_special_characters_in_device_id() {
        val deviceId = "SN 1&2=3"
        val url = resolver.resolveDroneWebSocketUrl(deviceId)

        // URL 字符串中不允许出现未编码的特殊字符
        assertTrue("URL 不应包含未编码空格: $url", !url.contains(' '))

        // 查询参数读取时自动解码回原始值，保证服务端拿到原 deviceId
        val parsed = parseWsUrl(url)
        assertEquals(deviceId, parsed.queryParameter("deviceId"))
    }

    @Test
    fun http_base_url_passes_through_require_http_base_url() {
        // 配置为合法 http(s) 地址时应原样规范化返回（保留尾部斜杠）
        val base = resolver.requireHttpBaseUrl()

        assertTrue(base.startsWith("http"))
        val parsed = base.toHttpUrlOrNull()
        assertNotNull(parsed)
        assertEquals(
            BuildConfig.DRONE_BACKEND_BASE_URL.toHttpUrlOrNull()!!.host,
            parsed!!.host
        )
    }
}
