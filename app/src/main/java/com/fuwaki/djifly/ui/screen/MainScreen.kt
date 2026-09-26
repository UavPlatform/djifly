package com.fuwaki.djifly.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fuwaki.djifly.ui.navigation.BottomNavItem
import com.fuwaki.djifly.ui.theme.AccentBlue
import com.fuwaki.djifly.ui.theme.Divider
import com.fuwaki.djifly.ui.theme.SurfaceCard
import com.fuwaki.djifly.ui.theme.TextPrimary
import com.fuwaki.djifly.ui.theme.TextTertiary

/**
 * Main container screen with bottom navigation bar.
 *
 * djifly 收敛为飞行执行适配器（REQ-DJIFLY-001）：仅 待执飞 / 任务 / 我的 三个 Tab，
 * 聊天与任务大厅 Tab 已移除（业务归 app/）。
 *
 * Hosts 3 tab screens and manages tab switching via [selectedTab] state.
 * Each tab's content is injected as a composable lambda so the actual screen
 * implementations can be provided externally.
 */
@Composable
fun MainScreen(
    homeContent: @Composable (selectTab: (Int) -> Unit) -> Unit,
    myTasksContent: @Composable () -> Unit,
    profileContent: @Composable () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            androidx.compose.foundation.layout.Column {
                HorizontalDivider(thickness = 0.5.dp, color = Divider)
                NavigationBar(
                    containerColor = SurfaceCard,
                    contentColor = TextPrimary,
                    tonalElevation = 3.dp
                ) {
                    BottomNavItem.entries.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label
                                )
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentBlue,
                                selectedTextColor = AccentBlue,
                                unselectedIconColor = TextTertiary,
                                unselectedTextColor = TextTertiary,
                                indicatorColor = AccentBlue.copy(alpha = 0.12f)
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> homeContent { tabIndex -> selectedTab = tabIndex }
                1 -> myTasksContent()
                2 -> profileContent()
            }
        }
    }
}
