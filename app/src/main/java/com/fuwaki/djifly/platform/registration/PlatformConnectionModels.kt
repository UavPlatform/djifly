package com.fuwaki.djifly.platform.registration

sealed interface PlatformConnectionResult {
    data class Success(
        val message: String
    ) : PlatformConnectionResult

    data class Failure(
        val message: String,
        val retryable: Boolean
    ) : PlatformConnectionResult
}
