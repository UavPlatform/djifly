package com.fuwaki.djifly.data.auth

import android.util.Log
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AuthInterceptor"

/**
 * OkHttp 拦截器 —— 自动在请求头中注入 Authorization: Bearer <token>
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        // 登录/注册/刷新/无人机注册/WebSocket 请求 不注入 Token
        val path = original.url.encodedPath
        val skipAuth = SKIP_AUTH_PATHS.any { path.contains(it) }
        if (skipAuth) {
            Log.d(TAG, "SKIP auth for: $path")
            return chain.proceed(original)
        }

        val token = runBlocking { tokenStore.getAccessToken() }
        Log.d(TAG, "Path: $path | Token: ${if (token != null) "${token.take(20)}..." else "NULL"}")

        val request = if (token != null) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            Log.w(TAG, "NO TOKEN available for: $path")
            original
        }

        val response = chain.proceed(request)
        if (response.code == 401) {
            Log.e(TAG, "401 for: $path")
        }
        return response
    }

    companion object {
        private val SKIP_AUTH_PATHS = listOf(
            "/user/login",
            "/user/register",
            "/rider/register",
            "/user/refresh",
            "/appUav/add",
            "/api/ws/request"
        )
    }
}
