package com.fuwaki.djifly.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuwaki.djifly.data.models.TaskType
import com.fuwaki.djifly.data.models.TaskVo
import com.fuwaki.djifly.ui.components.ConfirmDialog
import com.fuwaki.djifly.ui.components.TaskCard
import com.fuwaki.djifly.ui.theme.*
import com.fuwaki.djifly.ui.viewmodel.ActionState
import com.fuwaki.djifly.ui.viewmodel.TaskViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskSquareScreen(
    viewModel: TaskViewModel,
    onTaskClick: (String) -> Unit
) {
    val tasks by viewModel.squareTasks.collectAsState()
    val loading by viewModel.squareLoading.collectAsState()
    val selectedType by viewModel.selectedType.collectAsState()
    val actionState by viewModel.actionState.collectAsState()

    // Confirmation dialog state
    var showAcceptDialog by remember { mutableStateOf<TaskVo?>(null) }

    // Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
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

    // Load on first composition
    LaunchedEffect(Unit) {
        viewModel.loadSquare()
    }

    // Filtered tasks
    val filteredTasks = remember(tasks, selectedType) {
        if (selectedType == null) tasks
        else tasks.filter { it.taskType == selectedType!!.name }
    }

    Scaffold(
        containerColor = SurfaceDark,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Title
            Text(
                text = "任务大厅",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            )

            // Filter chips
            FilterChipRow(
                selectedType = selectedType,
                onTypeSelected = { viewModel.filterByType(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

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
                filteredTasks.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无可用任务，稍后再来看看",
                            color = TextTertiary,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredTasks, key = { it.taskNum }) { task ->
                            TaskCard(
                                task = task,
                                onAccept = { showAcceptDialog = task },
                                onClick = { onTaskClick(task.taskNum) }
                            )
                        }
                        // Bottom spacer
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }

    // Accept confirmation dialog
    showAcceptDialog?.let { task ->
        ConfirmDialog(
            title = "确认接单",
            message = "确定要接受任务「${task.taskName}」吗？",
            confirmText = "确认接单",
            onConfirm = {
                viewModel.acceptTask(task.taskNum)
                showAcceptDialog = null
            },
            onDismiss = { showAcceptDialog = null }
        )
    }
}

@Composable
private fun FilterChipRow(
    selectedType: TaskType?,
    onTypeSelected: (TaskType?) -> Unit
) {
    data class ChipItem(val label: String, val type: TaskType?)

    val chips = listOf(
        ChipItem("全部", null),
        ChipItem("航拍", TaskType.AERIAL_PHOTO),
        ChipItem("测绘", TaskType.SURVEY),
        ChipItem("物流", TaskType.TRANSPORT),
        ChipItem("监测", TaskType.MONITORING)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEach { chip ->
            val isSelected = selectedType == chip.type
            val bgColor = if (isSelected) AccentBlue else SurfaceCard
            val textColor = if (isSelected) Color.White else TextSecondary

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(bgColor)
                    .clickable { onTypeSelected(chip.type) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = chip.label,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}
