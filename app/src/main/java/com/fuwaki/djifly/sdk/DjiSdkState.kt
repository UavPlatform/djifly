package com.fuwaki.djifly.sdk

import dji.sdk.keyvalue.value.product.ProductType

/**
 * DJI SDK connection and registration state
 */
sealed class SdkConnectionState {
    /** SDK is initializing */
    data object Initializing : SdkConnectionState()

    /** SDK registration successful */
    data object Registered : SdkConnectionState()

    /** SDK registration failed */
    data class RegistrationFailed(val error: String) : SdkConnectionState()

    /** Product connected */
    data class ProductConnected(val productName: String, val productType: ProductType) : SdkConnectionState()

    /** Product disconnected */
    data object ProductDisconnected : SdkConnectionState()
}

/**
 * Product information
 */
data class ProductInfo(
    val name: String = "Unknown",
    val type: ProductType = ProductType.UNKNOWN,
    val isConnected: Boolean = false,
    val firmwareVersion: String = "N/A",
    val serialNumber: String = "N/A"
)

/**
 * DJI SDK status information
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
            is SdkConnectionState.Initializing -> "SDK Initializing..."
            is SdkConnectionState.Registered -> "SDK Registered - Waiting for Product"
            is SdkConnectionState.RegistrationFailed -> "SDK Registration Failed: ${(connectionState as SdkConnectionState.RegistrationFailed).error}"
            is SdkConnectionState.ProductConnected -> "Product Connected: ${(connectionState as SdkConnectionState.ProductConnected).productName}"
            is SdkConnectionState.ProductDisconnected -> "Product Disconnected"
        }
}
