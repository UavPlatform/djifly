package com.fuwaki.djifly.platform.registration

/** Local DJI aircraft identity. No server registration or connection lease is represented here. */
sealed interface AircraftIdentityState {
    data object WaitingForAircraft : AircraftIdentityState

    data class Registered(
        val serialNumber: String,
        val droneName: String,
        val controllerModel: String,
    ) : AircraftIdentityState
}
