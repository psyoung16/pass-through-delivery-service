package com.github.psyoung16.delivery.domain.document.events

import com.github.psyoung16.delivery.domain.document.DocumentId
import java.time.Instant

/**
 * 2차 인증 필요 (외부 기관 응답)
 */
data class TwoWayAuthRequired(
    override val documentId: DocumentId,
    val reason: String,
    override val occurredAt: Instant = Instant.now()
) : DocumentDomainEvent