package com.fuwaki.djifly.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuwaki.djifly.data.error.GlobalErrorHandler
import com.fuwaki.djifly.data.error.apiCall
import com.fuwaki.djifly.data.models.CreateSessionRequest
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

    init {
        loadSessions()
        chatRealtimeClient.ensureConnected()
        observeRealtimeMessages()
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
            val request = CreateSessionRequest(name = name, type = 0, userIds = userIds)
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
            }
        }
    }
}
