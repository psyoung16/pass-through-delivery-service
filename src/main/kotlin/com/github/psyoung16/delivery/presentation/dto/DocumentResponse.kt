package com.github.psyoung16.delivery.presentation.dto

import com.github.psyoung16.delivery.domain.document.DocumentStatus
import com.github.psyoung16.delivery.domain.document.DocumentType
import com.github.psyoung16.delivery.domain.document.FailureType
import com.github.psyoung16.delivery.domain.document.IssuanceMethod

/**
 * 서류 조회 Response DTO
 */
data class DocumentResponse(
    val id: Long,
    val consentId: Long,
    val memberId: Long,
    val documentType: DocumentType,
    val issuanceMethod: IssuanceMethod,
    val status: DocumentStatus,
    val fileUrl: String? = null,
    val failureReason: String? = null,
    val failureType: FailureType? = null
)