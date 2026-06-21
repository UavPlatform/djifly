package com.fuwaki.djifly.data.error

import retrofit2.Response

/**
 * 应用级错误类型 —— 统一封装后端错误，UI 层根据类型展示不同交互
 */
sealed class AppError(val message: String) {
    /** 需要绑定无人机（后端 401 + 特定消息） */
    class DroneRequired(message: String) : AppError(message)

    /** Token 过期或无效（后端 401 通用） */
    class Unauthorized(message: String) : AppError(message)

    /** 请求过于频繁（后端 429） */
    class RateLimited(message: String) : AppError(message)

    /** 网络不通 / 超时 */
    class Network(message: String) : AppError(message)

    /** 后端返回的业务错误（success=false） */
    class Business(message: String) : AppError(message)

    /** HTTP 非 200 状态码 */
    class Http(val code: Int, message: String) : AppError(message)

    /** 未知异常 */
    class Unknown(message: String) : AppError(message)
}

/**
 * 从 Retrofit Response 中提取错误信息，返回 AppError
 */
fun <T> Response<T>.toAppError(): AppError {
    val code = code()
    return when {
        code == 429 -> {
            val bodyMessage = try {
                val errorBody = errorBody()?.string()
                if (errorBody != null) {
                    val gson = com.google.gson.Gson()
                    val map = gson.fromJson(errorBody, Map::class.java)
                    map["message"] as? String
                } else null
            } catch (_: Exception) { null }
            AppError.RateLimited(bodyMessage ?: "请求过于频繁，请稍后重试")
        }
        code == 401 -> {
            // 尝试从 body 中读取后端返回的 message
            val bodyMessage = try {
                val errorBody = errorBody()?.string()
                if (errorBody != null) {
                    val gson = com.google.gson.Gson()
                    val map = gson.fromJson(errorBody, Map::class.java)
                    map["message"] as? String
                } else null
            } catch (_: Exception) { null }

            val msg = bodyMessage ?: "未授权"
            if (msg.contains("无人机") || msg.contains("drone", ignoreCase = true)) {
                AppError.DroneRequired(msg)
            } else {
                AppError.Unauthorized(msg)
            }
        }
        code in 500..599 -> AppError.Http(code, "服务器错误 ($code)")
        code == 0 -> AppError.Network("网络连接失败，请检查网络")
        else -> {
            val bodyMessage = try {
                val errorBody = errorBody()?.string()
                if (errorBody != null) {
                    val gson = com.google.gson.Gson()
                    val map = gson.fromJson(errorBody, Map::class.java)
                    map["message"] as? String
                } else null
            } catch (_: Exception) { null }
            AppError.Http(code, bodyMessage ?: "请求失败 ($code)")
        }
    }
}

/**
 * 从异常中提取 AppError
 */
fun Throwable.toAppError(): AppError {
    return when (this) {
        is java.net.UnknownHostException -> AppError.Network("无法连接服务器，请检查网络")
        is java.net.SocketTimeoutException -> AppError.Network("连接超时，请稍后重试")
        is java.net.ConnectException -> AppError.Network("连接失败，请检查服务器地址")
        is javax.net.ssl.SSLException -> AppError.Network("安全连接失败")
        else -> AppError.Unknown(this.localizedMessage ?: "未知错误")
    }
}

/**
 * 从 ApiResult（后端 Result<T> 信封）中提取错误
 */
fun <T> com.fuwaki.djifly.data.models.ApiResult<T>.toAppError(): AppError {
    val msg = message ?: "操作失败"
    return when (code) {
        401 -> {
            if (msg.contains("无人机") || msg.contains("drone", ignoreCase = true)) {
                AppError.DroneRequired(msg)
            } else {
                AppError.Unauthorized(msg)
            }
        }
        in 500..599 -> AppError.Http(code, msg)
        else -> AppError.Business(msg)
    }
}
