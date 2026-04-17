package com.github.psyoung16.delivery.presentation.dto

/**
 * 2차 인증 후 재시도 Request DTO
 */
data class RetryRequest(
    val authToken: String? = null
)