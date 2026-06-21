package com.fuwaki.djifly.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuwaki.djifly.data.auth.AuthRepository
import com.fuwaki.djifly.data.auth.AuthState
import com.fuwaki.djifly.data.auth.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── 登录表单状态 ──
sealed class LoginState {
    data object Idle : LoginState()
    data object Loading : LoginState()
    data class Error(val message: String) : LoginState()
    data object Success : LoginState()
}

// ── 注册表单状态 ──
sealed class RegisterState {
    data object Idle : RegisterState()
    data object Loading : RegisterState()
    data class Error(val message: String) : RegisterState()
    data object Success : RegisterState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenStore: TokenStore
) : ViewModel() {

    /** 可观察的认证状态 */
    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuthState.Unknown)

    /** 登录表单状态 */
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    /** 注册表单状态 */
    private val _registerState = MutableStateFlow<RegisterState>(RegisterState.Idle)
    val registerState: StateFlow<RegisterState> = _registerState.asStateFlow()

    /** 启动页就绪标志 */
    private val _splashReady = MutableStateFlow(false)
    val splashReady: StateFlow<Boolean> = _splashReady.asStateFlow()

    init {
        viewModelScope.launch {
            // 保底最少 1.5 秒启动页（同时等待 authState 初始化）
            delay(1500)
            _splashReady.value = true
        }
    }

    /**
     * 登录
     */
    fun login(userName: String, password: String) {
        if (_loginState.value == LoginState.Loading) return

        // 输入校验
        if (userName.isBlank()) {
            _loginState.value = LoginState.Error("请输入用户名")
            return
        }
        if (password.isBlank()) {
            _loginState.value = LoginState.Error("请输入密码")
            return
        }

        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            val result = authRepository.login(userName.trim(), password)
            result.fold(
                onSuccess = { _loginState.value = LoginState.Success },
                onFailure = { e -> _loginState.value = LoginState.Error(e.message ?: "登录失败") }
            )
        }
    }

    /**
     * 注册
     */
    fun register(
        userName: String,
        password: String,
        confirmPassword: String,
        djiId: String? = null
    ) {
        if (_registerState.value == RegisterState.Loading) return

        // 输入校验
        when {
            userName.isBlank() -> {
                _registerState.value = RegisterState.Error("请输入用户名")
                return
            }
            userName.length < 4 || userName.length > 20 -> {
                _registerState.value = RegisterState.Error("用户名长度需为 4-20 个字符")
                return
            }
            password.isBlank() -> {
                _registerState.value = RegisterState.Error("请输入密码")
                return
            }
            password.length < 6 -> {
                _registerState.value = RegisterState.Error("密码长度至少 6 个字符")
                return
            }
            password != confirmPassword -> {
                _registerState.value = RegisterState.Error("两次密码输入不一致")
                return
            }
        }

        val djiIdValue = djiId?.trim()?.ifBlank { null }

        viewModelScope.launch {
            _registerState.value = RegisterState.Loading
            val result = authRepository.register(userName.trim(), password, djiIdValue)
            result.fold(
                onSuccess = { _registerState.value = RegisterState.Success },
                onFailure = { e -> _registerState.value = RegisterState.Error(e.message ?: "注册失败") }
            )
        }
    }

    /**
     * 登出
     */
    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _loginState.value = LoginState.Idle
            _registerState.value = RegisterState.Idle
        }
    }

    /** 清除登录错误状态 */
    fun clearLoginError() {
        if (_loginState.value is LoginState.Error) {
            _loginState.value = LoginState.Idle
        }
    }

    /** 清除注册错误状态 */
    fun clearRegisterError() {
        if (_registerState.value is RegisterState.Error) {
            _registerState.value = RegisterState.Idle
        }
    }
}
