package com.fuwaki.djifly.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fuwaki.djifly.data.models.SessionVO
import com.fuwaki.djifly.ui.theme.AccentBlue
import com.fuwaki.djifly.ui.theme.Divider
import com.fuwaki.djifly.ui.theme.StatusRed
import com.fuwaki.djifly.ui.theme.SurfaceDark
import com.fuwaki.djifly.ui.theme.TextPrimary
import com.fuwaki.djifly.ui.theme.TextSecondary
import com.fuwaki.djifly.ui.theme.TextTertiary
import com.fuwaki.djifly.ui.viewmodel.MessageViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(
    onNavigateToChat: (Long, String?) -> Unit,
    viewModel: MessageViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val unreadCountMap by viewModel.unreadCountMap.collectAsState()

    // 返回消息页时刷新未读（从聊天页 pop 回来会重组本页，红点即时回落）
    LaunchedEffect(Unit) {
        viewModel.refreshUnread()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "消息",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = SurfaceDark
            )
        )

    val pullToRefreshState = rememberPullToRefreshState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(pullToRefreshState.nestedScrollConnection)
    ) {
        if (sessions.isEmpty() && !loading) {
            EmptyMessageState()
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(sessions, key = { it.id }) { session ->
                    val sessionUnread = unreadCountMap[session.id] ?: session.unreadCount
                    SessionItem(
                        session = session,
                        unreadCount = sessionUnread,
                        onClick = { onNavigateToChat(session.id, session.otherUserName ?: session.name) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 76.dp),
                        thickness = 0.5.dp,
                        color = Divider
                    )
                }
            }
        }

        PullToRefreshContainer(
            state = pullToRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.loadSessions()
            pullToRefreshState.endRefresh()
        }
    }
    }
}

@Composable
private fun SessionItem(
    session: SessionVO,
    unreadCount: Int = 0,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar circle
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(AccentBlue),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (session.otherUserName ?: session.name).firstOrNull()?.toString() ?: "?",
                color = androidx.compose.ui.graphics.Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Name + last message + 未读红点（1B-8 收敛包）
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = session.otherUserName ?: session.name,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = session.lastMessage ?: "",
                color = TextSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Time
        Text(
            text = formatSessionTime(session.lastMessageTime ?: session.createTime),
            color = TextTertiary,
            fontSize = 12.sp
        )

        // 未读红点（1B-8 收敛包，t49：进入会话后由客户端显式推进清零）
        if (unreadCount > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(StatusRed, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EmptyMessageState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.ChatBubbleOutline,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "暂无消息",
                color = TextTertiary,
                fontSize = 16.sp
            )
        }
    }
}

/**
 * Format session time for display. Shows time for today, date otherwise.
 */
private fun formatSessionTime(createTime: Long): String {
    if (createTime == 0L) return ""
    return try {
        val now = System.currentTimeMillis()
        val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        val todaySdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val cal = java.util.Calendar.getInstance()
        val today = cal.apply { timeInMillis = now }.get(java.util.Calendar.DAY_OF_YEAR)
        val msgDay = cal.apply { timeInMillis = createTime }.get(java.util.Calendar.DAY_OF_YEAR)
        if (today == msgDay) todaySdf.format(java.util.Date(createTime))
        else sdf.format(java.util.Date(createTime))
    } catch (_: Exception) {
        ""
    }
}
