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
import com.fuwaki.djifly.platform.telemetry.DroneModelMapper
import com.fuwaki.djifly.platform.telemetry.UploadReportStatus
import com.fuwaki.djifly.platform.telemetry.UploadReportStatusResolver
import com.fuwaki.djifly.sdk.DjiSdkManager
import com.fuwaki.djifly.sdk.SdkConnectionState
import com.fuwaki.djifly.ui.components.AircraftBarUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 作业壳列表加载态：把失败与空列表拆开（DS-DJIFLY-002）。 */
sealed class HomeOrdersLoad {
    data object Loading : HomeOrdersLoad()
    data object Ready : HomeOrdersLoad()
    data class Failed(val message: String) : HomeOrdersLoad()
}

data class HomeUiState(
    val userName: String? = null,
    /** 作业页至多一单 */
    val currentOrder: ApiDispatchOrderVO? = null,
    val orderCannotFlyReason: String? = null,
    val load: HomeOrdersLoad = HomeOrdersLoad.Loading,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: PlatformApiService,
    private val tokenStore: TokenStore,
    private val dispatchTelemetry: DispatchTelemetryService,
    private val sdkManager: DjiSdkManager,
    val errorHandler: GlobalErrorHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    val dispatchState: StateFlow<DispatchFlightState> = dispatchTelemetry.state

    val aircraftBar: StateFlow<AircraftBarUi> = combine(
        sdkManager.sdkStatus,
        dispatchTelemetry.state,
        _uiState
    ) { sdk, dispatch, home ->
        val serial = DroneModelMapper.validSerial(sdk.productInfo.serialNumber)
        val connected = sdk.isProductConnected && serial != null
        val recognizing = sdk.connectionState is SdkConnectionState.ProductConnected && serial == null
        val hasOrder = home.currentOrder != null
        val upload = UploadReportStatusResolver.resolve(
            hasFlyableOrder = hasOrder,
            aircraftConfirmed = connected,
            recordingActive = dispatch.recordingActive,
            pendingPackets = dispatch.pendingPackets,
            lastError = dispatch.lastError,
        )
        val platformLabel = when {
            !connected -> "未连机"
            dispatch.recordingActive -> "已连接，正在记录"
            else -> "已连接，等待记录"
        }
        val next = when {
            !connected && recognizing -> "正在读取机身号，请稍候"
            !connected -> "请先连上飞机；未确认飞机不能进入飞行页"
            hasOrder && home.orderCannotFlyReason != null -> home.orderCannotFlyReason
            !hasOrder -> "暂无可飞订单"
            else -> null
        }
        AircraftBarUi(
            connected = connected,
            recognizing = recognizing,
            modelName = sdk.productInfo.name.takeUnless { it.isBlank() || it == "未知设备" },
            serialNumber = serial,
            batteryPercent = sdk.flightData.batteryPercentage.takeIf { sdk.flightData.batteryKnown },
            platformLabel = platformLabel,
            nextStepHint = next,
            uploadStatus = upload,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AircraftBarUi(
            connected = false,
            platformLabel = "未连机",
            uploadStatus = UploadReportStatusResolver.resolve(
                hasFlyableOrder = false,
                aircraftConfirmed = false,
                recordingActive = false,
                pendingPackets = 0,
                lastError = null,
            ),
        )
    )

    val aircraftConfirmed: StateFlow<Boolean> = aircraftBar
        .map { it.connected }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init { loadHomeData() }

    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(load = HomeOrdersLoad.Loading)
            val userName = tokenStore.userName.firstOrNull()
            runCatching { dispatchTelemetry.refreshOrders() }
            val result = runCatching { api.getMyDispatchOrders() }
            val response = result.getOrNull()
            if (response == null || !response.isSuccessful) {
                val message = when {
                    result.exceptionOrNull() != null ->
                        "网络异常，拉不到订单列表。请检查网络后重试。"
                    else ->
                        "订单加载失败（HTTP ${response?.code()}），请重试。"
                }
                _uiState.value = HomeUiState(
                    userName = userName,
                    currentOrder = null,
                    orderCannotFlyReason = null,
                    load = HomeOrdersLoad.Failed(message),
                )
                return@launch
            }
            val all = response.body()?.data.orEmpty()
            val flyable = all.filter { DispatchStages.isFlyable(it) }
            val current = DispatchStages.current(flyable) ?: flyable.firstOrNull()
            _uiState.value = HomeUiState(
                userName = userName,
                currentOrder = current,
                orderCannotFlyReason = null,
                load = HomeOrdersLoad.Ready,
            )
        }
    }
}
