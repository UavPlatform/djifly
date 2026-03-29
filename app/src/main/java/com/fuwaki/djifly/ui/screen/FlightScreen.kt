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
import com.fuwaki.djifly.platform.registration.PlatformRegistrationManager
import com.fuwaki.djifly.platform.registration.PlatformRegistrationState
import com.fuwaki.djifly.sdk.DjiSdkManager
import com.fuwaki.djifly.sdk.SdkConnectionState
import com.fuwaki.djifly.ui.widget.*
import com.fuwaki.djifly.ui.widget.compose.TopStatusRow
import com.fuwaki.djifly.ui.widget.compose.TakeOffButton
import com.fuwaki.djifly.ui.widget.compose.ReturnHomeButton
import com.fuwaki.djifly.ui.widget.compose.CameraConfigBar
import com.fuwaki.djifly.ui.widget.compose.ServerConnectionChip
import dji.sdk.keyvalue.value.common.CameraLensType
import dji.sdk.keyvalue.value.common.ComponentIndexType

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
    registrationManager: PlatformRegistrationManager,
    navController: NavController
) {
    val sdkStatus by sdkManager.sdkStatus.collectAsState()
    val registrationState by registrationManager.state.collectAsState()

    if (sdkStatus.connectionState is SdkConnectionState.ProductConnected) {
        FlightScreenContent(
            sdkManager = sdkManager,
            registrationState = registrationState,
            navController = navController
        )
    } else {
        ConnectionRequiredScreen(
            registrationState = registrationState,
            navController = navController
        )
    }
}

@Composable
private fun ConnectionRequiredScreen(
    registrationState: PlatformRegistrationState,
    navController: NavController
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0A0A0A)) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ServerConnectionChip(
                    registrationState = registrationState,
                    showDetail = true,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
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
    registrationState: PlatformRegistrationState,
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                ServerConnectionChip(
                    registrationState = registrationState,
                    showDetail = false
                )
            }
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

        // 4. 右侧相机控制 (调整为精确居中)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .width(90.dp)
        ) {
            Column(
                modifier = Modifier.wrapContentHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 4.1 对焦模式切换
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(0.4f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    FocusModeWidget(modifier = Modifier.size(26.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4.2 镜头/变焦控制
                LensControlWidget(modifier = Modifier.width(44.dp).height(90.dp))

                Spacer(modifier = Modifier.height(12.dp))

                // 4.3 核心按钮区 (拍照、录像切换)
                CameraControlsComposeWidget(
                    cameraIndex = ComponentIndexType.LEFT_OR_MAIN,
                    lensType = CameraLensType.UNKNOWN
                )

                // 🚀 核心优化：增加底部补偿间距，平衡上方的挂件，使拍照按钮处于屏幕中心
                Spacer(modifier = Modifier.height(120.dp))
            }
        }

        // 5. 底部左侧姿态球 (HSI) - 贴死边缘
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
        ) {
            Surface(
                color = Color.Black.copy(0.3f),
                shape = RoundedCornerShape(topEnd = 12.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    width = 0.5.dp,
                    brush = Brush.linearGradient(listOf(Color.White.copy(0.15f), Color.Transparent))
                )
            ) {
                Box(
                    modifier = Modifier
                        .height(100.dp)
                        .wrapContentWidth()
                        .padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 0.dp),
                    contentAlignment = Alignment.Center
                ) {
                    HorizontalSituationIndicatorWidget(
                        modifier = Modifier.fillMaxHeight()
                    )
                }
            }
        }

        // 8. 右下角相机参数栏 (贴死边缘)
        CameraConfigBar(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
        )

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
