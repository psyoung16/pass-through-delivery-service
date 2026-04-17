package com.github.psyoung16.delivery.domain.document

import com.github.psyoung16.delivery.domain.consent.ConsentId
import com.github.psyoung16.delivery.domain.document.events.DocumentDomainEvent
import com.github.psyoung16.delivery.domain.document.events.DocumentRequested
import com.github.psyoung16.delivery.domain.document.events.DocumentUploaded
import com.github.psyoung16.delivery.domain.document.events.TwoWayAuthRequired
import com.github.psyoung16.delivery.domain.member.MemberId

/**
 * Document (Event Sourcing - replay 패턴)
 *
 * DB에 저장되지 않고, 이벤트 스트림에서 재구성됩니다.
 * 모든 상태는 이벤트를 replay하여 계산됩니다.
 *
 * 비즈니스 로직은 Pair<Document, Event>를 반환하며,
 * 이벤트 저장은 Repository에서 담당합니다.
 */
data class Document(
    val id: DocumentId,
    private val events: List<DocumentDomainEvent> = emptyList()
) {
    companion object {

        /**
         * 새로운 Document 생성
         */
        fun create(
            id: DocumentId,
            consentId: ConsentId,
            memberId: MemberId,
            documentType: DocumentType,
            issuanceMethod: IssuanceMethod
        ): Pair<Document, DocumentRequested> {
            val event = DocumentRequested(
                documentId = id,
                consentId = consentId,
                memberId = memberId,
                documentType = documentType,
                issuanceMethod = issuanceMethod
            )
            val document = Document(id, listOf(event))
            return Pair(document, event)
        }
    }

    /**
     * 이벤트 리스트로 새 Document 생성 (내부 사용)
     */
    private fun replay(newEvents: List<DocumentDomainEvent>): Document {
        return Document(id, newEvents)
    }

    // ========== 상태 조회 메서드 ==========

    /**
     * 현재 상태 계산 (이벤트 replay)
     */
    fun status(): DocumentStatus {
        return events.fold(DocumentStatus.REQUESTED) { _, event ->
            when (event) {
                is DocumentRequested -> DocumentStatus.REQUESTED
                is DocumentUploaded -> DocumentStatus.COMPLETED
                is TwoWayAuthRequired -> DocumentStatus.TWO_WAY_AUTH_REQUIRED
            }
        }
    }

    // ========== 비즈니스 로직 (커맨드) ==========

    /**
     * 유저가 직접 서류를 업로드
     */
    fun uploadFile(fileUrl: String): Pair<Document, DocumentUploaded> {
        val event = DocumentUploaded(
            documentId = id,
            fileUrl = fileUrl
        )
        val newDocument = replay(events + event)
        return Pair(newDocument, event)
    }

    /**
     * 외부 기관에서 2차 인증 요구
     */
    fun requireTwoWayAuth(reason: String): Pair<Document, TwoWayAuthRequired> {
        val event = TwoWayAuthRequired(
            documentId = id,
            reason = reason
        )
        val newDocument = replay(events + event)
        return Pair(newDocument, event)
    }
}
