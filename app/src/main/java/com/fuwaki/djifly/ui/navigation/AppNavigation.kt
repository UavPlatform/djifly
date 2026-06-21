package com.fuwaki.djifly.ui.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

/**
 * All navigation routes for the app.
 */
object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val MAIN = "main"
    const val TASK_SQUARE = "task_square"
    const val TASK_DETAIL = "task_detail/{taskNum}"
    const val MY_TASKS = "my_tasks"
    const val MESSAGES = "messages"
    const val CHAT = "chat/{sessionId}?otherUserName={otherUserName}"
    const val PROFILE = "profile"
    const val DRONE_MANAGE = "drone_manage"
    const val STARTER = "starter"
    const val FLIGHT = "flight"

    fun taskDetail(taskNum: String) = "task_detail/$taskNum"
    fun chat(sessionId: Long, otherUserName: String? = null): String {
        val encodedName = Uri.encode(otherUserName.orEmpty())
        return "chat/$sessionId?otherUserName=$encodedName"
    }
}

/**
 * Bottom navigation items for the main screen.
 */
enum class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    HOME(Routes.MAIN, "首页", Icons.Default.Home),
    TASK_SQUARE(Routes.TASK_SQUARE, "大厅", Icons.Default.List),
    MY_TASKS(Routes.MY_TASKS, "任务", Icons.Default.Assignment),
    MESSAGES(Routes.MESSAGES, "消息", Icons.Default.Email),
    PROFILE(Routes.PROFILE, "我的", Icons.Default.Person)
}

/**
 * Top-level NavHost graph. Each screen's content is injected as a composable lambda
 * so that the actual screen implementations can be provided by other modules.
 */
@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String,
    splashContent: @Composable () -> Unit,
    loginContent: @Composable () -> Unit,
    registerContent: @Composable () -> Unit,
    mainContent: @Composable () -> Unit,
    taskSquareContent: @Composable () -> Unit,
    taskDetailContent: @Composable (String) -> Unit,
    myTasksContent: @Composable () -> Unit,
    messagesContent: @Composable () -> Unit,
    chatContent: @Composable (Long, String?) -> Unit,
    profileContent: @Composable () -> Unit,
    droneManageContent: @Composable () -> Unit,
    starterContent: @Composable () -> Unit,
    flightContent: @Composable () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Splash
        composable(Routes.SPLASH) {
            splashContent()
        }

        // Auth
        composable(Routes.LOGIN) {
            loginContent()
        }
        composable(Routes.REGISTER) {
            registerContent()
        }

        // Main container with bottom nav
        composable(Routes.MAIN) {
            mainContent()
        }

        // Task square (also accessible from bottom nav, but can be a standalone route too)
        composable(Routes.TASK_SQUARE) {
            taskSquareContent()
        }

        // Task detail with taskNum argument
        composable(
            route = Routes.TASK_DETAIL,
            arguments = listOf(
                navArgument("taskNum") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val taskNum = backStackEntry.arguments?.getString("taskNum") ?: ""
            taskDetailContent(taskNum)
        }

        // My tasks
        composable(Routes.MY_TASKS) {
            myTasksContent()
        }

        // Messages
        composable(Routes.MESSAGES) {
            messagesContent()
        }

        // Chat with sessionId argument
        composable(
            route = Routes.CHAT,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.LongType },
                navArgument("otherUserName") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: 0L
            val otherUserName = backStackEntry.arguments?.getString("otherUserName")
            chatContent(sessionId, otherUserName)
        }

        // Profile
        composable(Routes.PROFILE) {
            profileContent()
        }

        // Drone management
        composable(Routes.DRONE_MANAGE) {
            droneManageContent()
        }

        // Starter (pre-flight check)
        composable(Routes.STARTER) {
            starterContent()
        }

        // Flight control
        composable(Routes.FLIGHT) {
            flightContent()
        }
    }
}
