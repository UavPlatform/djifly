package com.fuwaki.djifly.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuwaki.djifly.ui.theme.AccentBlue
import com.fuwaki.djifly.ui.theme.SurfaceDark
import com.fuwaki.djifly.ui.theme.TextSecondary

/**
 * 启动页
 */
@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App 名称
            Text(
                text = "飞手端",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = AccentBlue
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 副标题
            Text(
                text = "DJI Fly",
                fontSize = 16.sp,
                fontWeight = FontWeight.Light,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(48.dp))

            // 加载指示器
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = AccentBlue,
                strokeWidth = 3.dp
            )
        }
    }
}
