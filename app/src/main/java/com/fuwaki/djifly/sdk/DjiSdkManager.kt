package com.fuwaki.djifly.sdk

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import dji.sdk.keyvalue.key.ProductKey
import dji.sdk.keyvalue.value.product.ProductType
import dji.v5.common.error.IDJIError
import dji.v5.common.register.DJISDKInitEvent
import dji.v5.et.create
import dji.v5.et.get
import dji.v5.manager.SDKManager
import dji.v5.manager.interfaces.SDKManagerCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages DJI SDK connection and provides SDK status to the UI
 */
class DjiSdkManager private constructor() {

    private val TAG = "DjiSdkManager"

    // SDK status state
    private val _sdkStatus = MutableStateFlow(DjiSdkStatus())
    val sdkStatus: StateFlow<DjiSdkStatus> = _sdkStatus.asStateFlow()

    // Current product info
    private var currentProductInfo = ProductInfo()

    // Initialization progress
    var initProgress by mutableIntStateOf(0)
        private set

    // Database download progress
    var databaseProgress by mutableStateOf(0f)
        private set

    /**
     * Initialize DJI SDK
     */
    fun initSdk(context: Context) {
        Log.i(TAG, "Initializing DJI SDK...")

        SDKManager.getInstance().init(context, object : SDKManagerCallback {
            override fun onInitProcess(event: DJISDKInitEvent?, totalProcess: Int) {
                Log.i(TAG, "onInitProcess: event=$event, totalProcess=$totalProcess")
                initProgress = totalProcess
                updateSdkStatus {
                    copy(initProgress = totalProcess)
                }

                if (event == DJISDKInitEvent.INITIALIZE_COMPLETE) {
                    SDKManager.getInstance().registerApp()
                    updateSdkStatus {
                        copy(
                            connectionState = SdkConnectionState.Initializing,
                            initProgress = 100
                        )
                    }
                }
            }

            override fun onRegisterSuccess() {
                Log.i(TAG, "onRegisterSuccess: DJI SDK registered successfully")
                updateSdkStatus {
                    copy(connectionState = SdkConnectionState.Registered)
                }
            }

            override fun onRegisterFailure(error: IDJIError?) {
                val errorMsg = error?.toString() ?: "Unknown error"
                Log.e(TAG, "onRegisterFailure: $errorMsg")
                updateSdkStatus {
                    copy(connectionState = SdkConnectionState.RegistrationFailed(errorMsg))
                }
            }

            override fun onProductConnect(productId: Int) {
                Log.i(TAG, "onProductConnect: productId=$productId")
                // Fetch product details from SDK
                fetchProductDetails()
            }

            override fun onProductDisconnect(productId: Int) {
                Log.i(TAG, "onProductDisconnect: productId=$productId")
                currentProductInfo = ProductInfo()
                updateSdkStatus {
                    copy(
                        connectionState = SdkConnectionState.ProductDisconnected,
                        productInfo = currentProductInfo
                    )
                }
            }

            override fun onProductChanged(productId: Int) {
                Log.i(TAG, "onProductChanged: productId=$productId")
                // Refresh product details when product changes
                fetchProductDetails()
            }

            override fun onDatabaseDownloadProgress(current: Long, total: Long) {
                val progress = if (total > 0) current.toFloat() / total else 0f
                databaseProgress = progress
                Log.d(TAG, "Database download progress: ${progress * 100}%")
            }
        })
    }

    /**
     * Fetch detailed product information from SDK
     */
    private fun fetchProductDetails() {
        try {
            // Get product type
            val productType = ProductKey.KeyProductType.create().get(ProductType.UNRECOGNIZED)
            Log.d(TAG, "Product type: $productType")
            currentProductInfo = currentProductInfo.copy(type = productType)
            updateProductInfo(productType.name ?: "Unknown Product")

            // Get firmware version
            ProductKey.KeyFirmwareVersion.create().get(
                onSuccess = { firmware ->
                    Log.d(TAG, "Firmware version: $firmware")
                    currentProductInfo = currentProductInfo.copy(firmwareVersion = firmware ?: "N/A")
                    updateSdkStatus { copy(productInfo = currentProductInfo) }
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to get firmware version: $error")
                }
            )

            // Get serial number
            ProductKey.KeySerialNumber.create().get(
                onSuccess = { serial ->
                    Log.d(TAG, "Serial number: $serial")
                    currentProductInfo = currentProductInfo.copy(serialNumber = serial ?: "N/A")
                    updateSdkStatus { copy(productInfo = currentProductInfo) }
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to get serial number: $error")
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching product details: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun updateProductInfo(productName: String) {
        currentProductInfo = currentProductInfo.copy(
            isConnected = true,
            name = productName
        )
        updateSdkStatus {
            copy(
                connectionState = SdkConnectionState.ProductConnected(productName, currentProductInfo.type),
                productInfo = currentProductInfo
            )
        }
    }

    private fun updateSdkStatus(update: DjiSdkStatus.() -> DjiSdkStatus) {
        _sdkStatus.value = _sdkStatus.value.update()
    }

    companion object {
        @Volatile
        private var instance: DjiSdkManager? = null

        fun getInstance(): DjiSdkManager {
            return instance ?: synchronized(this) {
                instance ?: DjiSdkManager().also { instance = it }
            }
        }
    }
}
