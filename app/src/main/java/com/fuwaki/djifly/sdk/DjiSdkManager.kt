package com.fuwaki.djifly.sdk

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import dji.sdk.keyvalue.key.AirLinkKey
import dji.sdk.keyvalue.key.BatteryKey
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.ProductKey
import dji.sdk.keyvalue.key.RemoteControllerKey
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.sdk.keyvalue.value.common.LocationCoordinate2D
import dji.sdk.keyvalue.value.common.Velocity3D
import dji.sdk.keyvalue.value.product.ProductType
import dji.sdk.keyvalue.value.flightcontroller.LowBatteryRTHInfo
import dji.v5.common.error.IDJIError
import dji.v5.common.register.DJISDKInitEvent
import dji.v5.et.create
import dji.v5.et.get
import dji.v5.et.listen
import dji.v5.et.action
import dji.v5.manager.SDKManager
import dji.v5.manager.interfaces.SDKManagerCallback
import dji.v5.manager.aircraft.perception.PerceptionManager
import dji.v5.manager.aircraft.perception.listener.PerceptionInformationListener
import dji.v5.manager.KeyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DjiSdkManager @Inject constructor() {

    private val TAG = "DjiSdkManager"
    private val _sdkStatus = MutableStateFlow(DjiSdkStatus())
    val sdkStatus: StateFlow<DjiSdkStatus> = _sdkStatus.asStateFlow()

    private var currentProductInfo = ProductInfo()
    private var currentFlightData = FlightDataState()

    var initProgress by mutableIntStateOf(0)
        private set

    private val perceptionListener = PerceptionInformationListener { info ->
        updateFlightData { data -> data.copy(isVisionSystemHealthy = info.isVisionPositioningEnabled) }
    }

    fun initSdk(context: Context) {
        SDKManager.getInstance().init(context, object : SDKManagerCallback {
            override fun onInitProcess(event: DJISDKInitEvent?, totalProcess: Int) {
                initProgress = totalProcess
                updateSdkStatus { copy(initProgress = totalProcess) }
                if (event == DJISDKInitEvent.INITIALIZE_COMPLETE) {
                    SDKManager.getInstance().registerApp()
                }
            }

            override fun onRegisterSuccess() {
                updateSdkStatus { copy(connectionState = SdkConnectionState.Registered) }
            }

            override fun onRegisterFailure(error: IDJIError?) {
                updateSdkStatus { copy(connectionState = SdkConnectionState.RegistrationFailed(error?.toString() ?: "")) }
            }

            override fun onProductConnect(productId: Int) {
                fetchProductDetails()
                startListeningToFlightData()
            }

            override fun onProductDisconnect(productId: Int) {
                currentProductInfo = ProductInfo()
                updateSdkStatus {
                    copy(connectionState = SdkConnectionState.ProductDisconnected, productInfo = currentProductInfo)
                }
                stopListeningToFlightData()
            }

            override fun onProductChanged(productId: Int) { fetchProductDetails() }
            override fun onDatabaseDownloadProgress(current: Long, total: Long) {}
        })
    }

    private fun startListeningToFlightData() {
        BatteryKey.KeyChargeRemainingInPercent.create(ComponentIndexType.LEFT_OR_MAIN).listen(this) { percent: Int? ->
            percent?.let {
                updateFlightData { data -> data.copy(batteryPercentage = it) }
            }
        }
        FlightControllerKey.KeyGPSSatelliteCount.create().listen(this) { count: Int? ->
            count?.let {
                updateFlightData { data -> data.copy(gpsSatelliteCount = it) }
            }
        }
        AirLinkKey.KeyUpLinkQualityRaw.create().listen(this) { quality: Int? ->
            quality?.let {
                updateFlightData { data -> data.copy(uplinkQuality = it) }
            }
        }
        AirLinkKey.KeyDownLinkQualityRaw.create().listen(this) { quality: Int? ->
            quality?.let {
                updateFlightData { data -> data.copy(downlinkQuality = it) }
            }
        }
        // 监听剩余飞行时间
        FlightControllerKey.KeyLowBatteryRTHInfo.create().listen(this) { info: LowBatteryRTHInfo? ->
            info?.let {
                updateFlightData { data -> data.copy(flightTimeRemaining = it.remainingFlightTime) }
            }
        }
        // 监听飞行器经纬度（LocationCoordinate2D，度）
        FlightControllerKey.KeyAircraftLocation.create().listen(this) { location: LocationCoordinate2D? ->
            updateFlightData { data ->
                FlightTelemetry.applyLocation(data, location?.latitude, location?.longitude)
            }
        }
        // 监听相对起飞点高度（米）
        FlightControllerKey.KeyAltitude.create().listen(this) { altitude: Double? ->
            updateFlightData { data ->
                FlightTelemetry.applyAltitude(data, altitude)
            }
        }
        // 监听三维速度，上报水平合速度（米/秒）
        FlightControllerKey.KeyAircraftVelocity.create().listen(this) { velocity: Velocity3D? ->
            updateFlightData { data ->
                FlightTelemetry.applyVelocity(data, velocity?.x, velocity?.y)
            }
        }
        PerceptionManager.getInstance().addPerceptionInformationListener(perceptionListener)
    }

    private fun stopListeningToFlightData() {
        KeyManager.getInstance().cancelListen(this)
        PerceptionManager.getInstance().removePerceptionInformationListener(perceptionListener)
    }

    /**
     * 统一的飞行数据更新入口：先更新 [currentFlightData]，再同步到 [sdkStatus]。
     */
    private fun updateFlightData(update: (FlightDataState) -> FlightDataState) {
        currentFlightData = update(currentFlightData)
        updateSdkStatus { copy(flightData = currentFlightData) }
    }

    /**
     * 注入模拟遥测（真机不可用期间验证 UAV_STATUS 上报协议与数据流，
     * 见打磨路线图 1A-4 风险表；单测覆盖见 app/src/test 的 FlightTelemetryTest 与
     * UavStatusPayloadFactoryTest——两者经由同一份 FlightTelemetry.applySimulated 逻辑）。
     *
     * 注意：会覆盖 Key 监听写入的真实遥测值，仅限调试/测试场景调用。
     * 高度单位米，速度单位米/秒，经纬度单位度（WGS84）。
     */
    fun injectSimulatedTelemetry(
        latitude: Double,
        longitude: Double,
        altitude: Double,
        speed: Double
    ) {
        updateFlightData { data ->
            FlightTelemetry.applySimulated(data, latitude, longitude, altitude, speed)
        }
    }

    fun performTakeOff() {
        FlightControllerKey.KeyStartTakeoff.create().action({
            Log.d(TAG, "Takeoff action success")
        }, { error ->
            Log.e(TAG, "Takeoff action failed: $error")
        })
    }

    fun performRTH() {
        FlightControllerKey.KeyStartGoHome.create().action({
            Log.d(TAG, "RTH action success")
        }, { error ->
            Log.e(TAG, "RTH action failed: $error")
        })
    }

    private fun fetchProductDetails() {
        ProductKey.KeyProductType.create().get({ type: ProductType? ->
            val nonNullType = type ?: ProductType.UNKNOWN
            currentProductInfo = currentProductInfo.copy(
                type = nonNullType,
                name = nonNullType.name,
                isConnected = true
            )
            updateSdkStatus {
                copy(connectionState = SdkConnectionState.ProductConnected(nonNullType.name, nonNullType), productInfo = currentProductInfo)
            }
        }, { error -> Log.e(TAG, "fetchProductDetails failed: $error") })

        ProductKey.KeyFirmwareVersion.create().get({ firmwareVersion: String? ->
            currentProductInfo = currentProductInfo.copy(
                firmwareVersion = firmwareVersion.orUnavailable()
            )
            updateSdkStatus { copy(productInfo = currentProductInfo) }
        }, { error -> Log.e(TAG, "fetchProduct firmware failed: $error") })

        FlightControllerKey.KeySerialNumber.create().get({ serialNumber: String? ->
            val normalized = serialNumber.normalizeDeviceValue()
            if (normalized != null) {
                currentProductInfo = currentProductInfo.copy(serialNumber = normalized)
                updateSdkStatus { copy(productInfo = currentProductInfo) }
            } else {
                fetchProductSerialFallback()
            }
        }, { error ->
            Log.e(TAG, "fetch flight controller serial failed: $error")
            fetchProductSerialFallback()
        })

        RemoteControllerKey.KeyRemoteControllerType.create().get({ remoteControllerType ->
            currentProductInfo = currentProductInfo.copy(
                controllerModel = remoteControllerType?.name.orUnavailable(fallback = Build.MODEL)
            )
            updateSdkStatus { copy(productInfo = currentProductInfo) }
        }, { error ->
            Log.e(TAG, "fetch remote controller type failed: $error")
            currentProductInfo = currentProductInfo.copy(controllerModel = Build.MODEL.orUnavailable())
            updateSdkStatus { copy(productInfo = currentProductInfo) }
        })
    }

    private fun fetchProductSerialFallback() {
        ProductKey.KeySerialNumber.create().get({ serialNumber: String? ->
            currentProductInfo = currentProductInfo.copy(
                serialNumber = serialNumber.orUnavailable()
            )
            updateSdkStatus { copy(productInfo = currentProductInfo) }
        }, { error -> Log.e(TAG, "fetch product serial fallback failed: $error") })
    }

    private fun updateSdkStatus(update: DjiSdkStatus.() -> DjiSdkStatus) {
        _sdkStatus.value = _sdkStatus.value.update()
    }

    private fun String?.normalizeDeviceValue(): String? {
        val value = this?.trim().orEmpty()
        if (value.isBlank()) return null
        if (value.equals("N/A", ignoreCase = true)) return null
        if (value.equals("UNKNOWN", ignoreCase = true)) return null
        return value
    }

    private fun String?.orUnavailable(fallback: String = "N/A"): String {
        return normalizeDeviceValue() ?: fallback
    }
}
