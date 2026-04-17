package com.github.psyoung16.delivery.presentation.exception

import java.time.Instant

/**
 * 에러 응답 DTO
 */
data class ErrorResponse(
    val timestamp: Instant = Instant.now(),
    val status: Int,
    val error: String,
    val message: String,
    val path: String? = null
)