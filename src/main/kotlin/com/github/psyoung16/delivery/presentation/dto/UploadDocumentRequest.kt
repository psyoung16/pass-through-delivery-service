package com.github.psyoung16.delivery.presentation.dto

import com.github.psyoung16.delivery.domain.document.DocumentType

/**
 * 사용자 직접 업로드 요청 (원샷)
 */
data class UploadDocumentRequest(
    val consentId: Long,
    val memberId: Long,
    val documentType: DocumentType,
    val fileUrl: String
)