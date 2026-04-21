package com.github.psyoung16.delivery.application.service

import com.github.psyoung16.delivery.domain.consent.ConsentId
import com.github.psyoung16.delivery.domain.document.*
import com.github.psyoung16.delivery.domain.member.MemberId
import com.github.psyoung16.delivery.infrastructure.external.ExternalApiClient
import com.github.psyoung16.delivery.presentation.exception.DocumentNotFoundException
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

/**
 * Document 발급 서비스
 *
 * 간소화 버전: 인메모리 Map으로 Document 관리
 */
@Service
class DocumentIssuanceService(
    private val externalApiClient: ExternalApiClient
) {

    private val documents: MutableMap<Long, Document> = mutableMapOf()
    private var currentId = 1L

    /**
     * 서류 발급 요청
     */
    fun requestDocument(
        consentId: ConsentId,
        memberId: MemberId,
        documentType: DocumentType,
        issuanceMethod: IssuanceMethod
    ): DocumentId {
        val documentId = DocumentId(currentId++)
        val (document, event) = Document.create(
            id = documentId,
            consentId = consentId,
            memberId = memberId,
            documentType = documentType,
            issuanceMethod = issuanceMethod
        )
        documents[documentId.value] = document
        return documentId
    }

    /**
     * 파일 업로드 (사용자 직접 업로드)
     */
    fun uploadFile(documentId: DocumentId, fileUrl: String) {
        val document = getDocumentOrThrow(documentId)
        val (updatedDocument, event) = document.uploadFile(fileUrl)
        documents[documentId.value] = updatedDocument
    }

    /**
     * 외부 API 발급 프로세스 시작
     */
    fun startProcessing(documentId: DocumentId) {
        val document = getDocumentOrThrow(documentId)
        val (updatedDocument, event) = document.startProcessing()
        documents[documentId.value] = updatedDocument
    }

    /**
     * 외부 API로 서류 발급 완료
     */
    fun issueDocument(documentId: DocumentId, fileUrl: String) {
        val document = getDocumentOrThrow(documentId)
        val (updatedDocument, event) = document.issueDocument(fileUrl)
        documents[documentId.value] = updatedDocument
    }

    /**
     * 2차 인증 요구
     */
    fun requireTwoWayAuth(documentId: DocumentId, reason: String) {
        val document = getDocumentOrThrow(documentId)
        val (updatedDocument, event) = document.requireTwoWayAuth(reason)
        documents[documentId.value] = updatedDocument
    }

    /**
     * 2차 인증 후 재시도
     */
    fun retryProcessing(documentId: DocumentId) {
        val document = getDocumentOrThrow(documentId)
        val (updatedDocument, event) = document.retryProcessing()
        documents[documentId.value] = updatedDocument
    }

    /**
     * 서류 발급 실패
     */
    fun failIssuance(documentId: DocumentId, reason: String, failureType: FailureType) {
        val document = getDocumentOrThrow(documentId)
        val (updatedDocument, event) = document.failIssuance(reason, failureType)
        documents[documentId.value] = updatedDocument
    }

    /**
     * API 자동 발급 요청 (간편인증 포함)
     *
     * @return transactionId (외부 API에서 받은 거래 ID)
     */
    fun requestApiIssuanceWithAuth(
        documentId: DocumentId,
        userName: String,
        phoneNo: String,
        identity: String,
        easyAuthMethod: String
    ): String {
        // 외부 API에 간편인증 요청 (500ms 소요)
        val requestId = externalApiClient.requestEasyAuth(
            userName = userName,
            phoneNo = phoneNo,
            identity = identity,
            easyAuthMethod = easyAuthMethod
        )

        // TwoWayAuthRequired 이벤트 발행
        requireTwoWayAuth(documentId, "$easyAuthMethod 인증 필요")

        return requestId
    }

    /**
     * 비동기로 외부 API 서류 발급 시작 (Pass-Through 핵심)
     *
     * Controller는 즉시 응답하고, 이 메서드는 백그라운드에서 실행
     * 10초~3분 걸리는 작업을 비동기로 처리
     */
    @Async
    fun startIssuanceAsync(documentId: DocumentId, transactionId: String) {
        try {
            // 1. 재시도 이벤트 발행
            retryProcessing(documentId)

            // 2. 외부 API에 실제 발급 요청 (10초~3분 소요)
            val fileUrl = externalApiClient.issueDocument(transactionId)

            // 3. 발급 완료 이벤트 발행
            issueDocument(documentId, fileUrl)
        } catch (e: Exception) {
            // 4. 실패 시 이벤트 발행
            failIssuance(documentId, e.message ?: "Unknown error", FailureType.PERMANENT)
        }
    }

    /**
     * 서류 조회
     */
    fun getDocument(documentId: DocumentId): Document {
        return getDocumentOrThrow(documentId)
    }

    private fun getDocumentOrThrow(documentId: DocumentId): Document {
        return documents[documentId.value]
            ?: throw DocumentNotFoundException(documentId)
    }
}