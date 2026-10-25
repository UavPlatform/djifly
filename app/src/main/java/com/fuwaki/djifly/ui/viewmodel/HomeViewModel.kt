package com.fuwaki.djifly.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuwaki.djifly.data.auth.TokenStore
import com.fuwaki.djifly.data.error.GlobalErrorHandler
import com.fuwaki.djifly.data.error.apiCall
import com.fuwaki.djifly.data.models.FlightEntryGate
import com.fuwaki.djifly.data.models.TaskVo
import com.fuwaki.djifly.platform.network.PlatformApiService
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
    /** 待执飞任务（matchStatus=CONFIRMED 或已 IN_PROGRESS/PENDING_ACCEPTANCE，见 [FlightEntryGate]） */
    val pendingTasks: List<TaskVo> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * 首页数据：只拉取「当前飞手的任务」（/rider/my-tasks）并按执飞门禁过滤。
 *
 * djifly 是飞行执行适配器（REQ-DJIFLY-001）：不再拉取任务大厅（/rider/square）推荐任务，
 * 也不再展示收益统计（/rider/stats）——这两类业务归 app/。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: PlatformApiService,
    private val tokenStore: TokenStore,
    private val dispatchTelemetry: DispatchTelemetryService,
    val errorHandler: GlobalErrorHandler // 暴露给 UI 层的 GlobalErrorEffect 使用
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** 派单执行：当前订单、连机记录、待补传条数（自动上报，无需手填编号）。 */
    val dispatchState: StateFlow<DispatchFlightState> = dispatchTelemetry.state

    init {
        loadHomeData()
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val userName = tokenStore.userName.firstOrNull()
            dispatchTelemetry.refreshOrders()

            // 仅本飞手名下的任务；撮合未确认（洽谈/待支付/待飞手确认）的任务不进入执飞列表
            var pendingTasks = emptyList<TaskVo>()
            apiCall(errorHandler) { api.getMyTasks() }
                .onSuccess { data ->
                    pendingTasks = data?.tasks
                        ?.filter { FlightEntryGate.isPendingFlight(it) }
                        ?: emptyList()
                }

            _uiState.value = HomeUiState(
                userName = userName,
                pendingTasks = pendingTasks,
                isLoading = false
            )
        }
    }
}
