package com.github.psyoung16.delivery.domain.document

import com.github.psyoung16.delivery.domain.consent.ConsentId
import com.github.psyoung16.delivery.domain.document.events.DocumentDomainEvent
import com.github.psyoung16.delivery.domain.document.events.DocumentIssueFailed
import com.github.psyoung16.delivery.domain.document.events.DocumentIssued
import com.github.psyoung16.delivery.domain.document.events.DocumentRequested
import com.github.psyoung16.delivery.domain.document.events.DocumentUploaded
import com.github.psyoung16.delivery.domain.document.events.ProcessingRetried
import com.github.psyoung16.delivery.domain.document.events.ProcessingStarted
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
                is ProcessingStarted -> DocumentStatus.PROCESSING
                is ProcessingRetried -> DocumentStatus.PROCESSING
                is DocumentIssued -> DocumentStatus.COMPLETED
                is DocumentIssueFailed -> DocumentStatus.FAILED
            }
        }
    }

    /**
     * ConsentId 조회 (첫 번째 DocumentRequested 이벤트에서 추출)
     */
    fun consentId(): ConsentId {
        return events.filterIsInstance<DocumentRequested>().first().consentId
    }

    /**
     * MemberId 조회 (첫 번째 DocumentRequested 이벤트에서 추출)
     */
    fun memberId(): MemberId {
        return events.filterIsInstance<DocumentRequested>().first().memberId
    }

    /**
     * DocumentType 조회 (첫 번째 DocumentRequested 이벤트에서 추출)
     */
    fun documentType(): DocumentType {
        return events.filterIsInstance<DocumentRequested>().first().documentType
    }

    /**
     * IssuanceMethod 조회 (첫 번째 DocumentRequested 이벤트에서 추출)
     */
    fun issuanceMethod(): IssuanceMethod {
        return events.filterIsInstance<DocumentRequested>().first().issuanceMethod
    }

    /**
     * FileUrl 조회 (마지막 DocumentUploaded 또는 DocumentIssued 이벤트에서 추출)
     */
    fun fileUrl(): String? {
        return events.reversed().firstNotNullOfOrNull { event ->
            when (event) {
                is DocumentUploaded -> event.fileUrl
                is DocumentIssued -> event.fileUrl
                else -> null
            }
        }
    }

    /**
     * Failure Reason 조회 (마지막 DocumentIssueFailed 이벤트에서 추출)
     */
    fun failureReason(): String? {
        return events.filterIsInstance<DocumentIssueFailed>().lastOrNull()?.reason
    }

    /**
     * Failure Type 조회 (마지막 DocumentIssueFailed 이벤트에서 추출)
     */
    fun failureType(): FailureType? {
        return events.filterIsInstance<DocumentIssueFailed>().lastOrNull()?.failureType
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
     * 외부 기관에 서류 발급 처리 시작
     */
    fun startProcessing(): Pair<Document, ProcessingStarted> {
        val event = ProcessingStarted(
            documentId = id
        )
        val newDocument = replay(events + event)
        return Pair(newDocument, event)
    }

    /**
     * 외부 기관에서 서류 발급 완료
     */
    fun issueDocument(fileUrl: String): Pair<Document, DocumentIssued> {
        val event = DocumentIssued(
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

    /**
     * 2차 인증 완료 후 재시도
     */
    fun retryProcessing(): Pair<Document, ProcessingRetried> {
        val event = ProcessingRetried(
            documentId = id
        )
        val newDocument = replay(events + event)
        return Pair(newDocument, event)
    }

    /**
     * 외부 기관에서 서류 발급 실패
     */
    fun failIssuance(reason: String, failureType: FailureType): Pair<Document, DocumentIssueFailed> {
        val event = DocumentIssueFailed(
            documentId = id,
            reason = reason,
            failureType = failureType
        )
        val newDocument = replay(events + event)
        return Pair(newDocument, event)
    }
}
