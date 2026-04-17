package com.github.psyoung16.delivery.presentation.exception

import com.github.psyoung16.delivery.domain.document.DocumentId

/**
 * 서류를 찾을 수 없을 때 발생하는 예외
 */
class DocumentNotFoundException(documentId: DocumentId) :
    RuntimeException("Document not found: ${documentId.value}")