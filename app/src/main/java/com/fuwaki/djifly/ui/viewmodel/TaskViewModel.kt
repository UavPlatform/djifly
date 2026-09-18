package com.fuwaki.djifly.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuwaki.djifly.data.error.AppError
import com.fuwaki.djifly.data.error.GlobalErrorHandler
import com.fuwaki.djifly.data.error.apiCall
import com.fuwaki.djifly.platform.network.generated.ApiSessionDTO
import com.fuwaki.djifly.data.models.TaskType
import com.fuwaki.djifly.data.models.TaskVo
import com.fuwaki.djifly.data.stats.RiderStatsRepository
import com.fuwaki.djifly.platform.attachment.DeliverableCheck
import com.fuwaki.djifly.platform.attachment.DeliverableItemPhase
import com.fuwaki.djifly.platform.attachment.DeliverableItemUi
import com.fuwaki.djifly.platform.attachment.DeliverableRules
import com.fuwaki.djifly.platform.attachment.DeliverableUploadOutcome
import com.fuwaki.djifly.platform.attachment.DeliverableUploader
import com.fuwaki.djifly.platform.attachment.PendingDeliverable
import com.fuwaki.djifly.platform.live.RiderLiveRequestMapper
import com.fuwaki.djifly.platform.live.RiderLiveRequestState
import com.fuwaki.djifly.platform.registration.PlatformRegistrationStore
import com.fuwaki.djifly.platform.network.PlatformApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
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
    private val registrationStore: PlatformRegistrationStore,
    private val deliverableUploader: DeliverableUploader,
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

    // 飞手主动开播（1B-4b，Q3=C）
    private val _liveRequestState = MutableStateFlow<RiderLiveRequestState>(RiderLiveRequestState.Idle)
    val liveRequestState: StateFlow<RiderLiveRequestState> = _liveRequestState

    // 交付物上传（1B-9b）：完成对话框内选择 → 串行直传 → 与任务关联
    private val _deliverables = MutableStateFlow<List<DeliverableItemUi>>(emptyList())
    val deliverables: StateFlow<List<DeliverableItemUi>> = _deliverables

    private val _attachments = MutableStateFlow<List<com.fuwaki.djifly.platform.network.AttachmentItem>>(emptyList())
    val attachments: StateFlow<List<com.fuwaki.djifly.platform.network.AttachmentItem>> = _attachments

    private var deliverableSeq = 0

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

    /** 完成任务（t32 契约：note 可选完成说明，≤500 字） */
    fun completeTask(taskNum: String, note: String? = null) {
        viewModelScope.launch {
            _actionState.value = ActionState.Loading
            apiCall(errorHandler) { api.completeTask(taskNum, note) }
                .onSuccess {
                    _actionState.value = ActionState.Success("任务已完成")
                    loadMyTasks()
                }
                .onError { error ->
                    _actionState.value = ActionState.Error(error.message)
                }
        }
    }

    /**
     * 飞手主动开播（1B-4b）：对本人在平台注册且绑定的在线设备下发开播。
     * deviceId 取最近注册快照的 serialNumber；未注册直接本地报错（与后端 404 语义一致）。
     */
    fun requestRiderLiveStream() {
        viewModelScope.launch {
            _liveRequestState.value = RiderLiveRequestState.Loading
            val deviceId = registrationStore.latestRegistration.firstOrNull()?.serialNumber
            if (deviceId.isNullOrBlank()) {
                _liveRequestState.value = RiderLiveRequestState.Error(
                    RiderLiveRequestMapper.userMessageFor(
                        com.fuwaki.djifly.data.error.AppError.Http(404, "设备未注册")
                    )
                )
                return@launch
            }
            apiCall(errorHandler) { api.requestRiderLive(deviceId) }
                .onSuccess { vo ->
                    _liveRequestState.value = RiderLiveRequestState.Success(
                        userMessage = RiderLiveRequestMapper.successMessage(vo),
                        liveState = vo.liveState,
                        alreadyRunning = RiderLiveRequestMapper.isAlreadyRunning(vo)
                    )
                }
                .onError { error ->
                    _liveRequestState.value = RiderLiveRequestState.Error(
                        RiderLiveRequestMapper.userMessageFor(error)
                    )
                }
        }
    }

    // -----------------------------------------------------------------------
    // 交付物上传（1B-9b）
    // -----------------------------------------------------------------------

    /** 添加待上传交付物；类型/大小预检不过直接以 FAILED 入列（文案即拒绝原因） */
    fun addDeliverable(fileName: String, contentType: String, sizeBytes: Long, openStream: () -> java.io.InputStream?) {
        val item = PendingDeliverable(
            id = "deliverable-${System.nanoTime()}-${deliverableSeq++}",
            fileName = fileName,
            contentType = contentType,
            sizeBytes = sizeBytes,
            openStream = openStream
        )
        val check = DeliverableRules.check(contentType, sizeBytes)
        val phase = if (check is DeliverableCheck.Ok) DeliverableItemPhase.PENDING else DeliverableItemPhase.FAILED
        val message = (check as? DeliverableCheck.Rejected)?.reason
        _deliverables.value = _deliverables.value + DeliverableItemUi(item, phase, message)
    }

    fun removeDeliverable(id: String) {
        _deliverables.value = _deliverables.value.filterNot { it.item.id == id }
    }

    fun retryDeliverable(id: String) {
        _deliverables.value = _deliverables.value.map {
            if (it.item.id == id && it.phase == DeliverableItemPhase.FAILED) {
                it.copy(phase = DeliverableItemPhase.PENDING, message = null)
            } else {
                it
            }
        }
    }

    fun clearDeliverables() {
        _deliverables.value = emptyList()
    }

    /** 是否存在阻塞完成任务状态的项目（待传/上传中/失败） */
    fun hasBlockingDeliverables(): Boolean =
        _deliverables.value.any {
            it.phase == DeliverableItemPhase.PENDING || it.phase == DeliverableItemPhase.UPLOADING || it.phase == DeliverableItemPhase.FAILED
        }

    /** 串行上传全部待传/失败项（多文件逐个，失败标 FAILED 不中断后续） */
    fun uploadAllDeliverables(taskNum: String) {
        viewModelScope.launch {
            val targets = _deliverables.value.filter {
                it.phase == DeliverableItemPhase.PENDING || it.phase == DeliverableItemPhase.FAILED
            }
            for (target in targets) {
                _deliverables.value = _deliverables.value.map {
                    if (it.item.id == target.item.id) {
                        it.copy(phase = DeliverableItemPhase.UPLOADING, message = null)
                    } else {
                        it
                    }
                }
                val outcome = deliverableUploader.upload(taskNum, target.item)
                _deliverables.value = _deliverables.value.map {
                    if (it.item.id != target.item.id) {
                        it
                    } else {
                        when (outcome) {
                            is DeliverableUploadOutcome.Uploaded -> it.copy(
                                phase = DeliverableItemPhase.UPLOADED,
                                objectKey = outcome.objectKey,
                                message = null
                            )
                            is DeliverableUploadOutcome.Rejected -> it.copy(
                                phase = DeliverableItemPhase.FAILED,
                                message = outcome.reason
                            )
                            is DeliverableUploadOutcome.Failed -> it.copy(
                                phase = DeliverableItemPhase.FAILED,
                                message = outcome.reason
                            )
                        }
                    }
                }
            }
            refreshAttachments(taskNum)
        }
    }

    /** 刷新附件列表（成果归档确认，任务所有者/飞手可见） */
    fun refreshAttachments(taskNum: String) {
        viewModelScope.launch {
            runCatching { api.getAttachments(taskNum) }.onSuccess { response ->
                if (response.isSuccessful) {
                    _attachments.value = response.body()?.data.orEmpty()
                }
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
            val request = ApiSessionDTO(
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