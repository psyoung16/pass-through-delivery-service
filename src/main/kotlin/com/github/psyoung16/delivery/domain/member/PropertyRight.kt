package com.github.psyoung16.delivery.domain.member

import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.math.BigDecimal

/**
 * 권리 내역
 *
 * @property type 권리 유형 (건물/토지)
 * @property address 소재지 (예: "101동 103호", "123-45 번지")
 * @property shareRatio 지분율 (0.0 ~ 1.0, 예: 0.6 = 60%)
 */
@Embeddable
data class PropertyRight(
    @Enumerated(EnumType.STRING)
    val type: PropertyType,
    val address: String,
    val shareRatio: BigDecimal = BigDecimal.ONE
) {
    init {
        require(address.isNotBlank()) { "Address must not be blank" }
        require(shareRatio > BigDecimal.ZERO && shareRatio <= BigDecimal.ONE) {
            "Share ratio must be between 0 and 1"
        }
    }
}
