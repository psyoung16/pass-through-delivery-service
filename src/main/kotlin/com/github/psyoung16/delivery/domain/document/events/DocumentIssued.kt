package com.github.psyoung16.delivery.domain.document.events

import com.github.psyoung16.delivery.domain.document.DocumentId
import java.time.Instant

/**
 * 외부 기관에서 서류가 성공적으로 발급됨
 */
data class DocumentIssued(
    override val documentId: DocumentId,
    val fileUrl: String,
    override val occurredAt: Instant = Instant.now()
) : DocumentDomainEvent