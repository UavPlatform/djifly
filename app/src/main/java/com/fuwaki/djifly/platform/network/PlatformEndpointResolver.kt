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

        val normalized = if (configuredBaseUrl.endsWith('/')) {
            configuredBaseUrl
        } else {
            "$configuredBaseUrl/"
        }

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
    }
}
