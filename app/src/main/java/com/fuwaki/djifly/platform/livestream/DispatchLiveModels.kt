package com.fuwaki.djifly.platform.livestream

data class DispatchLiveStart(val requestKey: String)
data class DispatchLiveHeartbeat(val state: String)

data class DispatchLiveTicket(
    val liveId: String?,
    val connectionId: String?,
    val sdkAppId: Int?,
    val roomId: String?,
    val userId: String?,
    val userSig: String?,
    val privateMapKey: String?
)

data class DispatchLiveStatus(val state: String?)
