package com.github.psyoung16.delivery.presentation.controller

import com.github.psyoung16.delivery.application.service.DocumentIssuanceService
import com.github.psyoung16.delivery.domain.consent.ConsentId
import com.github.psyoung16.delivery.domain.document.DocumentId
import com.github.psyoung16.delivery.domain.member.MemberId
import com.github.psyoung16.delivery.presentation.dto.CreateDocumentResponse
import com.github.psyoung16.delivery.presentation.dto.DocumentResponse
import com.github.psyoung16.delivery.presentation.dto.UploadDocumentRequest
import com.github.psyoung16.delivery.presentation.exception.DocumentNotFoundException
import com.github.psyoung16.delivery.presentation.exception.ErrorResponse
import com.github.psyoung16.delivery.presentation.exception.InvalidDocumentStateException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Document REST API Controller
 */
@RestController
@RequestMapping("/api/documents")
class DocumentController(
    private val documentIssuanceService: DocumentIssuanceService
) {

    /**
     * 사용자 직접 업로드 (원샷)
     */
    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED)
    fun uploadDocument(
        @RequestBody request: UploadDocumentRequest
    ): CreateDocumentResponse {
        // 1. Document 생성
        val documentId = documentIssuanceService.requestDocument(
            consentId = ConsentId(request.consentId),
            memberId = MemberId(request.memberId),
            documentType = request.documentType,
            issuanceMethod = com.github.psyoung16.delivery.domain.document.IssuanceMethod.USER_UPLOADED
        )

        // 2. 즉시 파일 업로드
        documentIssuanceService.uploadFile(
            documentId = documentId,
            fileUrl = request.fileUrl
        )

        return CreateDocumentResponse(documentId = documentId.value)
    }

    /**
     * 서류 조회
     */
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun getDocument(
        @PathVariable id: Long
    ): DocumentResponse {
        val document = documentIssuanceService.getDocument(DocumentId(id))

        return DocumentResponse(
            id = document.id.value,
            consentId = document.consentId().value,
            memberId = document.memberId().value,
            documentType = document.documentType(),
            issuanceMethod = document.issuanceMethod(),
            status = document.status(),
            fileUrl = document.fileUrl(),
            failureReason = document.failureReason(),
            failureType = document.failureType()
        )
    }

    /**
     * 서류를 찾을 수 없는 경우 예외 처리
     */
    @ExceptionHandler(DocumentNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleDocumentNotFoundException(ex: DocumentNotFoundException): ErrorResponse {
        return ErrorResponse(
            status = HttpStatus.NOT_FOUND.value(),
            error = "NOT_FOUND",
            message = ex.message ?: "Document not found"
        )
    }

    /**
     * 잘못된 서류 상태에서 작업 시도 시 예외 처리
     */
    @ExceptionHandler(InvalidDocumentStateException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInvalidDocumentStateException(ex: InvalidDocumentStateException): ErrorResponse {
        return ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = "INVALID_STATE",
            message = ex.message ?: "Invalid document state"
        )
    }

    /**
     * 잘못된 요청 파라미터 예외 처리
     */
    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ErrorResponse {
        return ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = "INVALID_ARGUMENT",
            message = ex.message ?: "Invalid argument"
        )
    }
}