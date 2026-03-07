package com.fuwaki.djifly

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fuwaki.djifly.sdk.DjiSdkManager
import com.fuwaki.djifly.ui.screen.FlightScreen
import com.fuwaki.djifly.ui.screen.StarterScreen
import com.fuwaki.djifly.ui.theme.DjiflyTheme

class MainActivity : ComponentActivity() {

    private val sdkManager = DjiSdkManager.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable immersive mode - hide status bar and navigation bar
        enableImmersiveMode()

        setContent {
            DjiflyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Setup Navigation
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = "starter"
                    ) {
                        composable("starter") {
                            StarterScreen(
                                sdkManager = sdkManager,
                                navController = navController
                            )
                        }
                        composable("flight") {
                            FlightScreen(
                                sdkManager = sdkManager,
                                navController = navController
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Enable immersive fullscreen mode
     * Hides status bar and navigation bar for a truly fullscreen experience
     */
    private fun enableImmersiveMode() {
        // Allow content to extend under system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Hide status bar and navigation bar
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Re-apply immersive mode when window gains focus
            WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }
}
