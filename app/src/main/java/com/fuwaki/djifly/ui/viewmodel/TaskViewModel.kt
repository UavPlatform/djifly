package com.fuwaki.djifly.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuwaki.djifly.data.error.AppError
import com.fuwaki.djifly.data.error.GlobalErrorHandler
import com.fuwaki.djifly.data.error.apiCall
import com.fuwaki.djifly.data.models.CreateSessionRequest
import com.fuwaki.djifly.data.models.TaskType
import com.fuwaki.djifly.data.models.TaskVo
import com.fuwaki.djifly.platform.network.PlatformApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ActionState {
    data object Idle : ActionState()
    data object Loading : ActionState()
    data class Success(val message: String) : ActionState()
    data class Error(val message: String) : ActionState()
}

sealed class ContactState {
    data object Idle : ContactState()
    data object Loading : ContactState()
    data class Ready(val sessionId: Long, val otherUserName: String) : ContactState()
}

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val api: PlatformApiService,
    val errorHandler: GlobalErrorHandler // 暴露给 UI 层的 GlobalErrorEffect 使用
) : ViewModel() {

    // Task Square
    private val _squareTasks = MutableStateFlow<List<TaskVo>>(emptyList())
    val squareTasks: StateFlow<List<TaskVo>> = _squareTasks
    private val _squareLoading = MutableStateFlow(false)
    val squareLoading: StateFlow<Boolean> = _squareLoading
    private val _selectedType = MutableStateFlow<TaskType?>(null)
    val selectedType: StateFlow<TaskType?> = _selectedType

    // My Tasks
    private val _activeTasks = MutableStateFlow<List<TaskVo>>(emptyList())
    val activeTasks: StateFlow<List<TaskVo>> = _activeTasks
    private val _completedTasks = MutableStateFlow<List<TaskVo>>(emptyList())
    val completedTasks: StateFlow<List<TaskVo>> = _completedTasks
    private val _myTasksLoading = MutableStateFlow(false)
    val myTasksLoading: StateFlow<Boolean> = _myTasksLoading

    // Task Detail
    private val _taskDetail = MutableStateFlow<TaskVo?>(null)
    val taskDetail: StateFlow<TaskVo?> = _taskDetail
    private val _detailLoading = MutableStateFlow(false)
    val detailLoading: StateFlow<Boolean> = _detailLoading

    // Actions state
    private val _actionState = MutableStateFlow<ActionState>(ActionState.Idle)
    val actionState: StateFlow<ActionState> = _actionState

    private val _contactState = MutableStateFlow<ContactState>(ContactState.Idle)
    val contactState: StateFlow<ContactState> = _contactState

    // -----------------------------------------------------------------------
    // Task Square
    // -----------------------------------------------------------------------

    fun loadSquare() {
        viewModelScope.launch {
            _squareLoading.value = true
            apiCall(errorHandler) { api.getTaskSquare() }
                .onSuccess { data ->
                    _squareTasks.value = data?.tasks ?: emptyList()
                }
                .onError { error ->
                    _actionState.value = ActionState.Error(error.message)
                }
            _squareLoading.value = false
        }
    }

    fun filterByType(type: TaskType?) {
        _selectedType.value = type
    }

    // -----------------------------------------------------------------------
    // My Tasks
    // -----------------------------------------------------------------------

    fun loadMyTasks() {
        viewModelScope.launch {
            _myTasksLoading.value = true
            // 加载进行中的任务
            apiCall(errorHandler) { api.getMyTasks() }
                .onSuccess { data ->
                    _activeTasks.value = data?.tasks ?: emptyList()
                }
            // 加载历史任务
            apiCall(errorHandler) { api.getTaskHistory() }
                .onSuccess { data ->
                    _completedTasks.value = data?.tasks ?: emptyList()
                }
            _myTasksLoading.value = false
        }
    }

    // -----------------------------------------------------------------------
    // Task Detail
    // -----------------------------------------------------------------------

    fun loadTaskDetail(taskNum: String) {
        viewModelScope.launch {
            _detailLoading.value = true
            apiCall(errorHandler) { api.getTaskDetail(taskNum) }
                .onSuccess { data ->
                    _taskDetail.value = data
                }
                .onError { error ->
                    _actionState.value = ActionState.Error(error.message)
                }
            _detailLoading.value = false
        }
    }

    // -----------------------------------------------------------------------
    // Actions
    // -----------------------------------------------------------------------

    fun acceptTask(taskNum: String) {
        viewModelScope.launch {
            _actionState.value = ActionState.Loading
            apiCall(errorHandler) { api.acceptTask(taskNum) }
                .onSuccess {
                    _actionState.value = ActionState.Success("接单成功")
                    loadSquare()
                    loadMyTasks()
                }
                .onError { error ->
                    _actionState.value = ActionState.Error(error.message)
                }
        }
    }

    fun cancelTask(taskNum: String) {
        viewModelScope.launch {
            _actionState.value = ActionState.Loading
            apiCall(errorHandler) { api.cancelTask(taskNum) }
                .onSuccess {
                    _actionState.value = ActionState.Success("取消成功")
                    loadMyTasks()
                    loadSquare()
                }
                .onError { error ->
                    _actionState.value = ActionState.Error(error.message)
                }
        }
    }

    fun completeTask(taskNum: String) {
        viewModelScope.launch {
            _actionState.value = ActionState.Loading
            apiCall(errorHandler) { api.completeTask(taskNum) }
                .onSuccess {
                    _actionState.value = ActionState.Success("任务已完成")
                    loadMyTasks()
                }
                .onError { error ->
                    _actionState.value = ActionState.Error(error.message)
                }
        }
    }

    fun contactTaskPublisher(task: TaskVo) {
        if (_contactState.value is ContactState.Loading) return

        viewModelScope.launch {
            if (task.userId <= 0L) {
                errorHandler.emit(AppError.Business("任务发布人信息缺失"))
                _contactState.value = ContactState.Idle
                return@launch
            }

            _contactState.value = ContactState.Loading
            val sessionName = task.taskName.ifBlank { "任务沟通" }
            val request = CreateSessionRequest(
                name = sessionName,
                type = 0,
                userIds = listOf(task.userId)
            )
            apiCall(errorHandler) { api.createSession(request) }
                .onSuccess { session ->
                    _contactState.value = ContactState.Ready(
                        sessionId = session.id,
                        otherUserName = session.otherUserName ?: "任务发布人"
                    )
                }
                .onError { error ->
                    _contactState.value = ContactState.Idle
                }
        }
    }

    fun resetActionState() {
        _actionState.value = ActionState.Idle
    }

    fun resetContactState() {
        _contactState.value = ContactState.Idle
    }
}
