package com.fuwaki.djifly.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fuwaki.djifly.data.models.MessageVO
import com.fuwaki.djifly.ui.theme.AccentBlue
import com.fuwaki.djifly.ui.theme.SurfaceCardLight
import com.fuwaki.djifly.ui.theme.SurfaceDark
import com.fuwaki.djifly.ui.theme.TextPrimary
import com.fuwaki.djifly.ui.theme.TextSecondary
import com.fuwaki.djifly.ui.theme.TextTertiary
import com.fuwaki.djifly.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    sessionId: Long,
    otherUserName: String?,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val userName by viewModel.otherUserName.collectAsState()
    val sending by viewModel.sending.collectAsState()

    // Set other user name from navigation args
    LaunchedEffect(otherUserName) {
        if (!otherUserName.isNullOrBlank()) {
            viewModel.setOtherUserName(otherUserName)
        }
    }

    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
            .imePadding()
    ) {
        // Top bar
        TopAppBar(
            title = {
                Text(
                    text = userName.ifBlank { "聊天" },
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = TextPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = SurfaceDark
            )
        )

        // Message list
        if (messages.isEmpty()) {
            EmptyChatState(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    // Time divider for messages > 5 min apart
                    val index = messages.indexOf(message)
                    if (index == 0 || shouldShowTimeDivider(messages[index - 1], message)) {
                        TimeDivider(timestamp = message.createTime)
                    }

                    MessageBubble(
                        message = message,
                        isOwn = message.fromUserId == currentUserId
                    )
                }
                // Bottom spacer
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }

        // Input bar
        ChatInputBar(
            sending = sending,
            onSend = { content -> viewModel.sendMessage(content) }
        )
    }
}

@Composable
private fun MessageBubble(
    message: MessageVO,
    isOwn: Boolean
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val maxBubbleWidth = screenWidth * 0.75f

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxBubbleWidth)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isOwn) 16.dp else 4.dp,
                        bottomEnd = if (isOwn) 4.dp else 16.dp
                    )
                )
                .background(if (isOwn) AccentBlue else SurfaceCardLight)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = message.content,
                color = if (isOwn) Color.White else TextPrimary,
                fontSize = 15.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun TimeDivider(timestamp: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = formatMessageTime(timestamp),
            color = TextTertiary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun EmptyChatState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "暂无消息，发送第一条消息吧",
            color = TextTertiary,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun ChatInputBar(
    sending: Boolean,
    onSend: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = inputText,
            onValueChange = { inputText = it },
            modifier = Modifier.weight(1f),
            placeholder = {
                Text("输入消息...", color = TextTertiary)
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SurfaceCardLight,
                unfocusedContainerColor = SurfaceCardLight,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = AccentBlue,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(24.dp),
            singleLine = false,
            maxLines = 4
        )

        Spacer(modifier = Modifier.height(8.dp))

        IconButton(
            onClick = {
                if (inputText.isNotBlank()) {
                    onSend(inputText)
                    inputText = ""
                }
            },
            enabled = inputText.isNotBlank() && !sending,
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = if (inputText.isNotBlank() && !sending) AccentBlue else AccentBlue.copy(alpha = 0.3f),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "发送",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Show time divider if messages are more than 5 minutes apart.
 */
private fun shouldShowTimeDivider(prev: MessageVO, current: MessageVO): Boolean {
    return (current.createTime - prev.createTime) > 5 * 60 * 1000
}

/**
 * Format a millisecond timestamp to a readable time string.
 */
private fun formatMessageTime(timestamp: Long): String {
    if (timestamp == 0L) return ""
    return try {
        val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        sdf.format(java.util.Date(timestamp))
    } catch (_: Exception) {
        ""
    }
}
