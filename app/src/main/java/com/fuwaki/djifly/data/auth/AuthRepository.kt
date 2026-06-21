package com.fuwaki.djifly.data.auth

import android.util.Log
import com.fuwaki.djifly.data.models.LoginRequest
import com.fuwaki.djifly.data.models.RegisterRequest
import com.fuwaki.djifly.data.models.UserInfo
import com.fuwaki.djifly.platform.network.PlatformApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AuthRepository"

/**
 * 认证状态
 */
sealed class AuthState {
    /** 尚未检查本地 Token */
    data object Unknown : AuthState()
    /** 未登录 */
    data object Unauthenticated : AuthState()
    /** 已登录 */
    data class Authenticated(val user: UserInfo) : AuthState()
}

/**
 * 认证仓库 —— 管理登录、注册、Token 刷新、登出
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: PlatformApiService,
    private val tokenStore: TokenStore
) {
    /**
     * 可观察的认证状态
     */
    val authState: Flow<AuthState> = combine(
        tokenStore.userId,
        tokenStore.userName,
        tokenStore.userRole
    ) { userId, userName, role ->
        if (userId != null && userName != null && role != null) {
            AuthState.Authenticated(UserInfo(userId, userName, role))
        } else {
            AuthState.Unauthenticated
        }
    }

    /**
     * 登录
     */
    suspend fun login(userName: String, password: String): Result<UserInfo> {
        return try {
            val response = api.login(LoginRequest(userName, password))
            Log.d(TAG, "Login response: code=${response.code()}, isSuccessful=${response.isSuccessful}")
            Log.d(TAG, "Login body: success=${response.body()?.success}, message=${response.body()?.message}, data=${response.body()?.data}")
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()!!.data!!
                Log.d(TAG, "Login data: token=${data.token.take(30)}..., refreshToken=${data.refreshToken.take(20)}...")
                // 解析 JWT 获取用户信息
                val userInfo = parseJwtPayload(data.token)
                Log.d(TAG, "Parsed userInfo: userId=${userInfo.userId}, userName=${userInfo.userName}, role=${userInfo.role}")
                tokenStore.saveSession(
                    token = data.token,
                    refreshToken = data.refreshToken,
                    userId = userInfo.userId,
                    userName = userInfo.userName,
                    role = userInfo.role
                )
                // 验证保存成功
                val savedToken = tokenStore.getAccessToken()
                Log.d(TAG, "Token saved successfully: ${savedToken != null}, startsWith: ${savedToken?.take(20)}")
                Result.success(userInfo)
            } else {
                val msg = response.body()?.message ?: "登录失败"
                Log.e(TAG, "Login failed: $msg")
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login exception", e)
            Result.failure(e)
        }
    }

    /**
     * 注册飞手（role=1）
     */
    suspend fun register(userName: String, password: String, djiId: String? = null): Result<UserInfo> {
        return try {
            val response = api.register(RegisterRequest(userName, password, djiId))
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()!!.data!!
                val userInfo = parseJwtPayload(data.token)
                tokenStore.saveSession(
                    token = data.token,
                    refreshToken = data.refreshToken,
                    userId = userInfo.userId,
                    userName = userInfo.userName,
                    role = userInfo.role
                )
                Result.success(userInfo)
            } else {
                val msg = response.body()?.message ?: "注册失败"
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 刷新 Token
     */
    suspend fun refreshToken(): Boolean {
        return try {
            val refreshToken = tokenStore.getRefreshToken() ?: return false
            val response = api.refreshToken(refreshToken)
            if (response.isSuccessful && response.body()?.success == true) {
                val newToken = response.body()!!.data!!.token
                tokenStore.updateAccessToken(newToken)
                true
            } else {
                // 刷新失败 → 登出
                tokenStore.clear()
                false
            }
        } catch (e: Exception) {
            tokenStore.clear()
            false
        }
    }

    /**
     * 登出
     */
    suspend fun logout() {
        tokenStore.clear()
    }

    /**
     * 获取当前用户 ID
     */
    suspend fun currentUserId(): Long? = tokenStore.getUserId()

    /**
     * 从 JWT payload 解析用户信息（不做签名验证，仅解析 payload）
     */
    private fun parseJwtPayload(token: String): UserInfo {
        try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val payload = parts[1]
                val decoded = android.util.Base64.decode(payload, android.util.Base64.URL_SAFE)
                val json = String(decoded, Charsets.UTF_8)
                val gson = com.google.gson.Gson()
                val map = gson.fromJson(json, Map::class.java)
                val userId = (map["userId"] as? Double)?.toLong()
                    ?: (map["userId"] as? Number)?.toLong()
                    ?: (map["userId"] as? String)?.toLongOrNull()
                    ?: 0L
                val userName = map["username"] as? String ?: map["userName"] as? String ?: ""
                val role = (map["role"] as? Double)?.toInt()
                    ?: (map["role"] as? Number)?.toInt()
                    ?: (map["role"] as? String)?.toIntOrNull()
                    ?: 1
                return UserInfo(userId, userName, role)
            }
        } catch (_: Exception) {
            // JWT 解析失败，返回默认值
        }
        return UserInfo(0, "", 1)
    }
}
