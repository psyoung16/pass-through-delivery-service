package com.github.psyoung16.delivery.domain.document

/**
 * Document 이벤트 타입
 */
enum class DocumentEventType(val description: String) {
    DOCUMENT_REQUESTED("서류 발급 요청됨"),
    DOCUMENT_UPLOADED("서류 업로드됨"),
    TWO_WAY_AUTH_REQUIRED("2차 인증 필요"),
}
