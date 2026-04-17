package com.github.psyoung16.delivery.domain.document

/**
 * Document 이벤트 타입
 */
enum class DocumentEventType(val description: String) {
    DOCUMENT_REQUESTED("서류 발급 요청됨"),
    DOCUMENT_UPLOADED("서류 업로드됨"),
    TWO_WAY_AUTH_REQUIRED("2차 인증 필요"),
    PROCESSING_STARTED("외부 기관 처리 시작"),
    PROCESSING_RETRIED("2차 인증 후 재시도"),
    DOCUMENT_ISSUED("서류 발급 완료"),
    DOCUMENT_ISSUE_FAILED("서류 발급 실패"),
}
