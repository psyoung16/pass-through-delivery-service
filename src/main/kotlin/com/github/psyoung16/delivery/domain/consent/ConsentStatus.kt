package com.github.psyoung16.delivery.domain.consent

/**
 * 동의서 상태
 */
enum class ConsentStatus(val description: String) {
    DRAFT("작성 중"),
    SUBMITTED("제출 완료")
}
