package com.github.psyoung16.delivery.domain.document.events

import com.github.psyoung16.delivery.domain.document.DocumentId
import java.time.Instant

/**
 * 2차 인증 완료 후 서류 발급 재시도됨
 */
data class ProcessingRetried(
    override val documentId: DocumentId,
    override val occurredAt: Instant = Instant.now()
) : DocumentDomainEvent