package com.github.psyoung16.delivery.domain.document

/**
 * 서류 발급 방식
 */
enum class IssuanceMethod(val description: String) {
    API_ISSUED("외부 API 자동 발급"),
    USER_UPLOADED("사용자 직접 업로드")
}
