package com.fuwaki.djifly.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuwaki.djifly.data.auth.TokenStore
import com.fuwaki.djifly.data.error.GlobalErrorHandler
import com.fuwaki.djifly.data.error.apiCall
import com.fuwaki.djifly.data.models.MessageVO
import com.fuwaki.djifly.data.models.SendMessageRequest
import com.fuwaki.djifly.platform.chat.ChatRealtimeClient
import com.fuwaki.djifly.platform.network.PlatformApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val api: PlatformApiService,
    private val tokenStore: TokenStore,
    private val chatRealtimeClient: ChatRealtimeClient,
    val errorHandler: GlobalErrorHandler,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sessionId: Long = savedStateHandle["sessionId"] ?: 0L

    private val _messages = MutableStateFlow<List<MessageVO>>(emptyList())
    val messages: StateFlow<List<MessageVO>> = _messages.asStateFlow()

    private val _otherUserName = MutableStateFlow("")
    val otherUserName: StateFlow<String> = _otherUserName.asStateFlow()

    private val _currentUserId = MutableStateFlow(0L)
    val currentUserId: StateFlow<Long> = _currentUserId.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    init {
        loadMessages()
        chatRealtimeClient.ensureConnected()
        observeRealtimeMessages()
    }

    fun loadMessages() {
        viewModelScope.launch {
            val userId = tokenStore.userId.firstOrNull() ?: 0L
            _currentUserId.value = userId

            apiCall(errorHandler) { api.getMessages(sessionId) }
                .onSuccess { data ->
                    _messages.value = data
                        .map { it.toMessageVO() }
                        .sortedBy { it.createTime }
                }
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank() || _sending.value) return

        viewModelScope.launch {
            _sending.value = true
            val text = content.trim()
            val now = System.currentTimeMillis()
            val currentUser = tokenStore.userId.firstOrNull() ?: 0L
            val pending = MessageVO(
                id = "local-$now",
                sessionId = sessionId,
                content = text,
                fromUserId = currentUser,
                createTime = now
            )
            appendOrReplaceMessage(pending)

            val request = SendMessageRequest(sessionId = sessionId, content = text)
            apiCall(errorHandler) { api.sendMessage(request) }
                .onSuccess {
                    loadMessages()
                }
            _sending.value = false
        }
    }

    fun setOtherUserName(name: String) {
        _otherUserName.value = name
    }

    private fun observeRealtimeMessages() {
        viewModelScope.launch {
            chatRealtimeClient.incomingMessages.collect { message ->
                if (message.fromUserId == _currentUserId.value) return@collect
                if (message.sessionId == sessionId) {
                    appendOrReplaceMessage(message)
                }
            }
        }
    }

    private fun appendOrReplaceMessage(message: MessageVO) {
        val next = _messages.value
            .filterNot { existing ->
                existing.id == message.id ||
                    (existing.id.startsWith("local-") &&
                        existing.fromUserId == message.fromUserId &&
                        existing.content == message.content)
            }
            .plus(message)
            .sortedBy { it.createTime }
        _messages.value = next
    }
}
