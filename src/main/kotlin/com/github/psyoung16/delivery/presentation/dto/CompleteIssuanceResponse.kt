package com.github.psyoung16.delivery.presentation.dto

/**
 * 2-way 인증 완료 후 실제 발급 응답
 */
data class CompleteIssuanceResponse(
    val status: String,       // COMPLETED or FAILED
    val fileUrl: String?,     // 발급 성공 시 파일 URL
    val failureReason: String?  // 실패 시 사유
)