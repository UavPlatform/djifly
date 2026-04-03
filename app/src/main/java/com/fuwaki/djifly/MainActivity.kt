package com.fuwaki.djifly

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import com.fuwaki.djifly.platform.registration.PlatformRegistrationManager
import com.fuwaki.djifly.platform.ws.WsCommunicationState
import com.fuwaki.djifly.sdk.DjiSdkManager
import com.fuwaki.djifly.ui.screen.FlightScreen
import com.fuwaki.djifly.ui.screen.StarterScreen
import com.fuwaki.djifly.ui.theme.DjiflyTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private enum class AppScreen {
    Starter,
    Flight
}

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var sdkManager: DjiSdkManager
    @Inject lateinit var registrationManager: PlatformRegistrationManager
    @Inject lateinit var wsCommunicationState: WsCommunicationState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable immersive mode
        enableImmersiveMode()

        setContent {
            DjiflyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Starter.name) }

                    when (AppScreen.valueOf(currentScreen)) {
                        AppScreen.Starter -> {
                            StarterScreen(
                                sdkManager = sdkManager,
                                registrationManager = registrationManager,
                                onEnterFlight = {
                                    currentScreen = AppScreen.Flight.name
                                }
                            )
                        }
                        AppScreen.Flight -> {
                            FlightScreen(
                                sdkManager = sdkManager,
                                registrationManager = registrationManager,
                                wsCommunicationState = wsCommunicationState,
                                onBack = {
                                    currentScreen = AppScreen.Starter.name
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }
}
