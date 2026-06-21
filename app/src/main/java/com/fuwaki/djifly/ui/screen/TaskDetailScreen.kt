package com.fuwaki.djifly.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
fun TaskDetailScreen(
    taskNum: String,
    viewModel: TaskViewModel,
    onBack: () -> Unit,
    onStartFlight: () -> Unit = {},
    onContactUser: (Long, String) -> Unit = { _, _ -> }
) {
    val taskDetail by viewModel.taskDetail.collectAsState()
    val loading by viewModel.detailLoading.collectAsState()
    val actionState by viewModel.actionState.collectAsState()
    val contactState by viewModel.contactState.collectAsState()

    // Dialog states
    var showAcceptDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var showCompleteDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(taskNum) {
        viewModel.loadTaskDetail(taskNum)
    }

    LaunchedEffect(actionState) {
        when (val state = actionState) {
            is ActionState.Success -> {
                snackbarHostState.showSnackbar(state.message, duration = SnackbarDuration.Short)
                viewModel.resetActionState()
                // Reload detail after action
                viewModel.loadTaskDetail(taskNum)
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
                title = { Text("任务详情", fontWeight = FontWeight.Bold) },
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
        },
        bottomBar = {
            taskDetail?.let { task ->
                val status = TaskStatus.fromValue(task.taskStatus)
                DetailBottomBar(
                    status = status,
                    isLoading = actionState is ActionState.Loading,
                    isContactLoading = contactState is ContactState.Loading,
                    onContact = { viewModel.contactTaskPublisher(task) },
                    onAccept = { showAcceptDialog = true },
                    onCancel = { showCancelDialog = true },
                    onComplete = { showCompleteDialog = true },
                    onStartFlight = onStartFlight
                )
            }
        }
    ) { padding ->
        when {
            loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentBlue)
                }
            }
            taskDetail == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无任务信息", color = TextTertiary, fontSize = 15.sp)
                }
            }
            else -> {
                val task = taskDetail!!
                val taskType = TaskType.fromValue(task.taskType)
                val taskStatus = TaskStatus.fromValue(task.taskStatus)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Status header card
                    StatusHeaderCard(task = task, taskType = taskType, taskStatus = taskStatus)

                    // Task info section
                    DetailSection(title = "任务信息") {
                        Text(
                            text = task.taskName,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        if (!task.description.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = task.description,
                                color = TextSecondary,
                                fontSize = 14.sp,
                                lineHeight = 22.sp
                            )
                        }
                    }

                    // Waypoints section
                    if (task.waypoints.isNotEmpty()) {
                        DetailSection(title = "航点信息 (${task.waypoints.size}个)") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                task.waypoints
                                    .sortedBy { it.orderIndex }
                                    .forEachIndexed { index, waypoint ->
                                        WaypointItem(waypoint = waypoint, index = index)
                                    }
                            }
                        }
                    }

                    // Meta info section
                    DetailSection(title = "详细信息") {
                        MetaRow(label = "任务编号", value = task.taskNum)
                        MetaRow(label = "创建时间", value = task.createTime)
                        if (!task.acceptTime.isNullOrBlank()) {
                            MetaRow(label = "接单时间", value = task.acceptTime)
                        }
                        if (!task.riderName.isNullOrBlank()) {
                            MetaRow(label = "骑手", value = task.riderName)
                        }
                        task.totalAmount?.let {
                            MetaRow(label = "任务金额", value = "¥${it.toPlainString()}")
                        }
                        task.totalDistance?.let {
                            MetaRow(label = "总距离", value = "${it.toPlainString()} km")
                        }
                    }

                    // Bottom spacer for the action bar
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    // Confirmation dialogs
    if (showAcceptDialog) {
        ConfirmDialog(
            title = "确认接单",
            message = "确定要接受此任务吗？",
            confirmText = "确认接单",
            onConfirm = {
                viewModel.acceptTask(taskNum)
                showAcceptDialog = false
            },
            onDismiss = { showAcceptDialog = false }
        )
    }
    if (showCancelDialog) {
        ConfirmDialog(
            title = "取消接单",
            message = "确定要取消此任务吗？取消后任务将重新回到任务大厅。",
            confirmText = "确认取消",
            onConfirm = {
                viewModel.cancelTask(taskNum)
                showCancelDialog = false
            },
            onDismiss = { showCancelDialog = false }
        )
    }
    if (showCompleteDialog) {
        ConfirmDialog(
            title = "完成任务",
            message = "确认已完成飞行任务？",
            confirmText = "确认完成",
            onConfirm = {
                viewModel.completeTask(taskNum)
                showCompleteDialog = false
            },
            onDismiss = { showCompleteDialog = false }
        )
    }
}

// ---------------------------------------------------------------------------
// Status header card
// ---------------------------------------------------------------------------

@Composable
private fun StatusHeaderCard(
    task: TaskVo,
    taskType: TaskType?,
    taskStatus: TaskStatus?
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (taskType != null) {
                    TaskTypeBadge(type = taskType)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (taskStatus != null) {
                    TaskStatusBadge(status = taskStatus)
                }
            }
            Text(
                text = task.totalAmount?.let { "¥${it.toPlainString()}" } ?: "¥--",
                color = AccentBlue,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Detail section wrapper
// ---------------------------------------------------------------------------

@Composable
private fun DetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                color = TextTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

// ---------------------------------------------------------------------------
// Meta row (label: value)
// ---------------------------------------------------------------------------

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextTertiary, fontSize = 13.sp)
        Text(text = value, color = TextPrimary, fontSize = 13.sp)
    }
}

// ---------------------------------------------------------------------------
// Bottom action bar
// ---------------------------------------------------------------------------

@Composable
private fun DetailBottomBar(
    status: TaskStatus?,
    isLoading: Boolean,
    isContactLoading: Boolean,
    onContact: () -> Unit,
    onAccept: () -> Unit,
    onCancel: () -> Unit,
    onComplete: () -> Unit,
    onStartFlight: () -> Unit = {}
) {
    Surface(
        color = SurfaceCard,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (status) {
                TaskStatus.IDLE -> {
                    OutlinedButton(
                        onClick = onContact,
                        enabled = !isContactLoading,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        if (isContactLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = TextSecondary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("联系用户", color = TextSecondary)
                        }
                    }
                    Button(
                        onClick = onAccept,
                        enabled = !isLoading,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("立即接单", color = Color.White)
                        }
                    }
                }
                TaskStatus.IN_PROGRESS -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                                    .height(48.dp)
                            ) {
                                if (isContactLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = TextSecondary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("联系用户", color = TextSecondary)
                                }
                            }
                            OutlinedButton(
                                onClick = onCancel,
                                enabled = !isLoading,
                                shape = RoundedCornerShape(12.dp),
                                border = ButtonDefaults.outlinedButtonBorder,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("取消接单", color = StatusRed)
                            }
                            Button(
                                onClick = onStartFlight,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("开始飞行", color = Color.White)
                            }
                        }
                        OutlinedButton(
                            onClick = onComplete,
                            enabled = !isLoading,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = TextSecondary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("完成任务", color = TextSecondary)
                            }
                        }
                    }
                }
                TaskStatus.COMPLETED -> {
                    OutlinedButton(
                        onClick = onContact,
                        enabled = !isContactLoading,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        if (isContactLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = TextSecondary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("联系用户", color = TextSecondary)
                        }
                    }
                }
                else -> {}
            }
        }
    }
}
