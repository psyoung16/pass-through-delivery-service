package com.github.psyoung16.delivery.presentation.dto

/**
 * API 자동 발급 응답 (2-way 인증 요청 결과)
 */
data class ApiIssuanceResponse(
    val documentId: Long,
    val status: String,     // TWO_WAY_AUTH_REQUIRED

    /**
     * 요청 식별자
     * 보안: 실제 운영에서는 UUID 매핑 테이블 필요 (현재는 간소화를 위해 스킵)
     */
    val requestId: String,

    val message: String     // "카카오톡 인증을 진행해주세요"
)