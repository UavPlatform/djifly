package com.fuwaki.djifly.data.auth

/**
 * Access token 获取/刷新的最小抽象，供 OkHttp Authenticator 等组件使用。
 * 抽成接口便于 JVM 单测注入假实现（TokenStore 依赖 DataStore/Android Context）。
 */
interface AccessTokenProvider {

    /** 当前存储的 access token；未登录时为 null */
    suspend fun currentAccessToken(): String?

    /**
     * 刷新 access token（AuthRepository.refreshToken 的语义）。
     * 成功返回 true，新 token 通过 [currentAccessToken] 取得；
     * 失败返回 false（refresh token 也失效，会话已被清除，应回到登录态）。
     */
    suspend fun refreshToken(): Boolean
}
