package com.github.psyoung16.delivery.domain.member

/**
 * 조합원 식별자
 */
@JvmInline
value class MemberId(val value: Long) {
    init {
        require(value > 0) { "MemberId must be positive" }
    }

    override fun toString(): String = "MemberId($value)"
}
