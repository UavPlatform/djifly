package com.fuwaki.djifly.platform.network

import com.google.gson.annotations.SerializedName

enum class PlatformWsType {
    @SerializedName("command")
    COMMAND,

    @SerializedName("event")
    EVENT,

    @SerializedName("response")
    RESPONSE,

    @SerializedName("error")
    ERROR
}

enum class PlatformWsName {
    CONNECT_SUCCESS,
    START_LIVE,
    LIVE_STARTED,
    LIVE_STOPPED,
    PING,
    PONG,
    UAV_STATUS,
    UAV_STATUS_UPDATE,
    ERROR
}

data class PlatformWsEnvelope<T>(
    val id: String? = null,
    val type: PlatformWsType,
    val name: PlatformWsName,
    val replyTo: String? = null,
    val deviceId: String? = null,
    val timestamp: Long,
    val success: Boolean? = null,
    val code: String? = null,
    val message: String? = null,
    val data: T? = null
)

data class PlatformStartLivePayload(
    val roomId: String,
    val userId: String,
    val userSig: String
)

data class PlatformLiveStartedPayload(
    val roomId: String
)

data class PlatformUavStatusPayload(
    val deviceId: String,
    val uavId: Long? = null,
    val uavName: String,
    val longitude: Double,
    val latitude: Double,
    val altitude: Double,
    val speed: Double,
    val battery: Int,
    val flightStatus: Int,
    val operation: String? = null,
    val timestamp: Long
)

object PlatformWsEnvelopeFactory {
    fun ping(
        messageId: String,
        deviceId: String,
        timestamp: Long
    ): PlatformWsEnvelope<Unit> {
        return PlatformWsEnvelope(
            id = messageId,
            type = PlatformWsType.EVENT,
            name = PlatformWsName.PING,
            deviceId = deviceId,
            timestamp = timestamp
        )
    }

    fun uavStatus(
        messageId: String,
        payload: PlatformUavStatusPayload,
        timestamp: Long
    ): PlatformWsEnvelope<PlatformUavStatusPayload> {
        return PlatformWsEnvelope(
            id = messageId,
            type = PlatformWsType.EVENT,
            name = PlatformWsName.UAV_STATUS,
            deviceId = payload.deviceId,
            timestamp = timestamp,
            data = payload
        )
    }

    fun startLiveAccepted(
        replyTo: String,
        deviceId: String,
        message: String = "已接收开播命令",
        timestamp: Long
    ): PlatformWsEnvelope<Unit> {
        return PlatformWsEnvelope(
            type = PlatformWsType.RESPONSE,
            name = PlatformWsName.START_LIVE,
            replyTo = replyTo,
            deviceId = deviceId,
            timestamp = timestamp,
            success = true,
            code = "OK",
            message = message
        )
    }

    fun startLiveRejected(
        replyTo: String,
        deviceId: String,
        code: String,
        message: String,
        timestamp: Long
    ): PlatformWsEnvelope<Unit> {
        return PlatformWsEnvelope(
            type = PlatformWsType.ERROR,
            name = PlatformWsName.START_LIVE,
            replyTo = replyTo,
            deviceId = deviceId,
            timestamp = timestamp,
            success = false,
            code = code,
            message = message
        )
    }

    fun liveStarted(
        replyTo: String?,
        deviceId: String,
        roomId: String,
        timestamp: Long
    ): PlatformWsEnvelope<PlatformLiveStartedPayload> {
        return PlatformWsEnvelope(
            type = PlatformWsType.EVENT,
            name = PlatformWsName.LIVE_STARTED,
            replyTo = replyTo,
            deviceId = deviceId,
            timestamp = timestamp,
            success = true,
            data = PlatformLiveStartedPayload(roomId = roomId)
        )
    }

    fun liveStopped(
        deviceId: String,
        timestamp: Long
    ): PlatformWsEnvelope<Unit> {
        return PlatformWsEnvelope(
            type = PlatformWsType.EVENT,
            name = PlatformWsName.LIVE_STOPPED,
            deviceId = deviceId,
            timestamp = timestamp,
            success = true
        )
    }
}
