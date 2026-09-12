package com.fuwaki.djifly.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuwaki.djifly.data.auth.AuthRepository
import com.fuwaki.djifly.data.auth.TokenStore
import com.fuwaki.djifly.data.stats.RiderStatsRepository
import com.fuwaki.djifly.data.stats.RiderStatsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenStore: TokenStore,
    private val statsRepository: RiderStatsRepository
) : ViewModel() {

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userId = MutableStateFlow(0L)
    val userId: StateFlow<Long> = _userId.asStateFlow()

    /** 飞手统计（/rider/stats 真实数据，1B-6：替代硬编码 0/5.0，FLY 中-7） */
    private val _stats = MutableStateFlow(RiderStatsUiState())
    val stats: StateFlow<RiderStatsUiState> = _stats.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _userName.value = tokenStore.userName.firstOrNull() ?: ""
            _userId.value = tokenStore.userId.firstOrNull() ?: 0L
        }
        viewModelScope.launch {
            statsRepository.loadStats()?.let { _stats.value = it }
        }
    }

    fun logout(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            authRepository.logout()
            onDone()
        }
    }
}
