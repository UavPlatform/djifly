package com.fuwaki.djifly.data.stats

import com.fuwaki.djifly.data.error.GlobalErrorHandler
import com.fuwaki.djifly.data.error.apiCall
import com.fuwaki.djifly.data.models.RiderStats
import com.fuwaki.djifly.platform.network.PlatformApiService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 飞手统计仓库（GET /rider/stats，契约 C9）。
 * HomeScreen 与 ProfileScreen 共用同一数据源，替代本地拼凑估算（FLY 中-7）。
 */
@Singleton
class RiderStatsRepository @Inject constructor(
    private val api: PlatformApiService,
    private val errorHandler: GlobalErrorHandler
) {

    /**
     * 拉取并映射为展示态。
     * @return 失败返回 null（错误已由全局 errorHandler 上报），调用方保留旧值或零值
     */
    suspend fun loadStats(): RiderStatsUiState? {
        var mapped: RiderStatsUiState? = null
        apiCall(errorHandler) { api.getRiderStats() }
            .onSuccess { stats ->
                // 契约模型（ApiRiderStatsVO）→ 领域模型（RiderStats）：只读取本端使用的字段。
                mapped = RiderStatsPresentation.toUiState(
                    stats?.let {
                        RiderStats(
                            todayOrders = it.todayOrders ?: 0L,
                            totalCompleted = it.totalCompleted ?: 0L,
                            totalEarnings = it.totalEarnings
                        )
                    }
                )
            }
        return mapped
    }
}