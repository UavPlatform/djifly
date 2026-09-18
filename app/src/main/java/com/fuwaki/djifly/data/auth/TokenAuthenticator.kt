package com.fuwaki.djifly.data.auth

import com.fuwaki.djifly.platform.network.generated.ApiPaths
import dagger.Lazy
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Authenticator —— 401 时自动刷新 access token 并重试（追溯 FLY 高-1：
 * 原实现 401 只打日志，refreshToken() 全工程零调用，token 过期只能手动重登）。
 *
 * 策略（authenticate 由 OkHttp 在收到 401 后同步调用，运行在 IO 线程）：
 * - 失败请求携带的 token 与当前存储的 token 不一致 → 其他请求已完成刷新，
 *   直接用最新 token 重试，避免并发 401 时重复刷新；
 * - 一致（或失败请求本就没带 token）→ 调用 [AccessTokenProvider.refreshToken] 刷新，
 *   成功后以新 token 重试；失败（refresh token 也失效）→ 放弃本次重试，
 *   会话已由 AuthRepository 清除，上层回到登录态；
 * - `/user/refresh` 自身的 401 不再刷新（refresh token 无效，防自循环）；
 * - 同一请求最多重试 1 次（priorResponse 链兜底），杜绝无限 401 循环。
 *
 * 依赖说明：AccessTokenProvider（=AuthRepository）经 Retrofit 依赖 OkHttpClient，
 * 与本类形成 DI 环，用 [Lazy] 延迟解析断环——authenticate 触发时 OkHttpClient
 * 已构造完成，get() 返回单例 AuthRepository。
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenProviderLazy: Lazy<AccessTokenProvider>
) : Authenticator {

    private val refreshLock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.url.encodedPath.contains(REFRESH_PATH_SEGMENT)) return null
        if (responseCount(response) >= MAX_RETRIES) return null

        val failedToken = response.request.header("Authorization")?.removePrefix(BEARER_PREFIX)

        val retryToken: String? = synchronized(refreshLock) {
            runBlocking {
                val tokenProvider = tokenProviderLazy.get()
                val current = tokenProvider.currentAccessToken()
                if (current != null && current != failedToken) {
                    // 其他请求已刷新成功，直接复用最新 token
                    current
                } else {
                    if (tokenProvider.refreshToken()) {
                        tokenProvider.currentAccessToken()
                    } else {
                        null
                    }
                }
            }
        }

        if (retryToken == null) return null

        return response.request.newBuilder()
            .header("Authorization", "$BEARER_PREFIX$retryToken")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    companion object {
        /** 刷新接口（契约路径 /user/refresh）自身的 401 不再触发刷新，防自循环。 */
        private val REFRESH_PATH_SEGMENT = "/" + ApiPaths.USER_REFRESH
        private const val BEARER_PREFIX = "Bearer "
        private const val MAX_RETRIES = 2
    }
}