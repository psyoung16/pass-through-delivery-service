package com.github.psyoung16.delivery.domain

import java.time.Instant

/**
 * 도메인 이벤트 Base 인터페이스
 */
interface DomainEvent {
    val occurredAt: Instant
        get() = Instant.now()
}
