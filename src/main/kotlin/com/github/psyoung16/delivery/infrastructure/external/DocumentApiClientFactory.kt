package com.github.psyoung16.delivery.infrastructure.external

import com.github.psyoung16.delivery.domain.document.DocumentType
import org.springframework.stereotype.Component

/**
 * Document 타입에 따라 적절한 외부 API 클라이언트를 제공하는 Factory
 *
 * 각 서류 타입은 서로 다른 외부 API를 호출함:
 * - RESIDENT_REGISTRATION → 정부24 API (Gov24ApiClient)
 * - FAMILY_RELATIONSHIP_CERTIFICATE → 대법원 API (CourtApiClient)
 */
@Component
class DocumentApiClientFactory(
    private val gov24ApiClient: Gov24ApiClient,
    private val courtApiClient: CourtApiClient
) {

    /**
     * DocumentType에 따라 적절한 API 클라이언트 반환
     */
    fun getClient(documentType: DocumentType): ExternalApiClient {
        return when (documentType) {
            DocumentType.RESIDENT_REGISTRATION -> gov24ApiClient
            DocumentType.FAMILY_RELATIONSHIP_CERTIFICATE -> courtApiClient
        }
    }
}