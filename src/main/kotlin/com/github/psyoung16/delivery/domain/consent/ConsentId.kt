package com.github.psyoung16.delivery.domain.consent

/**
 * 동의서 식별자
 */
@JvmInline
value class ConsentId(val value: Long) {
    init {
        require(value > 0) { "ConsentId must be positive" }
    }

    override fun toString(): String = "ConsentId($value)"
}
