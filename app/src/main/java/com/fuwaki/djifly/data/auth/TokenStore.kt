package com.fuwaki.djifly.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    // ── 可观察的登录状态 ──
    val userId: Flow<Long?> = appContext.authDataStore.data.map { it[USER_ID] }
    val userName: Flow<String?> = appContext.authDataStore.data.map { it[USER_NAME] }
    val userRole: Flow<Int?> = appContext.authDataStore.data.map { it[USER_ROLE] }

    // ── Token 读取 ──
    suspend fun getAccessToken(): String? =
        appContext.authDataStore.data.first()[ACCESS_TOKEN]

    suspend fun getRefreshToken(): String? =
        appContext.authDataStore.data.first()[REFRESH_TOKEN]

    suspend fun getUserId(): Long? =
        appContext.authDataStore.data.first()[USER_ID]

    // ── 保存完整会话 ──
    suspend fun saveSession(token: String, refreshToken: String, userId: Long, userName: String, role: Int) {
        appContext.authDataStore.edit { prefs ->
            prefs[ACCESS_TOKEN] = token
            prefs[REFRESH_TOKEN] = refreshToken
            prefs[USER_ID] = userId
            prefs[USER_NAME] = userName
            prefs[USER_ROLE] = role
        }
    }

    // ── 仅更新 Access Token（刷新时用） ──
    suspend fun updateAccessToken(token: String) {
        appContext.authDataStore.edit { prefs ->
            prefs[ACCESS_TOKEN] = token
        }
    }

    // ── 清除会话（登出） ──
    suspend fun clear() {
        appContext.authDataStore.edit { it.clear() }
    }

    // ── 判断是否已登录 ──
    suspend fun isLoggedIn(): Boolean = getAccessToken() != null

    companion object {
        private val ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val USER_ID = longPreferencesKey("user_id")
        private val USER_NAME = stringPreferencesKey("user_name")
        private val USER_ROLE = intPreferencesKey("user_role")
    }
}
