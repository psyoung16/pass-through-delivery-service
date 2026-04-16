package com.github.psyoung16.delivery.domain.member

import jakarta.persistence.Embeddable

/**
 * 연락처 정보
 */
@Embeddable
data class ContactInfo(
    val phoneNumber: String,
    val email: String? = null,
    val address: String? = null
) {
    init {
        require(phoneNumber.isNotBlank()) { "Phone number must not be blank" }
        email?.let {
            require(it.contains("@")) { "Invalid email format" }
        }
    }
}
