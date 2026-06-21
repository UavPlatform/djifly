package com.fuwaki.djifly.ui.effect

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.fuwaki.djifly.data.error.AppError
import com.fuwaki.djifly.data.error.GlobalErrorHandler
import com.fuwaki.djifly.ui.theme.AccentBlue
import com.fuwaki.djifly.ui.theme.StatusRed

/**
 * 全局错误展示效果
 *
 * 放在顶层 Composable 中，观察 GlobalErrorHandler 的错误流：
 * - DroneRequired → 弹出对话框，引导用户去绑定无人机
 * - Unauthorized → Snackbar 提示重新登录
 * - Network → Snackbar 提示网络问题
 * - Business / Http / Unknown → Snackbar 展示后端消息
 *
 * @param handler 全局错误处理器（Hilt 注入）
 * @param snackbarHostState 用于展示 Snackbar
 * @param onNavigateToDroneManage 点击"去绑定"时的导航回调
 * @param onNavigateToLogin Token 失效时跳转登录
 */
@Composable
fun GlobalErrorEffect(
    handler: GlobalErrorHandler,
    snackbarHostState: SnackbarHostState,
    onNavigateToDroneManage: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {}
) {
    var droneDialogMessage by remember { mutableStateOf<String?>(null) }

    // 观察错误流
    LaunchedEffect(handler) {
        handler.errors.collect { error ->
            when (error) {
                is AppError.DroneRequired -> {
                    // 弹出对话框，不是 Snackbar
                    droneDialogMessage = error.message
                }
                is AppError.Unauthorized -> {
                    val result = snackbarHostState.showSnackbar(
                        message = error.message,
                        actionLabel = "重新登录",
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        onNavigateToLogin()
                    }
                }
                is AppError.Network -> {
                    snackbarHostState.showSnackbar(
                        message = error.message,
                        duration = SnackbarDuration.Short
                    )
                }
                is AppError.RateLimited -> {
                    snackbarHostState.showSnackbar(
                        message = error.message,
                        duration = SnackbarDuration.Short
                    )
                }
                is AppError.Business -> {
                    snackbarHostState.showSnackbar(
                        message = error.message,
                        duration = SnackbarDuration.Short
                    )
                }
                is AppError.Http -> {
                    snackbarHostState.showSnackbar(
                        message = error.message,
                        duration = SnackbarDuration.Short
                    )
                }
                is AppError.Unknown -> {
                    snackbarHostState.showSnackbar(
                        message = error.message,
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    // 无人机绑定对话框
    if (droneDialogMessage != null) {
        AlertDialog(
            onDismissRequest = { droneDialogMessage = null },
            title = {
                Text("需要绑定无人机", color = Color.White)
            },
            text = {
                Text(droneDialogMessage!!, color = Color.White.copy(alpha = 0.8f))
            },
            confirmButton = {
                TextButton(onClick = {
                    droneDialogMessage = null
                    onNavigateToDroneManage()
                }) {
                    Text("去绑定", color = AccentBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { droneDialogMessage = null }) {
                    Text("稍后", color = Color.White.copy(alpha = 0.6f))
                }
            },
            containerColor = Color(0xFF1E293B),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.8f)
        )
    }
}
