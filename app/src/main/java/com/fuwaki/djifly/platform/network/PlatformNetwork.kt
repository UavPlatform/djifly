package com.fuwaki.djifly.platform.network

import com.fuwaki.djifly.BuildConfig
import com.google.gson.Gson
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object PlatformNetwork {

    private val gson = Gson()

    internal fun createApiService(): PlatformApiService {
        val baseUrl = resolveBaseUrl()
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(PlatformHeadersInterceptor())
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.BASIC
                    }
                }
            )
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(PlatformApiService::class.java)
    }

    fun gson(): Gson = gson

    private fun resolveBaseUrl(): String {
        val configuredBaseUrl = BuildConfig.DRONE_BACKEND_BASE_URL.trim()
        if (configuredBaseUrl.isBlank()) {
            throw IllegalStateException("未配置 DRONE_BACKEND_BASE_URL，无法向平台注册无人机")
        }

        val normalized = if (configuredBaseUrl.endsWith('/')) {
            configuredBaseUrl
        } else {
            "$configuredBaseUrl/"
        }

        return normalized.toHttpUrlOrNull()?.toString()
            ?: throw IllegalStateException("DRONE_BACKEND_BASE_URL 不是合法的 HTTP 地址: $configuredBaseUrl")
    }
}

private class PlatformHeadersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", "djifly/${BuildConfig.VERSION_NAME} (Android)")
            .build()
        return chain.proceed(request)
    }
}
