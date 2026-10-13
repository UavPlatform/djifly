package com.fuwaki.djifly.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 飞行控制台专用深色主题
 */
private val PilotDarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = AccentBlueDark,
    onPrimaryContainer = Color.White,
    secondary = StatusGreen,
    onSecondary = Color.White,
    tertiary = StatusAmber,
    onTertiary = Color.White,
    background = SurfaceDark,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCardLight,
    onSurfaceVariant = TextSecondary,
    error = StatusRed,
    onError = Color.White,
    outline = Divider,
    outlineVariant = Divider
)

/** Shell：登录 / 作业 / 起飞检查 / 我的 */
private val ShellLightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = BrandPrimary.copy(alpha = 0.12f),
    onPrimaryContainer = BrandPrimaryDark,
    secondary = StatusGreen,
    onSecondary = Color.White,
    tertiary = StatusAmber,
    onTertiary = Color.White,
    background = SurfacePage,
    onBackground = ShellTextPrimary,
    surface = ShellCard,
    onSurface = ShellTextPrimary,
    surfaceVariant = Color(0xFFEBEEF2),
    onSurfaceVariant = ShellTextSecondary,
    error = StatusRed,
    onError = Color.White,
    outline = ShellDivider,
    outlineVariant = ShellDivider
)

@Composable
fun DjiflyTheme(
    darkTheme: Boolean = false, // Shell 默认浅色；飞行页在导航处单独包一层 darkTheme = true
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val useDark = darkTheme
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDark -> PilotDarkColorScheme
        else -> ShellLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
