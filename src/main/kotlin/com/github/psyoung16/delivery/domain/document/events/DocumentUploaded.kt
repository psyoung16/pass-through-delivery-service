package com.github.psyoung16.delivery.domain.document.events

import com.github.psyoung16.delivery.domain.document.DocumentId
import java.time.Instant

/**
 * 서류 업로드됨 (유저 직접 업로드)
 */
data class DocumentUploaded(
    override val documentId: DocumentId,
    val fileUrl: String,
    override val occurredAt: Instant = Instant.now()
) : DocumentDomainEvent