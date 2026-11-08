package com.fuwaki.djifly.platform.registration

import android.os.Build
import com.fuwaki.djifly.di.ApplicationScope
import com.fuwaki.djifly.platform.telemetry.DroneModelMapper
import com.fuwaki.djifly.sdk.DjiSdkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local aircraft identity only. The old server registration and device-session lifecycle were
 * removed; the backend authorizes event packets and live sessions by order and pilot.
 */
@Singleton
class AircraftIdentityManager @Inject constructor(
    private val sdkManager: DjiSdkManager,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<AircraftIdentityState>(AircraftIdentityState.WaitingForAircraft)
    val state: StateFlow<AircraftIdentityState> = _state.asStateFlow()
    private var observer: Job? = null

    fun start() {
        if (observer != null) return
        observer = scope.launch {
            sdkManager.sdkStatus.collect { status ->
                val serial = DroneModelMapper.validSerial(status.productInfo.serialNumber)
                _state.value = if (!status.isProductConnected || serial == null) {
                    AircraftIdentityState.WaitingForAircraft
                } else {
                    AircraftIdentityState.Registered(
                        serialNumber = serial,
                        droneName = status.productInfo.name.ifBlank { serial },
                        controllerModel = status.productInfo.controllerModel.ifBlank { Build.MODEL },
                    )
                }
            }
        }
    }

    fun retryNow() = start()
}
