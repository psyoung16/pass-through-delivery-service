package com.github.psyoung16.delivery.presentation.controller

import com.github.psyoung16.delivery.domain.document.DocumentId
import com.github.psyoung16.delivery.domain.document.DocumentStatus
import com.github.psyoung16.delivery.presentation.dto.CreateDocumentResponse
import com.github.psyoung16.delivery.presentation.dto.DocumentResponse
import com.github.psyoung16.delivery.presentation.dto.RequestDocumentRequest
import com.github.psyoung16.delivery.presentation.dto.RetryRequest
import com.github.psyoung16.delivery.presentation.dto.UploadFileRequest
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
class DocumentController {

    /**
     * 서류 발급 요청
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun requestDocument(
        @RequestBody request: RequestDocumentRequest
    ): CreateDocumentResponse {
        // 성공 케이스: documentId 반환
        return CreateDocumentResponse(documentId = 1L)
    }

    /**
     * 파일 업로드 (사용자 직접 업로드)
     */
    @PostMapping("/{id}/upload")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun uploadFile(
        @PathVariable id: Long,
        @RequestBody request: UploadFileRequest
    ) {
        // 실패 케이스: 서류를 찾을 수 없음
        if (id == 9999L) {
            throw DocumentNotFoundException(DocumentId(id))
        }

        // 성공 케이스: 204 No Content
    }

    /**
     * 서류 조회
     */
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun getDocument(
        @PathVariable id: Long
    ): DocumentResponse {
        // 실패 케이스: 서류를 찾을 수 없음
        if (id == 9999L) {
            throw DocumentNotFoundException(DocumentId(id))
        }

        // 성공 케이스: 서류 정보 반환
        return DocumentResponse(
            id = id,
            consentId = 100L,
            memberId = 200L,
            documentType = com.github.psyoung16.delivery.domain.document.DocumentType.RESIDENT_REGISTRATION,
            issuanceMethod = com.github.psyoung16.delivery.domain.document.IssuanceMethod.API_ISSUED,
            status = DocumentStatus.COMPLETED,
            fileUrl = "https://external-api.com/documents/issued-123.pdf",
            failureReason = null,
            failureType = null
        )
    }

    /**
     * 2차 인증 후 재시도
     */
    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun retryAfterAuth(
        @PathVariable id: Long,
        @RequestBody request: RetryRequest
    ) {
        // 실패 케이스: 서류를 찾을 수 없음
        if (id == 9999L) {
            throw DocumentNotFoundException(DocumentId(id))
        }

        // 실패 케이스: 잘못된 상태
        if (request.authToken == "INVALID_STATE") {
            throw InvalidDocumentStateException(
                currentStatus = DocumentStatus.COMPLETED,
                expectedStatus = DocumentStatus.TWO_WAY_AUTH_REQUIRED
            )
        }

        // 성공 케이스: 204 No Content
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