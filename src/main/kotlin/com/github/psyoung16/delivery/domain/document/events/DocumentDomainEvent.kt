package com.github.psyoung16.delivery.domain.document.events

import com.github.psyoung16.delivery.domain.DomainEvent
import com.github.psyoung16.delivery.domain.document.DocumentId

/**
 * Document 도메인 이벤트
 */
sealed interface DocumentDomainEvent : DomainEvent {
    val documentId: DocumentId
}
