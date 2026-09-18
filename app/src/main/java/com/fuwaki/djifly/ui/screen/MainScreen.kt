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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fuwaki.djifly.ui.navigation.BottomNavItem
import com.fuwaki.djifly.ui.theme.AccentBlue
import com.fuwaki.djifly.ui.theme.Divider
import com.fuwaki.djifly.ui.theme.StatusRed
import com.fuwaki.djifly.ui.theme.SurfaceCard
import com.fuwaki.djifly.ui.theme.TextPrimary
import com.fuwaki.djifly.ui.theme.TextTertiary
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.sizeIn
import androidx.hilt.navigation.compose.hiltViewModel
import com.fuwaki.djifly.ui.viewmodel.MessageViewModel

/**
 * Main container screen with bottom navigation bar.
 *
 * Hosts 5 tab screens and manages tab switching via [selectedTab] state.
 * Each tab's content is injected as a composable lambda so the actual screen
 * implementations can be provided externally.
 */
@Composable
fun MainScreen(
    homeContent: @Composable (selectTab: (Int) -> Unit) -> Unit,
    taskSquareContent: @Composable () -> Unit,
    myTasksContent: @Composable () -> Unit,
    messagesContent: @Composable () -> Unit,
    profileContent: @Composable () -> Unit,
    messageViewModel: MessageViewModel = hiltViewModel()
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // 消息 Tab 未读红点（1B-8 收敛包）：总数来自 /chat/Message/unread/count（t49 只读契约），
    // 进入会话后由 ChatViewModel 显式 POST /read 推进清零
    val unreadCountMap by messageViewModel.unreadCountMap.collectAsState()
    val totalUnread = unreadCountMap.values.sum()

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
                                if (item == BottomNavItem.MESSAGES && totalUnread > 0) {
                                    Box {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = item.label
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .background(StatusRed, CircleShape)
                                                .sizeIn(minWidth = 16.dp, minHeight = 16.dp)
                                                .padding(horizontal = 3.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (totalUnread > 99) "99+" else totalUnread.toString(),
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                } else {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label
                                    )
                                }
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
                1 -> taskSquareContent()
                2 -> myTasksContent()
                3 -> messagesContent()
                4 -> profileContent()
            }
        }
    }
}
