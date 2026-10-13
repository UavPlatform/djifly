package com.fuwaki.djifly

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fuwaki.djifly.data.auth.AuthState
import com.fuwaki.djifly.data.error.GlobalErrorHandler
import com.fuwaki.djifly.ui.effect.GlobalErrorEffect
import com.fuwaki.djifly.ui.viewmodel.AuthViewModel
import com.fuwaki.djifly.platform.livestream.LiveStopCoordinator
import com.fuwaki.djifly.platform.registration.PlatformRegistrationManager
import com.fuwaki.djifly.platform.ws.WsCommunicationState
import com.fuwaki.djifly.sdk.DjiSdkManager
import com.fuwaki.djifly.ui.navigation.Routes
import com.fuwaki.djifly.ui.screen.DroneManageScreen
import com.fuwaki.djifly.ui.screen.FlightScreen
import com.fuwaki.djifly.ui.screen.HomeScreen
import com.fuwaki.djifly.ui.screen.LoginScreen
import com.fuwaki.djifly.ui.screen.MainScreen
import com.fuwaki.djifly.ui.screen.ProfileScreen
import com.fuwaki.djifly.ui.screen.SplashScreen
import com.fuwaki.djifly.ui.screen.StarterScreen
import com.fuwaki.djifly.ui.screen.TaskDetailScreen
import com.fuwaki.djifly.ui.theme.DjiflyTheme
import com.fuwaki.djifly.ui.viewmodel.TaskViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 飞手端主 Activity
 *
 * 导航流程：Splash → Login → Main(作业/我的) → TaskDetail/DroneManage/Starter → Flight
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var sdkManager: DjiSdkManager
    @Inject lateinit var registrationManager: PlatformRegistrationManager
    @Inject lateinit var wsCommunicationState: WsCommunicationState
    @Inject lateinit var errorHandler: GlobalErrorHandler
    @Inject lateinit var liveStopCoordinator: LiveStopCoordinator

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 默认竖屏（飞行控制台除外）
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableImmersiveMode()

        setContent {
            DjiflyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val snackbarHostState = remember { SnackbarHostState() }
                    val authState by authViewModel.authState.collectAsState()
                    val splashReady by authViewModel.splashReady.collectAsState()
                    val loginState by authViewModel.loginState.collectAsState()
                    var splashNavigationHandled by remember { mutableStateOf(false) }

                    LaunchedEffect(splashReady, authState) {
                        if (
                            splashNavigationHandled ||
                            !splashReady ||
                            authState is AuthState.Unknown
                        ) {
                            return@LaunchedEffect
                        }

                        splashNavigationHandled = true
                        val targetRoute = when (authState) {
                            is AuthState.Authenticated -> Routes.MAIN
                            else -> Routes.LOGIN
                        }
                        navController.navigate(targetRoute) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                            launchSingleTop = true
                        }
                    }

                    // 全局错误处理：DroneRequired → 对话框，其他 → Snackbar
                    GlobalErrorEffect(
                        handler = errorHandler,
                        snackbarHostState = snackbarHostState,
                        onNavigateToDroneManage = {
                            navController.navigate(Routes.DRONE_MANAGE)
                        },
                        onNavigateToLogin = {
                            authViewModel.logout()
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )

                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) }
                    ) { scaffoldPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Routes.SPLASH,
                        modifier = Modifier.padding(scaffoldPadding)
                        // 不使用动画过渡，避免 AnimatedContent LayoutNode 崩溃
                    ) {
                        composable(Routes.SPLASH) {
                            SplashScreen()
                        }

                        composable(Routes.LOGIN) {
                            LoginScreen(
                                loginState = loginState,
                                serverEndpoint = com.fuwaki.djifly.BuildConfig.DRONE_BACKEND_BASE_URL,
                                onLogin = { userName, password ->
                                    authViewModel.login(userName, password)
                                },
                                onNavigateToHome = {
                                    navController.navigate(Routes.MAIN) {
                                        popUpTo(Routes.LOGIN) { inclusive = true }
                                    }
                                },
                                onClearError = { authViewModel.clearLoginError() }
                            )
                        }

                        composable(Routes.MAIN) {
                            MainScreen(
                                homeContent = {
                                    HomeScreen(
                                        onNavigateToTaskDetail = { taskNum ->
                                            navController.navigate(Routes.taskDetail(taskNum))
                                        },
                                        onNavigateToStarter = { taskNum ->
                                            navController.navigate(Routes.starter(taskNum))
                                        }
                                    )
                                },
                                profileContent = {
                                    ProfileScreen(
                                        onNavigateToDroneManage = {
                                            navController.navigate(Routes.DRONE_MANAGE)
                                        },
                                        onNavigateToConnectionTest = {
                                            navController.navigate(Routes.starter(null))
                                        },
                                        onLogout = {
                                            authViewModel.logout()
                                            navController.navigate(Routes.LOGIN) {
                                                popUpTo(Routes.MAIN) { inclusive = true }
                                            }
                                        }
                                    )
                                }
                            )
                        }

                        composable(
                            route = Routes.TASK_DETAIL,
                            arguments = listOf(
                                navArgument("taskNum") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val taskNum = backStackEntry.arguments?.getString("taskNum") ?: ""
                            val taskVm: TaskViewModel = hiltViewModel()
                            TaskDetailScreen(
                                taskNum = taskNum,
                                viewModel = taskVm,
                                onBack = { navController.popBackStack() },
                                onStartFlight = { navController.navigate(Routes.starter(taskNum)) },
                                onStartLiveStream = { taskVm.requestRiderLiveStream() }
                            )
                        }

                        composable(Routes.DRONE_MANAGE) {
                            DroneManageScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = Routes.STARTER_PATTERN,
                            arguments = listOf(
                                navArgument("taskNum") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }
                            )
                        ) { backStackEntry ->
                            StarterScreen(
                                sdkManager = sdkManager,
                                registrationManager = registrationManager,
                                taskNum = backStackEntry.arguments?.getString("taskNum"),
                                onEnterFlight = { taskNum ->
                                    navController.navigate(Routes.flight(taskNum))
                                }
                            )
                        }

                        composable(
                            route = Routes.FLIGHT_PATTERN,
                            arguments = listOf(
                                navArgument("taskNum") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }
                            )
                        ) { backStackEntry ->
                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            DjiflyTheme(darkTheme = true) {
                                FlightScreen(
                                    sdkManager = sdkManager,
                                    registrationManager = registrationManager,
                                    wsCommunicationState = wsCommunicationState,
                                    liveStopCoordinator = liveStopCoordinator,
                                    taskNum = backStackEntry.arguments?.getString("taskNum"),
                                    onBack = {
                                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }
                    } // NavHost
                    } // Scaffold
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
