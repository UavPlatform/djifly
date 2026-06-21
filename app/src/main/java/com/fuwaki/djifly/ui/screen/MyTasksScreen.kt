package com.fuwaki.djifly.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuwaki.djifly.data.models.TaskStatus
import com.fuwaki.djifly.data.models.TaskType
import com.fuwaki.djifly.data.models.TaskVo
import com.fuwaki.djifly.ui.components.*
import com.fuwaki.djifly.ui.theme.*
import com.fuwaki.djifly.ui.viewmodel.ActionState
import com.fuwaki.djifly.ui.viewmodel.ContactState
import com.fuwaki.djifly.ui.viewmodel.TaskViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTasksScreen(
    viewModel: TaskViewModel,
    onTaskClick: (String) -> Unit,
    onStartFlight: () -> Unit = {},
    onContactUser: (Long, String) -> Unit = { _, _ -> },
    onBack: () -> Unit
) {
    val activeTasks by viewModel.activeTasks.collectAsState()
    val completedTasks by viewModel.completedTasks.collectAsState()
    val loading by viewModel.myTasksLoading.collectAsState()
    val actionState by viewModel.actionState.collectAsState()
    val contactState by viewModel.contactState.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("进行中", "已完成")

    // Dialog states
    var showCancelDialog by remember { mutableStateOf<TaskVo?>(null) }
    var showCompleteDialog by remember { mutableStateOf<TaskVo?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.loadMyTasks()
    }

    LaunchedEffect(actionState) {
        when (val state = actionState) {
            is ActionState.Success -> {
                snackbarHostState.showSnackbar(state.message, duration = SnackbarDuration.Short)
                viewModel.resetActionState()
            }
            is ActionState.Error -> {
                snackbarHostState.showSnackbar(state.message, duration = SnackbarDuration.Short)
                viewModel.resetActionState()
            }
            else -> {}
        }
    }

    LaunchedEffect(contactState) {
        when (val state = contactState) {
            is ContactState.Ready -> {
                onContactUser(state.sessionId, state.otherUserName)
                viewModel.resetContactState()
            }
            else -> {}
        }
    }

    Scaffold(
        containerColor = SurfaceDark,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("我的任务", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", color = TextPrimary, fontSize = 20.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = SurfaceDark,
                contentColor = TextPrimary,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier,
                            color = AccentBlue,
                            height = 3.dp
                        )
                    }
                },
                divider = {
                    HorizontalDivider(color = Divider, thickness = 0.5.dp)
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                color = if (selectedTab == index) AccentBlue else TextTertiary,
                                fontWeight = if (selectedTab == index) FontWeight.Medium else FontWeight.Normal,
                                fontSize = 15.sp
                            )
                        }
                    )
                }
            }

            // Content
            when {
                loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AccentBlue)
                    }
                }
                selectedTab == 0 -> {
                    // Active tasks
                    if (activeTasks.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无进行中的任务",
                                color = TextTertiary,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(activeTasks, key = { it.taskNum }) { task ->
                                ActiveTaskCard(
                                    task = task,
                                    isLoading = actionState is ActionState.Loading,
                                    isContactLoading = contactState is ContactState.Loading,
                                    onClick = { onTaskClick(task.taskNum) },
                                    onContact = { viewModel.contactTaskPublisher(task) },
                                    onCancel = { showCancelDialog = task },
                                    onComplete = { showCompleteDialog = task },
                                    onStartFlight = onStartFlight
                                )
                            }
                            item { Spacer(modifier = Modifier.height(80.dp)) }
                        }
                    }
                }
                selectedTab == 1 -> {
                    // Completed tasks
                    if (completedTasks.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无已完成的任务",
                                color = TextTertiary,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(completedTasks, key = { it.taskNum }) { task ->
                                CompletedTaskCard(
                                    task = task,
                                    onClick = { onTaskClick(task.taskNum) }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(80.dp)) }
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialogs
    showCancelDialog?.let { task ->
        ConfirmDialog(
            title = "取消接单",
            message = "确定要取消任务「${task.taskName}」吗？取消后任务将重新回到任务大厅。",
            confirmText = "确认取消",
            onConfirm = {
                viewModel.cancelTask(task.taskNum)
                showCancelDialog = null
            },
            onDismiss = { showCancelDialog = null }
        )
    }
    showCompleteDialog?.let { task ->
        ConfirmDialog(
            title = "完成任务",
            message = "确认已完成任务「${task.taskName}」的飞行？",
            confirmText = "确认完成",
            onConfirm = {
                viewModel.completeTask(task.taskNum)
                showCompleteDialog = null
            },
            onDismiss = { showCompleteDialog = null }
        )
    }
}

// ---------------------------------------------------------------------------
// Active task card (进行中)
// ---------------------------------------------------------------------------

@Composable
private fun ActiveTaskCard(
    task: TaskVo,
    isLoading: Boolean,
    isContactLoading: Boolean,
    onClick: () -> Unit,
    onContact: () -> Unit,
    onCancel: () -> Unit,
    onComplete: () -> Unit,
    onStartFlight: () -> Unit = {}
) {
    val taskType = TaskType.fromValue(task.taskType)
    val taskStatus = TaskStatus.fromValue(task.taskStatus)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: type badge + status dot + task name
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (taskType != null) {
                    TaskTypeBadge(type = taskType)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                // Status indicator dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .padding(end = 0.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = when (taskStatus) {
                            TaskStatus.IN_PROGRESS -> StatusGreen
                            TaskStatus.IDLE -> StatusAmber
                            else -> TextTertiary
                        }
                    ) {}
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = task.taskName,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }

            // Description
            if (!task.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = task.description,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Info row: price + waypoints + accept time
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = task.totalAmount?.let { "¥${it.toPlainString()}" } ?: "¥--",
                    color = AccentBlue,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${task.waypoints.size}个航点",
                        color = TextTertiary,
                        fontSize = 12.sp
                    )
                    if (!task.acceptTime.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "接单: ${task.acceptTime}",
                            color = TextTertiary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onContact,
                    enabled = !isContactLoading,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                ) {
                    if (isContactLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = TextSecondary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("联系用户", color = TextSecondary, fontSize = 13.sp)
                    }
                }
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                ) {
                    Text("取消接单", color = StatusRed, fontSize = 13.sp)
                }
                Button(
                    onClick = onStartFlight,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("开始飞行", color = Color.White, fontSize = 13.sp)
                    }
                }
            }

            // Complete task button (separate row)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onComplete,
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            ) {
                Text("完成任务", color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Completed task card (已完成)
// ---------------------------------------------------------------------------

@Composable
private fun CompletedTaskCard(
    task: TaskVo,
    onClick: () -> Unit
) {
    val taskType = TaskType.fromValue(task.taskType)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: type badge + completed badge + task name
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (taskType != null) {
                    TaskTypeBadge(type = taskType)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TaskStatusBadge(status = TaskStatus.COMPLETED)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = task.taskName,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Price row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = task.totalAmount?.let { "¥${it.toPlainString()}" } ?: "¥--",
                    color = AccentBlue,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "完成时间: ${task.updateTime ?: task.createTime}",
                    color = TextTertiary,
                    fontSize = 12.sp
                )
            }
        }
    }
}
