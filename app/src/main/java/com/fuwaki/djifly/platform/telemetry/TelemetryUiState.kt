package com.fuwaki.djifly.platform.telemetry

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Local telemetry diagnostics; there is no device 遥测事件 session anymore. */
data class TelemetryMessageLog(
    val timestamp: Long = System.currentTimeMillis(),
    val direction: TelemetryMessageDirection,
    val type: String,
    val name: String?,
    val content: String,
)

enum class TelemetryMessageDirection { SENT, RECEIVED }

@Singleton
class TelemetryUiState @Inject constructor() {
    private val _messages = MutableStateFlow<List<TelemetryMessageLog>>(emptyList())
    val messages: StateFlow<List<TelemetryMessageLog>> = _messages.asStateFlow()

    private val _streamState = MutableStateFlow<TelemetryStreamState>(TelemetryStreamState.Idle)
    val streamState: StateFlow<TelemetryStreamState> = _streamState.asStateFlow()

    fun clear() { _messages.value = emptyList() }
}

sealed class TelemetryStreamState {
    data object Idle : TelemetryStreamState()
    data object Connecting : TelemetryStreamState()
    data object Connected : TelemetryStreamState()
    data class Disconnected(val reason: String) : TelemetryStreamState()
    data class Error(val message: String) : TelemetryStreamState()
}
