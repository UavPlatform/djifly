package com.fuwaki.djifly.sdk

import dji.sdk.keyvalue.value.product.ProductType

/**
 * DJI SDK 连接和注册状态
 */
sealed class SdkConnectionState {
    data object Initializing : SdkConnectionState()
    data object Registered : SdkConnectionState()
    data class RegistrationFailed(val error: String) : SdkConnectionState()
    data class ProductConnected(val productName: String, val productType: ProductType) : SdkConnectionState()
    data object ProductDisconnected : SdkConnectionState()
}

/**
 * SDK 自身详细信息
 */
data class MsdkInfo(
    val sdkVersion: String = "N/A",
    val buildVersion: String = "N/A",
    val isDebug: Boolean = false,
    val isLdmEnabled: Boolean = false,
    val isLdmLicenseLoaded: Boolean = false,
    val countryCode: String = "N/A",
    val networkInfo: String = "Unknown"
)

/**
 * 实时飞行数据状态 (由 DjiSdkManager 更新)
 */
data class FlightDataState(
    val batteryPercentage: Int = 0,
    val gpsSatelliteCount: Int = 0,
    val rtkSatelliteCount: Int = 0,
    val uplinkQuality: Int = 0,   // RC 信号
    val downlinkQuality: Int = 0, // 图传信号
    val isVisionSystemHealthy: Boolean = true,
    val isRtkHealthy: Boolean = false,
    val flightTimeRemaining: Int = 0 // 剩余飞行时间（秒）
)

/**
 * 产品/无人机硬件详细信息
 */
data class ProductInfo(
    val name: String = "未知设备",
    val type: ProductType = ProductType.UNKNOWN,
    val isConnected: Boolean = false,
    val firmwareVersion: String = "N/A",
    val serialNumber: String = "N/A"
)

/**
 * DJI SDK 状态信息汇总
 */
data class DjiSdkStatus(
    val connectionState: SdkConnectionState = SdkConnectionState.Initializing,
    val msdkInfo: MsdkInfo = MsdkInfo(),
    val productInfo: ProductInfo = ProductInfo(),
    val flightData: FlightDataState = FlightDataState(),
    val initProgress: Int = 0,
    val databaseDownloadProgress: Float = 0f
) {
    val isProductConnected: Boolean
        get() = connectionState is SdkConnectionState.ProductConnected

    val sdkStatusText: String
        get() = when (connectionState) {
            is SdkConnectionState.Initializing -> "初始化"
            is SdkConnectionState.Registered -> "已就绪"
            is SdkConnectionState.RegistrationFailed -> "异常"
            is SdkConnectionState.ProductConnected -> "已连接"
            is SdkConnectionState.ProductDisconnected -> "断连"
        }
}
