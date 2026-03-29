package com.fuwaki.djifly.platform

import android.content.Context
import com.fuwaki.djifly.platform.livestream.DjiTrtcLiveService
import com.fuwaki.djifly.platform.network.PlatformNetwork
import com.fuwaki.djifly.platform.registration.PlatformRegistrationManager
import com.fuwaki.djifly.platform.registration.PlatformRegistrationRepository
import com.fuwaki.djifly.platform.registration.PlatformRegistrationStore
import com.fuwaki.djifly.sdk.DjiSdkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(
    appContext: Context,
    sdkManager: DjiSdkManager
) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val registrationStore = PlatformRegistrationStore(appContext)
    private val registrationRepository = PlatformRegistrationRepository(
        apiFactory = { PlatformNetwork.createApiService() }
    )

    val platformRegistrationManager = PlatformRegistrationManager(
        sdkManager = sdkManager,
        repository = registrationRepository,
        store = registrationStore,
        externalScope = applicationScope
    )

    val djiTrtcLiveService = DjiTrtcLiveService(appContext)
}
