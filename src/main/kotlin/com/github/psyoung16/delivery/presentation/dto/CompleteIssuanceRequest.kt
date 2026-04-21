package com.github.psyoung16.delivery.presentation.dto

/**
 * 2-way 인증 완료 후 실제 발급 요청
 */
data class CompleteIssuanceRequest(
    /**
     * Step 1에서 받은 requestId
     * 보안: 실제 운영에서는 UUID 매핑 테이블 필요 (현재는 스킵)
     */
    val requestId: String
)