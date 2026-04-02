package com.fuwaki.djifly.platform.connection

sealed interface PlatformConnectionResult {
    data class Success(
        val message: String
    ) : PlatformConnectionResult

    data class Failure(
        val message: String,
        val retryable: Boolean
    ) : PlatformConnectionResult
}

sealed interface PlatformConnectionSessionResult {
    data object Stopped : PlatformConnectionSessionResult

    data class Failed(
        val reason: String,
        val retryable: Boolean
    ) : PlatformConnectionSessionResult
}
