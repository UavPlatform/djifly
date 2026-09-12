package com.fuwaki.djifly.data.auth

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * TokenAuthenticator（401 自动刷新重试）纯单元测试。
 * 全部用 okhttp 内存对象构造 401 响应，不起网络，不依赖 DataStore/Android。
 */
class TokenAuthenticatorTest {

    private class FakeTokenProvider(
        var token: String? = "token-a",
        var refreshResult: Boolean = true
    ) : AccessTokenProvider {
        var refreshCalls = 0

        override suspend fun currentAccessToken(): String? = token

        override suspend fun refreshToken(): Boolean {
            refreshCalls++
            if (refreshResult) {
                token = "token-refreshed-$refreshCalls"
            }
            return refreshResult
        }
    }

    /** dagger.Lazy 的测试替身：直接返回固定实例 */
    private fun <T : Any> lazyOf(value: T): dagger.Lazy<T> = dagger.Lazy { value }

    private fun failedResponse(
        path: String = "/rider/square",
        authHeader: String? = "Bearer token-a",
        priorCount: Int = 0
    ): Response {
        val requestBuilder = Request.Builder().url("http://example.com$path")
        authHeader?.let { requestBuilder.header("Authorization", it) }
        val request = requestBuilder.build()

        var response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body(ResponseBody.create(null, ""))
            .build()

        repeat(priorCount) {
            response = response.newBuilder()
                .priorResponse(
                    // okhttp 校验 priorResponse.body 必须为 null
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(401)
                        .message("Unauthorized")
                        .build()
                )
                .build()
        }
        return response
    }

    @Test
    fun refreshes_token_and_retries_when_failed_token_matches_stored() {
        val provider = FakeTokenProvider(token = "token-a", refreshResult = true)
        val authenticator = TokenAuthenticator(lazyOf(provider))

        val retry = authenticator.authenticate(null, failedResponse(authHeader = "Bearer token-a"))

        assertEquals(1, provider.refreshCalls)
        assertEquals("Bearer token-refreshed-1", retry!!.header("Authorization"))
    }

    @Test
    fun reuses_current_token_without_extra_refresh_when_request_used_stale_token() {
        // 并发场景：另一个请求已刷新成功（存储里是 token-new），失败请求带的是旧 token
        val provider = FakeTokenProvider(token = "token-new")
        val authenticator = TokenAuthenticator(lazyOf(provider))

        val retry = authenticator.authenticate(null, failedResponse(authHeader = "Bearer token-stale"))

        assertEquals(0, provider.refreshCalls)
        assertEquals("Bearer token-new", retry!!.header("Authorization"))
    }

    @Test
    fun attaches_current_token_when_failed_request_carried_none() {
        // 失败请求没带 token（如登录前发起），此时存储已有 token → 直接补上，不触发刷新
        val provider = FakeTokenProvider(token = "token-existing")
        val authenticator = TokenAuthenticator(lazyOf(provider))

        val retry = authenticator.authenticate(null, failedResponse(authHeader = null))

        assertEquals(0, provider.refreshCalls)
        assertEquals("Bearer token-existing", retry!!.header("Authorization"))
    }

    @Test
    fun gives_up_when_refresh_fails() {
        val provider = FakeTokenProvider(token = "token-a", refreshResult = false)
        val authenticator = TokenAuthenticator(lazyOf(provider))

        val retry = authenticator.authenticate(null, failedResponse())

        assertNull(retry)
        assertEquals(1, provider.refreshCalls)
    }

    @Test
    fun never_refreshes_the_refresh_endpoint_itself() {
        // /user/refresh 的 401 = refresh token 无效，必须放弃而不是刷新自身
        val provider = FakeTokenProvider()
        val authenticator = TokenAuthenticator(lazyOf(provider))

        val retry = authenticator.authenticate(null, failedResponse(path = "/user/refresh"))

        assertNull(retry)
        assertEquals(0, provider.refreshCalls)
    }

    @Test
    fun gives_up_after_one_retried_attempt() {
        val provider = FakeTokenProvider(refreshResult = true)
        val authenticator = TokenAuthenticator(lazyOf(provider))

        val retry = authenticator.authenticate(null, failedResponse(priorCount = 1))

        assertNull(retry)
        assertEquals(0, provider.refreshCalls)
    }
}
