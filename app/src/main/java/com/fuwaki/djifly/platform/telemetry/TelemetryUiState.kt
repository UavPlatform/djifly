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

/**
 * LOCAL is a sensor event accepted into the local event journal. It is neither a WebSocket send nor receive, so it
 * must not borrow those arrows in the UI.
 */
enum class TelemetryMessageDirection { SENT, RECEIVED, LOCAL }

/**
 * Diagnostics buffer for the flight console. The only writer is the local event capture path
 * ([DispatchTelemetryService]); nothing here implies the backend received anything.
 */
@Singleton
class TelemetryUiState @Inject constructor() {
    private val _messages = MutableStateFlow<List<TelemetryMessageLog>>(emptyList())
    val messages: StateFlow<List<TelemetryMessageLog>> = _messages.asStateFlow()

    private val _streamState = MutableStateFlow<TelemetryStreamState>(TelemetryStreamState.Idle)
    val streamState: StateFlow<TelemetryStreamState> = _streamState.asStateFlow()

    /** Bounded so a long flight cannot grow this diagnostic buffer without limit. */
    fun record(events: List<TelemetryMessageLog>) {
        if (events.isEmpty()) return
        _messages.value = (_messages.value + events).takeLast(MAX_HISTORY)
    }

    fun updateStreamState(state: TelemetryStreamState) {
        _streamState.value = state
    }

    fun clear() {
        _messages.value = emptyList()
    }

    private companion object {
        const val MAX_HISTORY = 100
    }
}

sealed class TelemetryStreamState {
    data object Idle : TelemetryStreamState()
    data object Connecting : TelemetryStreamState()
    data object Connected : TelemetryStreamState()
    data class Disconnected(val reason: String) : TelemetryStreamState()
    data class Error(val message: String) : TelemetryStreamState()
}
