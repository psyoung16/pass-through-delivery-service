package com.github.psyoung16.delivery.domain.consent

/**
 * 동의서 제출 방식
 */
enum class ConsentType(val description: String) {
    ELECTRONIC("전자 동의"),
    PAPER("종이 동의서")
}
