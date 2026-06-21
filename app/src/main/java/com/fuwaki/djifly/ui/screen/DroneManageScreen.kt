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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuwaki.djifly.data.error.GlobalErrorHandler
import com.fuwaki.djifly.data.error.apiCall
import com.fuwaki.djifly.data.models.RiderUav
import com.fuwaki.djifly.platform.network.PlatformApiService
import com.fuwaki.djifly.ui.theme.AccentBlue
import com.fuwaki.djifly.ui.theme.Divider
import com.fuwaki.djifly.ui.theme.StatusRed
import com.fuwaki.djifly.ui.theme.SurfaceCard
import com.fuwaki.djifly.ui.theme.SurfaceDark
import com.fuwaki.djifly.ui.theme.TextPrimary
import com.fuwaki.djifly.ui.theme.TextSecondary
import com.fuwaki.djifly.ui.theme.TextTertiary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ──

@HiltViewModel
class DroneManageViewModel @Inject constructor(
    private val api: PlatformApiService,
    val errorHandler: GlobalErrorHandler
) : ViewModel() {

    private val _drones = MutableStateFlow<List<RiderUav>>(emptyList())
    val drones: StateFlow<List<RiderUav>> = _drones.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        loadDrones()
    }

    fun loadDrones() {
        viewModelScope.launch {
            _loading.value = true
            apiCall(errorHandler) { api.getDroneList() }
                .onSuccess { data ->
                    _drones.value = data ?: emptyList()
                }
            _loading.value = false
        }
    }

    fun bindDrone(djiId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            apiCall(errorHandler) { api.bindDrone(djiId) }
                .onSuccess {
                    loadDrones()
                    onSuccess()
                }
        }
    }

    fun unbindDrone(djiId: String) {
        viewModelScope.launch {
            apiCall(errorHandler) { api.unbindDrone(djiId) }
                .onSuccess {
                    loadDrones()
                }
        }
    }
}

// ── Screen ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DroneManageScreen(
    onBack: () -> Unit,
    viewModel: DroneManageViewModel = hiltViewModel()
) {
    val drones by viewModel.drones.collectAsState()
    val loading by viewModel.loading.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var unbindTarget by remember { mutableStateOf<RiderUav?>(null) }

    Scaffold(
        containerColor = SurfaceDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "我的无人机",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = AccentBlue,
                contentColor = TextPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加无人机"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (drones.isEmpty() && !loading) {
                EmptyDroneState(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                    items(drones, key = { it.id }) { drone ->
                        DroneCard(
                            drone = drone,
                            onUnbind = { unbindTarget = drone }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }

            // Bottom info text
            Text(
                text = "💡 提示: 解绑后将无法使用该无人机接单",
                color = TextTertiary,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }

    // Add drone dialog
    if (showAddDialog) {
        AddDroneDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { djiId ->
                viewModel.bindDrone(djiId) { showAddDialog = false }
            }
        )
    }

    // Confirm unbind dialog
    unbindTarget?.let { drone ->
        ConfirmUnbindDialog(
            droneName = maskDjiId(drone.djiId),
            onDismiss = { unbindTarget = null },
            onConfirm = {
                viewModel.unbindDrone(drone.djiId)
                unbindTarget = null
            }
        )
    }
}

@Composable
private fun DroneCard(
    drone: RiderUav,
    onUnbind: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drone icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = AccentBlue.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✈️",
                    fontSize = 24.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "DJI Drone",
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "DJI ID: ${maskDjiId(drone.djiId)}",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "绑定时间: ${drone.createTime}",
                    color = TextTertiary,
                    fontSize = 12.sp
                )
            }

            OutlinedButton(
                onClick = onUnbind,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, StatusRed)
            ) {
                Text(
                    text = "解绑",
                    color = StatusRed,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun EmptyDroneState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "暂无绑定的无人机",
            color = TextTertiary,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun AddDroneDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var djiId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("添加无人机", color = TextPrimary)
        },
        text = {
            Column {
                Text(
                    text = "请输入无人机的 DJI ID",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = djiId,
                    onValueChange = { djiId = it },
                    placeholder = { Text("DJI ID", color = TextTertiary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = AccentBlue,
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = Divider
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(djiId) },
                enabled = djiId.isNotBlank()
            ) {
                Text("确认添加", color = if (djiId.isNotBlank()) AccentBlue else TextTertiary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        },
        containerColor = SurfaceCard,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary
    )
}

@Composable
private fun ConfirmUnbindDialog(
    droneName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("确认解绑", color = TextPrimary)
        },
        text = {
            Text(
                text = "确定要解绑无人机 $droneName 吗？解绑后将无法使用该无人机接单。",
                color = TextSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确认解绑", color = StatusRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        },
        containerColor = SurfaceCard,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary
    )
}

/**
 * Mask the DJI ID for display (show first 4 and last 4 characters).
 */
private fun maskDjiId(djiId: String): String {
    if (djiId.length <= 8) return djiId
    val prefix = djiId.substring(0, 4)
    val suffix = djiId.substring(djiId.length - 4)
    return "$prefix****$suffix"
}
