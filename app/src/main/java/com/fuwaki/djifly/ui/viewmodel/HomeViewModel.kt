package com.fuwaki.djifly.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuwaki.djifly.data.auth.TokenStore
import com.fuwaki.djifly.data.error.GlobalErrorHandler
import com.fuwaki.djifly.data.error.apiCall
import com.fuwaki.djifly.data.models.TaskVo
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
    val todayOrders: Int = 0,
    val todayEarnings: String = "¥0",
    val rating: Double = 5.0,
    val completionRate: String = "0%",
    val activeTasks: List<TaskVo> = emptyList(),
    val recommendedTasks: List<TaskVo> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: PlatformApiService,
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

            // 加载全部任务用于统计
            var allMyTasks = emptyList<TaskVo>()
            apiCall(errorHandler) { api.getMyTasks() }
                .onSuccess { data ->
                    allMyTasks = data?.tasks ?: emptyList()
                }

            val completedCount = allMyTasks.count { it.taskStatus == "COMPLETED" }
            val totalCount = allMyTasks.size
            val completionRate = if (totalCount > 0) "${completedCount * 100 / totalCount}%" else "0%"
            val todayEarnings = allMyTasks
                .filter { it.taskStatus == "COMPLETED" }
                .sumOf { it.totalAmount?.toDouble() ?: 0.0 }

            _uiState.value = HomeUiState(
                userName = userName,
                todayOrders = allMyTasks.count { it.taskStatus == "IN_PROGRESS" },
                todayEarnings = "¥%.0f".format(todayEarnings),
                rating = 5.0,
                completionRate = completionRate,
                activeTasks = activeTasks,
                recommendedTasks = recommendedTasks,
                isLoading = false
            )
        }
    }
}
