package com.fuwaki.djifly.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuwaki.djifly.data.auth.TokenStore
import com.fuwaki.djifly.data.error.GlobalErrorHandler
import com.fuwaki.djifly.data.error.apiCall
import com.fuwaki.djifly.data.models.TaskVo
import com.fuwaki.djifly.data.stats.RiderStatsRepository
import com.fuwaki.djifly.data.stats.RiderStatsUiState
import com.fuwaki.djifly.platform.network.PlatformApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val userName: String? = null,
    val stats: RiderStatsUiState = RiderStatsUiState(),
    val activeTasks: List<TaskVo> = emptyList(),
    val recommendedTasks: List<TaskVo> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * 首页数据（1B-6：统计接 /rider/stats 真实数据，移除本地拼凑估算与 rating 硬编码，FLY 中-7）。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: PlatformApiService,
    private val statsRepository: RiderStatsRepository,
    private val tokenStore: TokenStore,
    val errorHandler: GlobalErrorHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeData()
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val userName = tokenStore.userName.firstOrNull()

            // 加载进行中的任务
            var activeTasks = emptyList<TaskVo>()
            apiCall(errorHandler) { api.getMyTasks() }
                .onSuccess { data ->
                    activeTasks = data?.tasks
                        ?.filter { it.taskStatus == "IN_PROGRESS" }
                        ?.take(3)
                        ?: emptyList()
                }

            // 加载推荐任务（大厅的 IDLE 任务）
            var recommendedTasks = emptyList<TaskVo>()
            apiCall(errorHandler) { api.getTaskSquare() }
                .onSuccess { data ->
                    recommendedTasks = data?.tasks
                        ?.filter { it.taskStatus == "IDLE" }
                        ?.take(3)
                        ?: emptyList()
                }

            // 统计接 /rider/stats 真实数据（今日接单/总完成/总收益），口径与后端一致
            val stats = statsRepository.loadStats()

            _uiState.value = HomeUiState(
                userName = userName,
                stats = stats ?: RiderStatsUiState(),
                activeTasks = activeTasks,
                recommendedTasks = recommendedTasks,
                isLoading = false
            )
        }
    }
}
