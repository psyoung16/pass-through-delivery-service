package com.github.psyoung16.delivery.presentation.exception

import com.github.psyoung16.delivery.domain.document.DocumentStatus

/**
 * 잘못된 서류 상태에서 작업을 시도할 때 발생하는 예외
 */
class InvalidDocumentStateException(
    currentStatus: DocumentStatus,
    expectedStatus: DocumentStatus
) : RuntimeException(
    "Invalid document state. Current: $currentStatus, Expected: $expectedStatus"
)