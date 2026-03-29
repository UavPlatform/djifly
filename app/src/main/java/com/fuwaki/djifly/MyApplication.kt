package com.fuwaki.djifly

import android.app.Application
import android.content.Context
import android.util.Log
import com.fuwaki.djifly.platform.AppContainer
import com.fuwaki.djifly.sdk.DjiSdkManager

class MyApplication : Application() {

    private val TAG = this::class.simpleName
    lateinit var appContainer: AppContainer
        private set

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        // Install DJI MSDK decryption/protection package before calling any MSDK interfaces
        // For MSDK v5.10.0 and later, use com.cySdkyc.clx.Helper.install()
        // For MSDK versions prior to v5.10.0, use com.secneo.sdk.Helper.install()
        com.cySdkyc.clx.Helper.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "MyApplication onCreate - Initializing DJI SDK")

        val sdkManager = DjiSdkManager.getInstance()
        appContainer = AppContainer(this, sdkManager)
        sdkManager.initSdk(this)
        appContainer.platformRegistrationManager.start()
    }
}
