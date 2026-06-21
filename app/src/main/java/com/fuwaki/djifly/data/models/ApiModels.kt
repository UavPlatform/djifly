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

data class LoginRequest(
    val userName: String,
    val password: String
)

data class RegisterRequest(
    val userName: String,
    val password: String,
    val djiId: String? = null
)

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
// 聊天相关
// ═══════════════════════════════════════════════════════

data class SessionVO(
    val id: Long = 0,
    val name: String = "",
    val type: Int = 0,              // 0=单聊, 1=群聊
    val userIds: List<Long> = emptyList(),
    val otherUserName: String? = null,
    val lastMessage: String? = null,
    val createTime: Long = 0L,      // 后端返回时间戳
    val lastMessageTime: Long? = null
)

/**
 * 聊天消息 —— 与后端 ChatMessage / MessageVO 对齐
 * 后端字段: id, msgType, content, fromUserId, status, createTime
 */
data class MessageVO(
    val id: String = "",            // 后端返回消息 ID
    val sessionId: Long = 0,
    val msgType: Int = 0,           // 0=CHAT, 1=NOTICE, 2=ORDER, 3=COMMAND
    val content: String = "",       // 消息文本内容
    val fromUserId: Long = 0,
    val toUserId: Long? = null,
    val status: Int = 0,            // 0=正常, 2=已撤回
    val createTime: Long = 0L       // 后端返回时间戳
) {
    // 从 WebSocket ChatEnvelope 构建
    companion object {
        fun fromEnvelope(envelope: ChatEnvelope): MessageVO {
            return envelope.toMessageVO()
        }
    }
}

data class ChatEnvelope(
    val msgId: String = "",
    val sessionId: Long = 0,
    val msgType: Any? = null,
    val timestamp: Long = 0,
    val fromUserId: Long = 0,
    val toUserId: Long? = null,
    val payload: Map<String, Any> = emptyMap(),
    val isOffline: Boolean = false,
    val needAck: Boolean = false
) {
    fun toMessageVO(): MessageVO {
        val typeValue = when (msgType) {
            is Number -> msgType.toInt()
            is String -> when (msgType.uppercase()) {
                "CHAT" -> 0
                "NOTICE" -> 1
                "ORDER" -> 2
                "COMMAND" -> 3
                else -> 0
            }
            else -> 0
        }

        return MessageVO(
            id = msgId,
            sessionId = sessionId,
            msgType = typeValue,
            content = payload["text"]?.toString().orEmpty(),
            fromUserId = fromUserId,
            toUserId = toUserId,
            createTime = timestamp
        )
    }
}

data class CreateSessionRequest(
    val name: String,
    val type: Int,
    val userIds: List<Long>
)

data class SendMessageRequest(
    val sessionId: Long,
    val content: String
)

// ═══════════════════════════════════════════════════════
// 无人机相关
// ═══════════════════════════════════════════════════════

data class RiderUav(
    val id: Long = 0,
    val userId: Long = 0,
    val djiId: String = "",
    val createTime: String = ""
)
