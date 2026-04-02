package com.fuwaki.djifly.platform.ws

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class WsMessageLog(
    val timestamp: Long = System.currentTimeMillis(),
    val direction: WsMessageDirection,
    val type: String,
    val name: String?,
    val content: String
)

enum class WsMessageDirection { SENT, RECEIVED }

@Singleton
class WsCommunicationState @Inject constructor() {
    
    private val _messages = MutableStateFlow<List<WsMessageLog>>(emptyList())
    val messages: StateFlow<List<WsMessageLog>> = _messages.asStateFlow()
    
    private val _connectionState = MutableStateFlow<WsConnectionState>(WsConnectionState.Idle)
    val connectionState: StateFlow<WsConnectionState> = _connectionState.asStateFlow()
    
    private val maxHistorySize = 100
    
    fun logSent(type: String, name: String?, content: String) {
        addLog(WsMessageDirection.SENT, type, name, content)
    }
    
    fun logReceived(type: String, name: String?, content: String) {
        addLog(WsMessageDirection.RECEIVED, type, name, content)
    }
    
    private fun addLog(direction: WsMessageDirection, type: String, name: String?, content: String) {
        val log = WsMessageLog(
            timestamp = System.currentTimeMillis(),
            direction = direction,
            type = type,
            name = name,
            content = content
        )
        _messages.value = (_messages.value + log).takeLast(maxHistorySize)
    }
    
    fun updateConnectionState(state: WsConnectionState) {
        _connectionState.value = state
    }
    
    fun clear() {
        _messages.value = emptyList()
    }
}

sealed class WsConnectionState {
    data object Idle : WsConnectionState()
    data object Connecting : WsConnectionState()
    data object Connected : WsConnectionState()
    data class Disconnected(val reason: String) : WsConnectionState()
    data class Error(val message: String) : WsConnectionState()
}
