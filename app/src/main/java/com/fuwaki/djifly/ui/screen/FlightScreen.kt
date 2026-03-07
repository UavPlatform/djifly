package com.fuwaki.djifly.ui.screen

import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.fuwaki.djifly.sdk.DjiSdkManager
import com.fuwaki.djifly.sdk.SdkConnectionState
import com.fuwaki.djifly.ui.widget.*
import com.fuwaki.djifly.ui.widget.compose.TopStatusRow
import com.fuwaki.djifly.ui.widget.compose.TakeOffButton
import com.fuwaki.djifly.ui.widget.compose.ReturnHomeButton

private const val TAG = "FlightScreen"

fun Context.findFragmentActivity(): FragmentActivity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is FragmentActivity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

@Composable
fun FlightScreen(
    sdkManager: DjiSdkManager,
    navController: NavController
) {
    val sdkStatus by sdkManager.sdkStatus.collectAsState()

    if (sdkStatus.connectionState is SdkConnectionState.ProductConnected) {
        FlightScreenContent(sdkManager = sdkManager, navController = navController)
    } else {
        ConnectionRequiredScreen(navController)
    }
}

@Composable
private fun ConnectionRequiredScreen(navController: NavController) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0A0A0A)) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Warning, null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("DISCONNECTED", color = Color.White, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(32.dp))
                OutlinedButton(
                    onClick = { navController.navigateUp() },
                    border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                ) {
                    Text("BACK TO HOME", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun FlightScreenContent(
    sdkManager: DjiSdkManager,
    navController: NavController
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isSettingsOpen by remember { mutableStateOf(false) }

    // 侧边栏动画偏移量 (0dp 展开，510dp 完全收起并隐藏在右侧)
    val panelOffsetX by animateDpAsState(
        targetValue = if (isSettingsOpen) 0.dp else 510.dp,
        animationSpec = tween(durationMillis = 300),
        label = "settings_panel_anim"
    )

    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) Log.d(TAG, "Flight Screen Active")
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 1. FPV 背景层
        FpvWidget(modifier = Modifier.fillMaxSize())

        // 2. 顶部状态栏
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(0.7f), Color.Transparent)))
                    .statusBarsPadding()
            ) {
                TopStatusRow(
                    sdkManager = sdkManager,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    onSettingsClick = {
                        isSettingsOpen = true
                    }
                )
            }
            RemainingFlightTimeWidget(modifier = Modifier.fillMaxWidth().height(12.dp))
        }

        // 3. 左侧控制按钮
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 72.dp)
                .fillMaxHeight(0.65f),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { navController.navigateUp() },
                modifier = Modifier.size(34.dp).background(Color.Black.copy(0.4f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                TakeOffButton(
                    onConfirm = { sdkManager.performTakeOff() }
                )
                ReturnHomeButton(
                    onConfirm = { sdkManager.performRTH() }
                )
            }
        }

        // 4. 右侧相机控制
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .fillMaxHeight(0.85f)
                .width(90.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(0.4f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AutoExposureLockWidget(modifier = Modifier.size(26.dp))
                    FocusModeWidget(modifier = Modifier.size(26.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))
                LensControlWidget(modifier = Modifier.width(44.dp).height(90.dp))
                Spacer(modifier = Modifier.height(16.dp))

                Box(modifier = Modifier.height(200.dp), contentAlignment = Alignment.Center) {
                    CameraControlsWidget(modifier = Modifier.fillMaxSize())
                }
            }
        }

        // 5. 底部姿态球 (HSI)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            Surface(
                color = Color.Black.copy(0.3f),
                shape = RoundedCornerShape(12.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(width = 0.5.dp, brush = Brush.linearGradient(listOf(Color.White.copy(0.15f), Color.Transparent)))
            ) {
                HorizontalSituationIndicatorWidget(
                    modifier = Modifier.width(260.dp).height(100.dp).padding(4.dp)
                )
            }
        }

        // 6. 遮罩层 (当面板打开时变暗，点击空白处关闭)
        if (panelOffsetX < 510.dp) {
            val alpha = (1f - (panelOffsetX.value / 510f)).coerceIn(0f, 1f) * 0.5f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = alpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isSettingsOpen = false
                    }
            )
        }

        // 7. 大疆设置面板 (动画滑动层)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(510.dp)
                .fillMaxHeight()
                .offset(x = panelOffsetX)
                .background(Color.Black.copy(alpha = 0.95f))
        ) {
            AndroidView(
                factory = { context ->
                    val activity = context.findFragmentActivity()
                        ?: throw IllegalStateException("当前 Context 无法转换为 FragmentActivity")

                    // 自定义 SettingPanelWidget，拦截返回事件来触发 Compose 动画收起
                    object : dji.v5.ux.core.widget.setting.SettingPanelWidget(activity) {
                        override fun onBackPressed(): Boolean {
                            // 先调用父类处理逻辑
                            val handled = super.onBackPressed()
                            if (!handled) {
                                // 父类返回 false 说明没有 Fragment 处理返回事件
                                // 此时用户想要关闭整个设置面板
                                isSettingsOpen = false
                                return true // 拦截返回事件
                            }
                            return handled
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
