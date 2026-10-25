package com.fuwaki.djifly.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuwaki.djifly.data.auth.TokenStore
import com.fuwaki.djifly.data.error.GlobalErrorHandler
import com.fuwaki.djifly.platform.dispatch.DispatchStages
import com.fuwaki.djifly.platform.network.PlatformApiService
import com.fuwaki.djifly.platform.network.generated.ApiDispatchOrderVO
import com.fuwaki.djifly.platform.telemetry.DispatchFlightState
import com.fuwaki.djifly.platform.telemetry.DispatchTelemetryService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val userName: String? = null,
    val pendingOrders: List<ApiDispatchOrderVO> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: PlatformApiService,
    private val tokenStore: TokenStore,
    private val dispatchTelemetry: DispatchTelemetryService,
    val errorHandler: GlobalErrorHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    val dispatchState: StateFlow<DispatchFlightState> = dispatchTelemetry.state

    init { loadHomeData() }

    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val userName = tokenStore.userName.firstOrNull()
            dispatchTelemetry.refreshOrders()
            val pending = runCatching { api.getMyDispatchOrders() }
                .getOrNull()
                ?.takeIf { it.isSuccessful }
                ?.body()
                ?.data
                .orEmpty()
                .filter { DispatchStages.isFlyable(it) }
            _uiState.value = HomeUiState(
                userName = userName,
                pendingOrders = pending,
                isLoading = false
            )
        }
    }
}
