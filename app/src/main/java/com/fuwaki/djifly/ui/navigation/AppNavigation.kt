package com.fuwaki.djifly.ui.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * All navigation routes for the app.
 *
 * djifly 是飞行执行适配器（REQ-DJIFLY-001）：只保留 登录 → 待执飞任务 → 起飞检查 → 飞行控制台 链路，
 * 任务大厅 / 聊天会话 / 消息列表路由已随业务瘦身移除（归属 app/）。
 */
object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val MAIN = "main"
    const val TASK_DETAIL = "task_detail/{taskNum}"
    const val PROFILE = "profile"
    const val DRONE_MANAGE = "drone_manage"

    // starter/flight 支持可选 taskNum（连接测试不带参，行为不受影响）
    const val STARTER = "starter"
    const val FLIGHT = "flight"
    const val STARTER_PATTERN = "starter?taskNum={taskNum}"
    const val FLIGHT_PATTERN = "flight?taskNum={taskNum}"

    fun taskDetail(taskNum: String) = "task_detail/$taskNum"

    /** 携任务进入飞行准备页（taskNum 空则退化为连接测试路由） */
    fun starter(taskNum: String?): String =
        if (taskNum.isNullOrBlank()) STARTER else "starter?taskNum=${Uri.encode(taskNum)}"

    /** 携任务进入飞行控制台（taskNum 空则退化为连接测试路由） */
    fun flight(taskNum: String?): String =
        if (taskNum.isNullOrBlank()) FLIGHT else "flight?taskNum=${Uri.encode(taskNum)}"
}

/**
 * Bottom navigation items for the main screen.
 */
enum class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    HOME(Routes.MAIN, "作业", Icons.Default.Home),
    PROFILE(Routes.PROFILE, "我的", Icons.Default.Person)
}
