package com.fuwaki.djifly

import android.app.Application
import android.content.Context
import android.util.Log
import com.fuwaki.djifly.platform.registration.PlatformRegistrationManager
import com.fuwaki.djifly.sdk.DjiSdkManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MyApplication : Application() {

    private val TAG = this::class.simpleName
    @Inject lateinit var sdkManager: DjiSdkManager
    @Inject lateinit var platformRegistrationManager: PlatformRegistrationManager

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

        sdkManager.initSdk(this)
        platformRegistrationManager.start()
    }
}
