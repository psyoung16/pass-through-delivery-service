package com.github.psyoung16.delivery.domain.document

/**
 * 서류 식별자
 */
@JvmInline
value class DocumentId(val value: Long) {
    init {
        require(value > 0) { "DocumentId must be positive" }
    }

    override fun toString(): String = "DocumentId($value)"
}
