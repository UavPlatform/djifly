package com.fuwaki.djifly.ui.screen

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.fuwaki.djifly.sdk.DjiSdkManager
import com.fuwaki.djifly.sdk.SdkConnectionState
import com.fuwaki.djifly.ui.widget.*

private const val TAG = "FlightScreen"

/**
 * Main Flight Screen
 * Entry point that checks product connection before displaying flight interface
 *
 * @param sdkManager DJI SDK manager instance
 * @param navController Navigation controller for screen navigation
 */
@Composable
fun FlightScreen(
    sdkManager: DjiSdkManager,
    navController: NavController
) {
    val sdkStatus by sdkManager.sdkStatus.collectAsState()

    // Only show flight interface when product is connected
    if (sdkStatus.connectionState is SdkConnectionState.ProductConnected) {
        FlightScreenContent(
            sdkManager = sdkManager,
            navController = navController
        )
    } else {
        // Show connection required message
        ConnectionRequiredScreen(navController)
    }
}

/**
 * Connection Required Screen
 * Displayed when no product is connected
 */
@Composable
private fun ConnectionRequiredScreen(navController: NavController) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No Product Connected",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Please connect to a DJI product",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(24.dp))
                androidx.compose.material3.Button(
                    onClick = { navController.navigateUp() }
                ) {
                    Text("Go Back")
                }
            }
        }
    }
}

/**
 * Flight Screen Content
 * Main flight interface with UXSDK components
 */
@Composable
private fun FlightScreenContent(
    sdkManager: DjiSdkManager,
    navController: NavController
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResume = rememberUpdatedState(Unit)
    val currentOnPause = rememberUpdatedState(Unit)

    // Handle lifecycle events for UXSDK components
    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    Log.d(TAG, "ON_RESUME - Resuming video stream")
                    // Video stream automatically resumes when FPVWidget is visible
                }
                Lifecycle.Event.ON_PAUSE -> {
                    Log.d(TAG, "ON_PAUSE - Pausing video stream")
                    // Video stream automatically pauses when FPVWidget is hidden
                }
                Lifecycle.Event.ON_DESTROY -> {
                    Log.d(TAG, "ON_DESTROY - Cleaning up resources")
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Top status bar
        TopBarPanelWidget(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(Color.Black),
            onSettingClick = {
                Log.d(TAG, "Settings button clicked")
                // TODO: Navigate to settings screen
            }
        )

        // 2. Remaining flight time (below top bar)
        RemainingFlightTimeWidget(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 40.dp)
                .fillMaxWidth()
                .height(20.dp)
        )

        // 3. Main FPV video stream (full screen background)
        FpvWidget(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 60.dp), // Reserve space for top bar and flight time
            cameraIndex = dji.sdk.keyvalue.value.common.ComponentIndexType.LEFT_OR_MAIN,
            enableCenterPoint = true,
            enableGridLines = true
        )

        // 4. Camera control buttons (top right)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 8.dp, top = 60.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AutoExposureLockWidget(modifier = Modifier.size(50.dp))
            FocusModeWidget(modifier = Modifier.size(50.dp))
            FocusExposureSwitchWidget(modifier = Modifier.size(50.dp))
        }

        // 5. Camera controls (right side)
        CameraControlsWidget(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .height(300.dp)
        )

        // 6. Left side flight control buttons column
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp, top = 125.dp)
                .width(80.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TakeOffWidget(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            )

            ReturnHomeWidget(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            )
        }

        // 7. Bottom attitude indicator
        HorizontalSituationIndicatorWidget(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .width(350.dp)
        )

        // 8. Back button (top left)
        IconButton(
            onClick = { navController.navigateUp() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back to Starter Screen",
                tint = Color.White
            )
        }
    }
}
