package com.github.psyoung16.delivery.domain.document.events

import com.github.psyoung16.delivery.domain.document.DocumentId
import java.time.Instant

/**
 * 외부 기관에 서류 발급 처리 시작됨
 */
data class ProcessingStarted(
    override val documentId: DocumentId,
    override val occurredAt: Instant = Instant.now()
) : DocumentDomainEvent