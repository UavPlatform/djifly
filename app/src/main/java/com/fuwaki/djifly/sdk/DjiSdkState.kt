package com.fuwaki.djifly.sdk

import dji.sdk.keyvalue.value.product.ProductType

/**
 * DJI SDK 连接和注册状态
 */
sealed class SdkConnectionState {
    /** SDK 正在初始化 */
    data object Initializing : SdkConnectionState()

    /** SDK 注册成功 */
    data object Registered : SdkConnectionState()

    /** SDK 注册失败 */
    data class RegistrationFailed(val error: String) : SdkConnectionState()

    /** 产品已连接 */
    data class ProductConnected(val productName: String, val productType: ProductType) : SdkConnectionState()

    /** 产品已断开 */
    data object ProductDisconnected : SdkConnectionState()
}

/**
 * 产品信息
 */
data class ProductInfo(
    val name: String = "未知设备",
    val type: ProductType = ProductType.UNKNOWN,
    val isConnected: Boolean = false,
    val firmwareVersion: String = "N/A",
    val serialNumber: String = "N/A"
)

/**
 * DJI SDK 状态信息
 */
data class DjiSdkStatus(
    val connectionState: SdkConnectionState = SdkConnectionState.Initializing,
    val productInfo: ProductInfo = ProductInfo(),
    val initProgress: Int = 0,
    val databaseDownloadProgress: Float = 0f
) {
    val isProductConnected: Boolean
        get() = connectionState is SdkConnectionState.ProductConnected

    val sdkStatusText: String
        get() = when (connectionState) {
            is SdkConnectionState.Initializing -> "SDK 初始化中..."
            is SdkConnectionState.Registered -> "SDK 已注册 - 等待连接"
            is SdkConnectionState.RegistrationFailed -> "SDK 注册失败: ${(connectionState as SdkConnectionState.RegistrationFailed).error}"
            is SdkConnectionState.ProductConnected -> "设备已就绪: ${(connectionState as SdkConnectionState.ProductConnected).productName}"
            is SdkConnectionState.ProductDisconnected -> "设备连接已断开"
        }
}
