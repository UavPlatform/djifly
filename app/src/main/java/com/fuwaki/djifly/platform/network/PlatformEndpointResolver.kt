package com.fuwaki.djifly.platform.network

import com.fuwaki.djifly.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlatformEndpointResolver @Inject constructor() {

    fun requireHttpBaseUrl(): String {
        val configuredBaseUrl = BuildConfig.DRONE_BACKEND_BASE_URL.trim()
        if (configuredBaseUrl.isBlank()) {
            throw IllegalStateException("未配置 DRONE_BACKEND_BASE_URL，无法向平台注册无人机")
        }

        val normalized = normalizeConfiguredBaseUrlForHttp(configuredBaseUrl)

        val httpUrl = normalized.toHttpUrlOrNull()
            ?: throw IllegalStateException("DRONE_BACKEND_BASE_URL 不是合法的 HTTP 地址: $configuredBaseUrl")

        val scheme = httpUrl.scheme.lowercase(Locale.US)
        if (scheme != "http" && scheme != "https") {
            throw IllegalStateException("DRONE_BACKEND_BASE_URL 必须使用 HTTP 或 HTTPS: $configuredBaseUrl")
        }

        return httpUrl.toString()
    }

    fun resolveDroneWebSocketUrl(deviceId: String): String {
        val httpUrl = requireHttpBaseUrl().toHttpUrlOrNull()
            ?: throw IllegalStateException("无法解析平台 HTTP 地址")

        val basePath = httpUrl.encodedPath.trimEnd('/')
        val wsPath = if (basePath.isBlank() || basePath == "/") {
            "/ws/drone"
        } else {
            "$basePath/ws/drone"
        }

        return httpUrl.newBuilder()
            .encodedPath(wsPath)
            .query(null)
            .addQueryParameter("deviceId", deviceId)
            .build()
            .toString()
            .toWebSocketUrl()
    }

    fun resolveChatWebSocketUrl(userId: Long): String {
        val httpUrl = requireHttpBaseUrl().toHttpUrlOrNull()
            ?: throw IllegalStateException("无法解析平台 HTTP 地址")

        val basePath = httpUrl.encodedPath.trimEnd('/')
        val wsPath = if (basePath.isBlank() || basePath == "/") {
            "/ws/$userId"
        } else {
            "$basePath/ws/$userId"
        }

        return httpUrl.newBuilder()
            .encodedPath(wsPath)
            .query(null)
            .build()
            .toString()
            .toWebSocketUrl()
    }

    private fun normalizeConfiguredBaseUrlForHttp(configuredBaseUrl: String): String {
        val httpBaseUrl = when {
            configuredBaseUrl.startsWith("ws://", ignoreCase = true) ->
                "http://${configuredBaseUrl.substringAfter("://")}"
            configuredBaseUrl.startsWith("wss://", ignoreCase = true) ->
                "https://${configuredBaseUrl.substringAfter("://")}"
            else -> configuredBaseUrl
        }

        return if (httpBaseUrl.endsWith('/')) httpBaseUrl else "$httpBaseUrl/"
    }

    private fun String.toWebSocketUrl(): String {
        return when {
            startsWith("https://", ignoreCase = true) -> "wss://${substringAfter("://")}"
            startsWith("http://", ignoreCase = true) -> "ws://${substringAfter("://")}"
            else -> this
        }
    }
}
