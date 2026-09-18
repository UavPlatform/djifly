package com.fuwaki.djifly.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuwaki.djifly.data.error.GlobalErrorHandler
import com.fuwaki.djifly.data.error.apiCall
import com.fuwaki.djifly.platform.network.generated.ApiSessionDTO
import com.fuwaki.djifly.data.models.SessionVO
import com.fuwaki.djifly.platform.chat.ChatRealtimeClient
import com.fuwaki.djifly.platform.network.PlatformApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MessageViewModel @Inject constructor(
    private val api: PlatformApiService,
    private val chatRealtimeClient: ChatRealtimeClient,
    val errorHandler: GlobalErrorHandler
) : ViewModel() {

    private val _sessions = MutableStateFlow<List<SessionVO>>(emptyList())
    val sessions: StateFlow<List<SessionVO>> = _sessions.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    // 未读数（1B-8 收敛包，t49 契约）：sessionId → 未读条数，只读端点，已读由进入会话显式推进
    private val _unreadCountMap = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val unreadCountMap: StateFlow<Map<Long, Int>> = _unreadCountMap.asStateFlow()

    val totalUnread: Int
        get() = _unreadCountMap.value.values.sum()

    init {
        loadSessions()
        refreshUnread()
        chatRealtimeClient.ensureConnected()
        observeRealtimeMessages()
    }

    /** 拉取未读数（只读，不推进已读位点） */
    fun refreshUnread() {
        viewModelScope.launch {
            apiCall(errorHandler) { api.getUnreadCount() }
                .onSuccess { map ->
                    _unreadCountMap.value = map
                        ?.mapKeys { (key, _) -> key.toLongOrNull() ?: 0L }
                        ?.filterKeys { it > 0L }
                        ?: emptyMap()
                }
        }
    }

    fun loadSessions() {
        viewModelScope.launch {
            _loading.value = true
            apiCall(errorHandler) { api.getSessions() }
                .onSuccess { data ->
                    _sessions.value = (data ?: emptyList())
                        .sortedByDescending { it.lastMessageTime ?: it.createTime }
                }
            _loading.value = false
        }
    }

    fun createSession(name: String, userIds: List<Long>, onSuccess: (Long) -> Unit) {
        viewModelScope.launch {
            val request = ApiSessionDTO(name = name, type = 0, userIds = userIds)
            apiCall(errorHandler) { api.createSession(request) }
                .onSuccess { session ->
                    onSuccess(session.id)
                    loadSessions()
                }
        }
    }

    private fun observeRealtimeMessages() {
        viewModelScope.launch {
            chatRealtimeClient.incomingMessages.collect {
                loadSessions()
                refreshUnread()   // 新消息到达 → 红点即时刷新（t49：补推保留未读语义）
            }
        }
    }
}