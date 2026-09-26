package com.fuwaki.djifly.data.models

import java.math.BigDecimal

// ═══════════════════════════════════════════════════════
// 统一响应信封 —— 与后端 Result<T> 对应
// ═══════════════════════════════════════════════════════

data class ApiResult<T>(
    val success: Boolean = false,
    val code: Int = 0,
    val errorCode: String? = null,
    val message: String? = null,
    val data: T? = null
)

// ═══════════════════════════════════════════════════════
// 认证相关
// ═══════════════════════════════════════════════════════

data class LoginResponse(
    val token: String,
    val refreshToken: String
)

data class RefreshResponse(
    val token: String
)

data class UserInfo(
    val userId: Long,
    val userName: String,
    val role: Int // 0=user, 1=rider, 2=admin
)

// ═══════════════════════════════════════════════════════
// 任务相关
// ═══════════════════════════════════════════════════════

data class TaskPageVO(
    val tasks: List<TaskVo> = emptyList(),
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val totalElements: Long = 0
)

data class TaskVo(
    val id: Long = 0,
    val taskNum: String = "",
    val taskName: String = "",
    val userId: Long = 0,
    val taskType: String = "",       // AERIAL_PHOTO, SURVEY, TRANSPORT, MONITORING
    val taskStatus: String = "",     // IDLE, IN_PROGRESS, COMPLETED
    val description: String? = null,
    val riderId: Long? = null,
    val riderName: String? = null,
    val acceptTime: String? = null,
    val orderNum: String? = null,
    val totalAmount: BigDecimal? = null,
    val totalDistance: BigDecimal? = null,
    val orderStatus: String? = null, // 后端返回枚举名: PENDING/PAID/COMPLETED 等
    val actionHint: String? = null,  // 操作提示（1B-9a 状态矩阵，后端统一计算，客户端直接渲染）
    /** 撮合子状态（ADR-0003 决定 6）：SEEKING_RIDER→…→CONFIRMED→PENDING_ACCEPTANCE→CLOSED */
    val matchStatus: String? = null,
    /** 选定应征的系统报价（= 成交价 totalAmount；未选定时为 null） */
    val quotedAmount: BigDecimal? = null,
    /** 成交机型显示名（如 DJI FlyCart 30；未选定时为 null） */
    val aircraftModelName: String? = null,
    /** 约定作业时间（用户下单时提交，"yyyy-MM-dd HH:mm:ss"） */
    val scheduledTime: String? = null,
    /** 吊运货物重量 kg */
    val cargoWeightKg: BigDecimal? = null,
    /** 吊运货物类别（CONSTRUCTION/EQUIPMENT/AGRICULTURAL） */
    val cargoCategory: String? = null,
    /** 任务期望执行时间（"yyyy-MM-dd HH:mm:ss"） */
    val taskTime: String? = null,
    val createTime: String = "",
    val updateTime: String? = null,
    val waypoints: List<WaypointVo> = emptyList()
)

data class WaypointVo(
    val id: Long = 0,
    val orderIndex: Int = 0,
    val longitude: Double = 0.0,
    val latitude: Double = 0.0,
    val altitude: Double = 0.0
)

// 任务类型枚举
enum class TaskType(val label: String) {
    AERIAL_PHOTO("航拍"),
    SURVEY("测绘"),
    TRANSPORT("物流"),
    MONITORING("监测");

    companion object {
        fun fromValue(value: String): TaskType? = entries.find { it.name == value }
    }
}

// 任务状态枚举
enum class TaskStatus(val label: String) {
    IDLE("空闲中"),
    IN_PROGRESS("执行中"),
    COMPLETED("已完成");

    companion object {
        fun fromValue(value: String): TaskStatus? = entries.find { it.name == value }
    }
}

// ═══════════════════════════════════════════════════════
// 无人机相关
// ═══════════════════════════════════════════════════════

data class RiderUav(
    val id: Long = 0,
    val userId: Long = 0,
    val djiId: String = "",
    val createTime: String = ""
)