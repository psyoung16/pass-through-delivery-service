package com.github.psyoung16.delivery.domain.document.events

import com.github.psyoung16.delivery.domain.document.DocumentId
import com.github.psyoung16.delivery.domain.document.FailureType
import java.time.Instant

/**
 * 외부 기관에서 서류 발급 실패
 */
data class DocumentIssueFailed(
    override val documentId: DocumentId,
    val reason: String,
    val failureType: FailureType,
    override val occurredAt: Instant = Instant.now()
) : DocumentDomainEvent