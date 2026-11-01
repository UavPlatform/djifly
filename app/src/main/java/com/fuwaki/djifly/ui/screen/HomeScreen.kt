package com.fuwaki.djifly.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.fuwaki.djifly.platform.dispatch.DispatchStages
import com.fuwaki.djifly.ui.components.AircraftBar
import com.fuwaki.djifly.ui.components.DispatchOrderCard
import com.fuwaki.djifly.ui.viewmodel.HomeOrdersLoad
import com.fuwaki.djifly.ui.viewmodel.HomeViewModel

/**
 * 作业壳（DS-DJIFLY-002）：飞机条 + 至多一单 + 一个主按钮；状态说真话。
 */
@Composable
fun HomeScreen(
    onNavigateToStarter: (String?) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val dispatchState by viewModel.dispatchState.collectAsState()
    val aircraftBar by viewModel.aircraftBar.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // 从飞行页回到作业页必须重拉列表
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadHomeData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            HomeHeader(userName = uiState.userName)
        }

        item {
            AircraftBar(
                ui = aircraftBar,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        when (val load = uiState.load) {
            HomeOrdersLoad.Loading -> item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "正在加载可飞订单…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(onClick = { viewModel.loadHomeData() }) {
                            Text("重试")
                        }
                    }
                }
            }
            is HomeOrdersLoad.Failed -> item {
                ShellStateCard(
                    title = "网络异常，拉不到订单",
                    body = load.message,
                    actionLabel = "重试",
                    onAction = { viewModel.loadHomeData() }
                )
            }
            HomeOrdersLoad.Ready -> {
                val order = uiState.currentOrder
                if (order == null) {
                    item {
                        ShellStateCard(
                            title = "没有可飞的单",
                            body = "当前没有可执飞订单。确认后可飞的订单会出现在这里。",
                            actionLabel = "重试刷新",
                            onAction = { viewModel.loadHomeData() }
                        )
                    }
                } else {
                    val cardState = dispatchState.copy(currentOrder = order)
                    val canEnter = aircraftBar.connected
                    item {
                        DispatchOrderCard(
                            state = cardState,
                            uploadPhrase = aircraftBar.uploadStatus.phrase,
                            primaryEnabled = canEnter,
                            primaryDisabledReason = if (!canEnter) {
                                aircraftBar.nextStepHint ?: "未确认飞机，不能进入飞行页"
                            } else null,
                            onStart = { onNavigateToStarter(order.orderId) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    // 订单摘要就在本页：阶段标签已在卡片内；补充起降/货物摘要
                    item {
                        OrderSummaryBlock(orderStage = DispatchStages.label(order.stage))
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(userName: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (userName.isNullOrBlank()) "飞翼通 · 现场作业" else "$userName · 现场作业",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ShellStateCard(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.TaskAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun OrderSummaryBlock(orderStage: String) {
    Text(
        text = "当前阶段：$orderStage（详情已收在本页，不再跳旧任务详情）",
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall
    )
}
