package com.github.psyoung16.delivery.presentation.dto

import com.github.psyoung16.delivery.domain.document.DocumentType
import com.github.psyoung16.delivery.domain.document.IssuanceMethod

/**
 * 서류 발급 요청 Request DTO
 */
data class RequestDocumentRequest(
    val consentId: Long,
    val memberId: Long,
    val documentType: DocumentType,
    val issuanceMethod: IssuanceMethod
)