package com.github.psyoung16.delivery.domain.document.events

import com.github.psyoung16.delivery.domain.consent.ConsentId
import com.github.psyoung16.delivery.domain.document.DocumentId
import com.github.psyoung16.delivery.domain.document.DocumentType
import com.github.psyoung16.delivery.domain.document.IssuanceMethod
import com.github.psyoung16.delivery.domain.member.MemberId
import java.time.Instant

/**
 * 서류 발급 요청됨
 */
data class DocumentRequested(
    override val documentId: DocumentId,
    val consentId: ConsentId,
    val memberId: MemberId,
    val documentType: DocumentType,
    val issuanceMethod: IssuanceMethod,
    override val occurredAt: Instant = Instant.now()
) : DocumentDomainEvent
