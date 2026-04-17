package com.github.psyoung16.delivery.domain.document

/**
 * 서류 발급 상태
 */
enum class DocumentStatus(val description: String) {
    REQUESTED("요청됨"),
    PROCESSING("처리 중"),
    TWO_WAY_AUTH_REQUIRED("2-way 인증 필요"),
    COMPLETED("발급 완료"),
    FAILED("발급 실패")
}
