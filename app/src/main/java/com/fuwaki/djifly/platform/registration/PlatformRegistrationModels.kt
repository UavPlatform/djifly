package com.fuwaki.djifly.platform.registration

data class RegisteredDroneSnapshot(
    val serialNumber: String,
    val droneId: Long?,
    val droneName: String,
    val controllerModel: String,
    val createdOnServer: Boolean,
    val lastRegisteredAtMillis: Long
)

sealed interface PlatformRegistrationState {
    data object WaitingForAircraft : PlatformRegistrationState
    data class Registering(val serialNumber: String) : PlatformRegistrationState
    data class RetryScheduled(
        val serialNumber: String,
        val nextAttempt: Int,
        val retryAfterSeconds: Long,
        val lastError: String
    ) : PlatformRegistrationState
    data class Registered(
        val serialNumber: String,
        val droneId: Long?,
        val droneName: String,
        val controllerModel: String,
        val createdOnServer: Boolean
    ) : PlatformRegistrationState
    data class Failed(
        val serialNumber: String?,
        val reason: String,
        val retryable: Boolean
    ) : PlatformRegistrationState
}

sealed interface PlatformRegistrationResult {
    data class Success(
        val droneId: Long?,
        val droneName: String,
        val createdOnServer: Boolean
    ) : PlatformRegistrationResult
    data class Failure(
        val message: String,
        val retryable: Boolean
    ) : PlatformRegistrationResult
}
