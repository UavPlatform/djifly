package com.fuwaki.djifly.platform.livestream

import javax.inject.Inject
import javax.inject.Singleton

/** Stops the local TRTC session and the order-level HTTP lease. */
@Singleton
class LiveStopCoordinator @Inject constructor(
    private val liveService: DjiTrtcLiveService,
    private val dispatchLivePublisher: DispatchLivePublisher,
) {
    fun stopStreamingAndNotify(): Boolean {
        val orderLiveStopped = dispatchLivePublisher.stopNow()
        if (!liveService.isSessionActive) return orderLiveStopped
        liveService.stop()
        return true
    }
}
