package com.github.psyoung16.delivery.domain.document

import jakarta.persistence.*
import java.time.Instant

/**
 * Document Event Store
 * 모든 Document 도메인 이벤트를 append-only로 저장
 */
@Entity
@Table(
    name = "document_events",
    indexes = [
        Index(name = "idx_document_id", columnList = "documentId"),
        Index(name = "idx_document_id_order", columnList = "documentId,eventOrder")
    ]
)
data class DocumentEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    /**
     * 이벤트가 속한 Document 식별자
     */
    val documentId: Long,

    /**
     * 이벤트 타입
     */
    @Enumerated(EnumType.STRING)
    val eventType: DocumentEventType,

    /**
     * 이벤트 데이터 (JSON)
     */
    @Column(length = 2000)
    val payload: String,

    /**
     * 이벤트 발생 시각
     */
    val occurredAt: Instant = Instant.now(),

    /**
     * 이벤트 순서 (같은 documentId 내에서)
     */
    val eventOrder: Long = 0
) {
    // append-only: 수정/삭제 불가
    // 오직 새로운 이벤트만 추가 가능
}
