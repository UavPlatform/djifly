package com.fuwaki.djifly.platform.registration

import android.os.Build
import android.util.Log
import com.fuwaki.djifly.platform.connection.PlatformConnectionSessionResult
import com.fuwaki.djifly.platform.connection.PlatformConnectionSessionService
import com.fuwaki.djifly.di.ApplicationScope
import com.fuwaki.djifly.sdk.DjiSdkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlatformRegistrationManager @Inject constructor(
    private val sdkManager: DjiSdkManager,
    private val registrationRepository: PlatformRegistrationRepository,
    private val connectionSessionService: PlatformConnectionSessionService,
    private val store: PlatformRegistrationStore,
    @ApplicationScope private val externalScope: CoroutineScope
) {

    private val tag = "PlatformRegManager"
    private val _state = MutableStateFlow<PlatformRegistrationState>(PlatformRegistrationState.WaitingForAircraft)
    val state: StateFlow<PlatformRegistrationState> = _state.asStateFlow()

    private val registeredSnapshotsThisSession = mutableMapOf<String, RegisteredDroneSnapshot>()
    private var observerJob: Job? = null
    private var activeSessionJob: Job? = null
    private var activeSerialNumber: String? = null

    fun start() {
        if (observerJob != null) return

        observerJob = externalScope.launch {
            sdkManager.sdkStatus
                .map { status ->
                    RegistrationTrigger(
                        isConnected = status.isProductConnected,
                        serialNumber = status.productInfo.serialNumber.normalizedValue(),
                        controllerModel = status.productInfo.controllerModel.normalizedValue() ?: Build.MODEL
                    )
                }
                .distinctUntilChanged()
                .collectLatest { trigger ->
                    if (!trigger.isConnected) {
                        stopActiveSession()
                        _state.value = PlatformRegistrationState.WaitingForAircraft
                        return@collectLatest
                    }

                    val serialNumber = trigger.serialNumber
                    if (serialNumber == null) {
                        stopActiveSession()
                        _state.value = PlatformRegistrationState.WaitingForAircraft
                        return@collectLatest
                    }

                    if (activeSerialNumber == serialNumber && activeSessionJob?.isActive == true) {
                        return@collectLatest
                    }

                    stopActiveSession()
                    activeSerialNumber = serialNumber
                    activeSessionJob = externalScope.launch {
                        managePlatformSession(
                            serialNumber = serialNumber,
                            controllerModel = trigger.controllerModel
                        )
                    }
                }
        }
    }

    fun retryNow() {
        val status = sdkManager.sdkStatus.value
        if (!status.isProductConnected) {
            _state.value = PlatformRegistrationState.WaitingForAircraft
            return
        }

        val serialNumber = status.productInfo.serialNumber.normalizedValue()
        if (serialNumber == null) {
            _state.value = PlatformRegistrationState.WaitingForAircraft
            return
        }

        val controllerModel = status.productInfo.controllerModel.normalizedValue() ?: Build.MODEL
        stopActiveSession()
        activeSerialNumber = serialNumber
        activeSessionJob = externalScope.launch {
            managePlatformSession(
                serialNumber = serialNumber,
                controllerModel = controllerModel
            )
        }
    }

    private suspend fun managePlatformSession(
        serialNumber: String,
        controllerModel: String
    ) {
        val snapshot = ensureRegistered(
            serialNumber = serialNumber,
            controllerModel = controllerModel
        ) ?: return

        _state.value = PlatformRegistrationState.Registering(serialNumber)

        when (
            val result = connectionSessionService.maintainRegisteredSession(
                serialNumber = serialNumber,
                snapshot = snapshot,
                onConnected = {
                    _state.value = PlatformRegistrationState.Registered(
                        serialNumber = serialNumber,
                        droneId = snapshot.droneId,
                        droneName = snapshot.droneName,
                        controllerModel = controllerModel,
                        createdOnServer = snapshot.createdOnServer
                    )
                },
                onRetryScheduled = { nextAttempt, retryAfterSeconds, lastError ->
                    _state.value = PlatformRegistrationState.RetryScheduled(
                        serialNumber = serialNumber,
                        nextAttempt = nextAttempt,
                        retryAfterSeconds = retryAfterSeconds,
                        lastError = lastError
                    )
                }
            )
        ) {
            PlatformConnectionSessionResult.Stopped -> Unit
            is PlatformConnectionSessionResult.Failed -> {
                _state.value = PlatformRegistrationState.Failed(
                    serialNumber = serialNumber,
                    reason = result.reason,
                    retryable = result.retryable
                )
            }
        }
    }

    private suspend fun ensureRegistered(
        serialNumber: String,
        controllerModel: String
    ): RegisteredDroneSnapshot? {
        registeredSnapshotsThisSession[serialNumber]?.let { return it }

        for ((index, retryDelayMillis) in RETRY_DELAYS_MS.withIndex()) {
            val attempt = index + 1
            _state.value = PlatformRegistrationState.Registering(serialNumber)

            when (val result = registrationRepository.registerDrone(serialNumber, controllerModel)) {
                is PlatformRegistrationResult.Success -> {
                    val snapshot = RegisteredDroneSnapshot(
                        serialNumber = serialNumber,
                        droneId = result.droneId,
                        droneName = result.droneName,
                        controllerModel = controllerModel,
                        createdOnServer = result.createdOnServer,
                        lastRegisteredAtMillis = System.currentTimeMillis()
                    )
                    store.save(snapshot)
                    registeredSnapshotsThisSession[serialNumber] = snapshot
                    return snapshot
                }

                is PlatformRegistrationResult.Failure -> {
                    Log.w(tag, "register attempt $attempt failed for $serialNumber: ${result.message}")
                    val hasNextAttempt = result.retryable && attempt < RETRY_DELAYS_MS.size
                    if (!hasNextAttempt) {
                        _state.value = PlatformRegistrationState.Failed(
                            serialNumber = serialNumber,
                            reason = result.message,
                            retryable = result.retryable
                        )
                        return null
                    }

                    _state.value = PlatformRegistrationState.RetryScheduled(
                        serialNumber = serialNumber,
                        nextAttempt = attempt + 1,
                        retryAfterSeconds = retryDelayMillis / 1000,
                        lastError = result.message
                    )
                    delay(retryDelayMillis)
                }
            }
        }
        return null
    }

    private fun stopActiveSession() {
        activeSessionJob?.cancel()
        activeSessionJob = null
        connectionSessionService.disconnect()
        activeSerialNumber = null
    }

    private fun String?.normalizedValue(): String? {
        val value = this?.trim().orEmpty()
        if (value.isBlank()) return null
        if (value.equals("N/A", ignoreCase = true)) return null
        if (value.equals("UNKNOWN", ignoreCase = true)) return null
        return value
    }

    private data class RegistrationTrigger(
        val isConnected: Boolean,
        val serialNumber: String?,
        val controllerModel: String
    )

    private companion object {
        val RETRY_DELAYS_MS = listOf(3_000L, 10_000L, 30_000L)
    }
}
