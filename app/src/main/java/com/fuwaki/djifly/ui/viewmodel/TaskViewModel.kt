package com.fuwaki.djifly.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuwaki.djifly.data.error.AppErrorMapping
import com.fuwaki.djifly.data.error.GlobalErrorHandler
import com.fuwaki.djifly.data.error.apiCall
import com.fuwaki.djifly.data.models.TaskVo
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

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val api: PlatformApiService,
    private val registrationStore: PlatformRegistrationStore,
    private val deliverableUploader: DeliverableUploader,
    val errorHandler: GlobalErrorHandler // 暴露给 UI 层的 GlobalErrorEffect 使用
) : ViewModel() {

    // Task Detail
    private val _taskDetail = MutableStateFlow<TaskVo?>(null)
    val taskDetail: StateFlow<TaskVo?> = _taskDetail
    private val _detailLoading = MutableStateFlow(false)
    val detailLoading: StateFlow<Boolean> = _detailLoading

    // Actions state
    private val _actionState = MutableStateFlow<ActionState>(ActionState.Idle)
    val actionState: StateFlow<ActionState> = _actionState

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

    /** 完成任务（t32 契约：note 可选完成说明，≤500 字；须在飞行页上传送达凭证后调用） */
    fun completeTask(taskNum: String, note: String? = null) {
        viewModelScope.launch {
            _actionState.value = ActionState.Loading
            apiCall(errorHandler) { api.completeTask(taskNum, note) }
                .onSuccess {
                    _actionState.value = ActionState.Success("已提交送达，等待用户验收")
                }
                .onError { error ->
                    _actionState.value = ActionState.Error(AppErrorMapping.userMessageFor(error))
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

    /** 添加当前图传 JPEG 缓存，通过服务端上传确认后才可送达（REQ-DJIFLY-001）。 */
    fun addDeliverableFromPhotoFile(file: java.io.File, fileName: String) {
        addDeliverable(
            fileName = fileName,
            contentType = "image/jpeg",
            sizeBytes = file.length(),
            openStream = { file.inputStream() },
            onDiscard = { file.delete(); Unit }
        )
    }

    fun hasSuccessfulDeliveryUploadInSession(): Boolean =
        _deliverables.value.any { it.phase == DeliverableItemPhase.UPLOADED }

    fun addDeliverable(fileName: String, contentType: String, sizeBytes: Long, openStream: () -> java.io.InputStream?, onDiscard: () -> Unit = {}) {
        val item = PendingDeliverable(
            id = "deliverable-${System.nanoTime()}-${deliverableSeq++}",
            fileName = fileName,
            contentType = contentType,
            sizeBytes = sizeBytes,
            openStream = openStream,
            onDiscard = onDiscard
        )
        val check = DeliverableRules.check(contentType, sizeBytes)
        val phase = if (check is DeliverableCheck.Ok) DeliverableItemPhase.PENDING else DeliverableItemPhase.FAILED
        val message = (check as? DeliverableCheck.Rejected)?.reason
        _deliverables.value = _deliverables.value + DeliverableItemUi(item, phase, message)
    }

    fun removeDeliverable(id: String) {
        _deliverables.value.filter { it.item.id == id }.forEach { it.item.onDiscard() }
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
        _deliverables.value.forEach { it.item.onDiscard() }
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

    override fun onCleared() {
        _deliverables.value.forEach { it.item.onDiscard() }
        super.onCleared()
    }

    fun resetActionState() {
        _actionState.value = ActionState.Idle
    }
}