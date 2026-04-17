package com.github.psyoung16.delivery.domain.document

/**
 * 서류 유형
 */
enum class DocumentType(val description: String) {
    RESIDENT_REGISTRATION("주민등록등본"),
    FAMILY_RELATIONSHIP_CERTIFICATE("가족관계증명서")
}
