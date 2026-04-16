# Event Sourcing vs CQRS 패턴 비교

## 개요

이 프로젝트는 **Event Sourcing**을 기반으로 구현하며, 향후 **CQRS**로 확장 가능하도록 설계되었습니다.

---

## 옵션 1: Event Sourcing (현재 구현)

### 개념
상태를 직접 저장하지 않고, **모든 변경사항을 이벤트로 기록**합니다. 현재 상태는 이벤트를 replay하여 계산합니다.

### 구조
```kotlin
// 1. 이벤트만 저장 (append-only)
@Entity
@Table(name = "document_events")
data class DocumentEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    val documentId: Long,

    @Enumerated(EnumType.STRING)
    val eventType: DocumentEventType,

    @Column(length = 2000)
    val payload: String,  // JSON으로 이벤트 데이터 저장

    val occurredAt: Instant = Instant.now(),

    @Column(name = "event_order")
    val order: Long = 0
)

// 2. Aggregate - DB에 저장 안 함, 메모리에서만 존재
class Document(
    val id: DocumentId,
    private val events: List<DomainEvent> = listOf()
) {
    // 상태는 이벤트에서 계산
    fun status(): DocumentStatus {
        return events.fold(DocumentStatus.REQUESTED) { status, event ->
            when (event) {
                is DocumentRequested -> DocumentStatus.REQUESTED
                is ProcessingStarted -> DocumentStatus.PROCESSING
                is TwoWayAuthRequired -> DocumentStatus.TWO_WAY_AUTH_REQUIRED
                is DocumentIssued -> DocumentStatus.COMPLETED
                is DocumentIssueFailed -> DocumentStatus.FAILED
                else -> status
            }
        }
    }

    fun fileUrl(): String? {
        return events
            .filterIsInstance<DocumentIssued>()
            .lastOrNull()
            ?.fileUrl
    }

    // 새 이벤트 적용
    fun applyEvent(event: DomainEvent): Document {
        return Document(id, events + event)
    }
}

// 3. Repository가 이벤트를 읽어서 Aggregate 재구성
class DocumentRepository {
    fun findById(id: DocumentId): Document {
        val events = eventStore.findByDocumentId(id)
        return Document.fromEvents(id, events)
    }

    fun save(document: Document) {
        // 새 이벤트만 append
        eventStore.append(document.uncommittedEvents)
    }
}
```

### 데이터 흐름
```
사용자 요청
    ↓
Command Handler
    ↓
Aggregate에 이벤트 적용
    ↓
Event Store에 이벤트 저장 (append-only)
    ↓
조회 시: Event Stream replay → 현재 상태 계산
```

### 장점
- **완전한 감사 추적**: 모든 변경 이력 보존
- **시간 여행**: 과거 특정 시점의 상태 재구성 가능
- **이벤트가 Single Source of Truth**: 상태 불일치 없음
- **디버깅 용이**: "왜 이 상태가 되었는가?" 명확히 파악
- **재시도 이력 추적**: 몇 번째 재시도인지, 왜 실패했는지 이벤트로 명확

### 단점
- **조회 성능**: 매번 이벤트 replay 필요 (이벤트 많아지면 느려짐)
- **구현 복잡도**: 전통적인 CRUD보다 러닝 커브 높음
- **스냅샷 필요**: 이벤트 많아지면 성능 최적화 위해 스냅샷 추가 필요
- **운영 대시보드**: 실시간 현황 파악이 느릴 수 있음

### 적합한 경우
- 감사 추적이 중요한 도메인 (금융, 의료, 법률)
- 복잡한 비즈니스 로직 (재시도, 보상 트랜잭션)
- 이벤트 기반 아키텍처로 확장 예정
- **포트폴리오**: Event Sourcing 경험 어필

---

## 옵션 2: CQRS (Command Query Responsibility Segregation)

### 개념
**쓰기(Command)와 읽기(Query)를 분리**합니다.
- Command Side: 이벤트 저장 (append-only)
- Query Side: 읽기 최적화된 별도 모델 (Materialized View)

### 구조
```kotlin
// ========== Command Side (Write) ==========

// 1. 이벤트 저장 (append-only)
@Entity
@Table(name = "document_events")
data class DocumentEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    val documentId: Long,

    @Enumerated(EnumType.STRING)
    val eventType: DocumentEventType,

    @Column(length = 2000)
    val payload: String,

    val occurredAt: Instant = Instant.now()
)

// ========== Query Side (Read) ==========

// 2. Read Model - 현재 상태만 (조회 최적화)
@Entity
@Table(name = "document_read_model")
data class DocumentReadModel(
    @Id val id: Long = 0,
    val consentId: Long,
    val memberId: Long,
    val type: DocumentType,
    val issuanceMethod: IssuanceMethod,

    // 현재 상태만 저장 (빠른 조회)
    @Enumerated(EnumType.STRING)
    val status: DocumentStatus = DocumentStatus.REQUESTED,
    val fileUrl: String? = null,
    val failureReason: String? = null,
    val requestedAt: Instant = Instant.now(),
    val completedAt: Instant? = null
) {
    // 조회 전용, 비즈니스 로직 없음
}

// 3. Event Handler - 이벤트 구독해서 Read Model 업데이트
@Component
class DocumentEventHandler {
    @EventListener
    fun on(event: DocumentIssued) {
        val readModel = readModelRepository.findById(event.documentId)
        readModel.copy(
            status = DocumentStatus.COMPLETED,
            fileUrl = event.fileUrl,
            completedAt = event.occurredAt
        ).let { readModelRepository.save(it) }
    }

    @EventListener
    fun on(event: DocumentIssueFailed) {
        val readModel = readModelRepository.findById(event.documentId)
        readModel.copy(
            status = DocumentStatus.FAILED,
            failureReason = event.reason
        ).let { readModelRepository.save(it) }
    }
}
```

### 데이터 흐름
```
[Command Side - Write]
사용자 요청
    ↓
Command Handler
    ↓
Event Store에 이벤트 저장 (append-only)
    ↓
Event 발행

[Event Stream]
    ↓

[Query Side - Read]
Event Handler 구독
    ↓
Read Model 업데이트
    ↓
사용자 조회 → Read Model에서 빠르게 조회
```

### 장점
- **조회 성능**: Read Model에서 바로 조회 (replay 불필요)
- **운영 대시보드**: 실시간 현황 파악 빠름
- **쓰기/읽기 독립 확장**: 각각 독립적으로 스케일 아웃 가능
- **완전한 히스토리**: Event Store에 모든 이벤트 보존
- **복잡한 쿼리 최적화**: Read Model을 조회에 최적화된 구조로 설계 가능

### 단점
- **복잡도 증가**: 두 개의 모델 관리
- **동기화 이슈**: Event → Read Model 업데이트 시 일시적 불일치 (Eventual Consistency)
- **저장 공간**: Event + Read Model 모두 저장
- **구현 비용**: EventHandler, Read Model Repository 추가 필요

### 적합한 경우
- 읽기가 쓰기보다 월등히 많은 경우
- 운영 대시보드/실시간 모니터링 필요
- 대용량 트래픽 대응 (쓰기/읽기 서버 분리)
- 복잡한 조회 쿼리 필요 (집계, 통계)

---

## 옵션 1 vs 옵션 2 비교표

| 특징 | 옵션 1: Event Sourcing | 옵션 2: CQRS |
|------|----------------------|--------------|
| **저장** | 이벤트만 (append-only) | 이벤트 + Read Model |
| **조회 성능** | 느림 (replay 필요) | 빠름 (Read Model 직접 조회) |
| **감사 추적** | 완벽 | 완벽 |
| **실시간 현황** | 느릴 수 있음 | 빠름 |
| **구현 복잡도** | 중간 | 높음 |
| **일관성** | 강한 일관성 | Eventual Consistency |
| **확장성** | 단일 시스템 | 쓰기/읽기 독립 확장 |
| **저장 공간** | 적음 (이벤트만) | 많음 (이벤트 + Read Model) |

---

## 이 프로젝트의 전략

### Phase 1: Event Sourcing (현재 구현)
- 순수 Event Sourcing으로 시작
- DocumentEvent 테이블만 사용
- 조회는 이벤트 replay
- **목표**: Event Sourcing 개념 확실히 구현

### Phase 2: CQRS 확장 (향후)
- DocumentReadModel 추가
- EventHandler로 Read Model 자동 업데이트
- 운영 대시보드는 Read Model 조회
- **목표**: 읽기 성능 최적화 + 쓰기/읽기 분리

### 포트폴리오 어필 포인트

**면접 시 설명:**
> "Event Sourcing으로 구현하여 완전한 감사 추적과 이벤트 기반 아키텍처를 경험했습니다.
> 현재는 단일 Event Store를 사용하지만, 트래픽이 증가하면 CQRS 패턴으로 확장하여
> 쓰기와 읽기를 분리하고, 각각 독립적으로 스케일 아웃할 수 있도록 설계했습니다."

**기술적 깊이:**
- Event Sourcing: 이벤트 replay, Aggregate 재구성
- CQRS: Command/Query 분리, Eventual Consistency
- 확장성: 쓰기/읽기 서버 분리, 독립 스케일링

---

## 레거시 vs Event Sourcing 차이

### 레거시 (데이터 중심)
```sql
-- 현재 상태만 저장
SELECT * FROM documents WHERE status = 'FAILED';

-- 재시도 이력: 복잡한 recursive 쿼리 필요
WITH RECURSIVE history AS (
    SELECT * FROM documents WHERE id = 1
    UNION ALL
    SELECT d.* FROM documents d
    JOIN history h ON d.parent_id = h.id
)
SELECT * FROM history;
```
**문제**: "왜 실패했는가?", "몇 번째 재시도인가?" 파악하려면 여러 레코드 직접 분석

### Event Sourcing (이벤트 중심)
```kotlin
// 이벤트 스트림 조회
val events = eventStore.findByDocumentId(documentId)
// [DocumentRequested, ProcessingStarted, DocumentIssueFailed,
//  DocumentResubmitted, TwoWayAuthRequired, UserAuthenticated, DocumentIssued]
```
**장점**: 각 단계의 **맥락**(왜, 언제, 어떻게)이 이벤트에 포함되어 전체 스토리 파악 가능

---

## Event Sourcing 구현 방식 비교

Event Sourcing을 구현하는 두 가지 주요 방식이 있습니다.

### 방식 1: uncommittedEvents 패턴

Aggregate가 직접 이벤트를 관리하는 방식입니다.

```kotlin
data class Document(
    val id: DocumentId,
    private val events: List<DomainEvent> = emptyList(),
    private val uncommittedEvents: List<DomainEvent> = emptyList()
) {
    /**
     * 비즈니스 로직 - 새 이벤트 생성
     */
    fun complete(fileUrl: String): Document {
        require(status() == DocumentStatus.PROCESSING) {
            "Only processing documents can be completed"
        }
        val event = DocumentIssued(documentId = id, fileUrl = fileUrl)
        return copy(uncommittedEvents = uncommittedEvents + event)
    }

    /**
     * 현재 상태 계산 (committed events만 사용)
     */
    fun status(): DocumentStatus {
        return events.fold(DocumentStatus.REQUESTED) { status, event ->
            when (event) {
                is DocumentIssued -> DocumentStatus.COMPLETED
                is DocumentIssueFailed -> DocumentStatus.FAILED
                // ...
                else -> status
            }
        }
    }

    /**
     * 미커밋 이벤트 조회
     */
    fun getUncommittedEvents(): List<DomainEvent> = uncommittedEvents

    /**
     * 이벤트 커밋 후 새 Aggregate 반환
     */
    fun markEventsAsCommitted(): Document {
        return copy(
            events = events + uncommittedEvents,
            uncommittedEvents = emptyList()
        )
    }
}

// Repository 사용 예시
class DocumentRepository(private val eventStore: EventStore) {
    fun save(document: Document): Document {
        // 1. 미커밋 이벤트를 Event Store에 저장
        document.getUncommittedEvents().forEach { event ->
            eventStore.append(event)
        }

        // 2. 이벤트 커밋 처리
        return document.markEventsAsCommitted()
    }

    fun findById(id: DocumentId): Document {
        val events = eventStore.findByDocumentId(id)
        return Document.fromEvents(id, events)
    }
}
```

**장점:**
- Aggregate가 이벤트 관리 책임을 가짐 (응집도 높음)
- 트랜잭션 경계가 명확 (uncommittedEvents가 경계)
- Unit of Work 패턴과 잘 맞음
- 여러 이벤트를 한 번에 저장 가능

**단점:**
- 구현이 복잡함
- Aggregate가 이벤트 관리 + 비즈니스 로직 두 가지 책임
- 불변성 유지를 위한 copy 호출이 많음

---

### 방식 2: replay 패턴 (이 프로젝트 채택)

Aggregate는 순수하게 상태 계산만 담당하고, 이벤트는 외부에서 관리하는 방식입니다.

```kotlin
data class Document(
    val id: DocumentId,
    private val events: List<DomainEvent> = emptyList()
) {
    companion object {
        /**
         * 이벤트 스트림에서 Document 재구성
         */
        fun fromEvents(id: DocumentId, events: List<DomainEvent>): Document {
            return Document(id, events)
        }
    }

    /**
     * 비즈니스 로직 - 새 이벤트 반환
     */
    fun complete(fileUrl: String): Pair<Document, DocumentIssued> {
        require(status() == DocumentStatus.PROCESSING) {
            "Only processing documents can be completed"
        }
        val event = DocumentIssued(documentId = id, fileUrl = fileUrl)
        val newDocument = replay(events + event)
        return Pair(newDocument, event)
    }

    /**
     * 현재 상태 계산
     */
    fun status(): DocumentStatus {
        return events.fold(DocumentStatus.REQUESTED) { status, event ->
            when (event) {
                is DocumentIssued -> DocumentStatus.COMPLETED
                is DocumentIssueFailed -> DocumentStatus.FAILED
                // ...
                else -> status
            }
        }
    }

    /**
     * 이벤트 리스트로 새 Document 생성 (내부 사용)
     */
    private fun replay(newEvents: List<DomainEvent>): Document {
        return Document(id, newEvents)
    }
}

// Repository 사용 예시
class DocumentRepository(private val eventStore: EventStore) {
    fun save(document: Document, event: DomainEvent): Document {
        // 1. Event Store에 이벤트 저장
        eventStore.append(event)

        // 2. 이미 replay된 Document 반환 (추가 작업 불필요)
        return document
    }

    fun findById(id: DocumentId): Document {
        val events = eventStore.findByDocumentId(id)
        return Document.fromEvents(id, events)
    }
}

// 사용 예시
val document = repository.findById(documentId)
val (updatedDocument, event) = document.complete("https://file.url")
repository.save(updatedDocument, event)
```

**장점:**
- **단순함**: Aggregate는 상태 계산만 집중
- 이벤트 관리 책임이 외부(Repository, EventStore)에 위임
- Aggregate가 순수 함수에 가까움 (테스트 쉬움)
- **실무에서 많이 사용**: 이해하기 쉽고 구현이 직관적

**단점:**
- Aggregate가 이벤트를 직접 관리하지 않음
- Pair 반환으로 인해 코드가 약간 장황해질 수 있음
- 여러 이벤트를 한 번에 처리하려면 List<Event> 반환 필요

---

### 방식 비교표

| 특징 | uncommittedEvents | replay (채택) |
|------|-------------------|---------------|
| **복잡도** | 높음 | 낮음 |
| **Aggregate 책임** | 이벤트 관리 + 비즈니스 로직 | 비즈니스 로직만 |
| **이벤트 저장** | Repository가 uncommittedEvents 저장 | Repository가 반환된 event 저장 |
| **불변성** | copy로 새 Aggregate 생성 | 이미 replay된 Aggregate 반환 |
| **테스트** | 중간 (이벤트 커밋 로직 필요) | 쉬움 (순수 함수) |
| **실무 사용** | 복잡한 도메인 | 일반적인 도메인 |
| **코드 가독성** | 낮음 (commit 로직 복잡) | 높음 (직관적) |

---

### 이 프로젝트의 선택: replay 패턴

**선택 이유:**
1. **학습 목적**: Event Sourcing의 핵심인 "이벤트로부터 상태 계산"에 집중
2. **단순함**: Aggregate가 순수하게 비즈니스 로직만 담당
3. **실무 경험**: 실제 프로젝트에서 사용했던 방식
4. **테스트 용이성**: Aggregate 테스트가 단순해짐

**트레이드오프 수용:**
- Pair 반환으로 인한 약간의 장황함은 코드 명확성으로 상쇄
- 이벤트 관리를 외부에 위임함으로써 Aggregate의 순수성 확보

---

## 결론

- **Event Sourcing (옵션 1)**: 감사 추적, 이벤트 기반 아키텍처 학습, 복잡한 비즈니스 로직
- **CQRS (옵션 2)**: Event Sourcing + 읽기 성능 최적화, 대용량 트래픽 대응
- **구현 방식**: replay 패턴 (단순하고 실용적)

**이 프로젝트는 옵션 1 (Event Sourcing)을 replay 패턴으로 구현하여, 향후 옵션 2 (CQRS)로 자연스럽게 확장 가능하도록 설계합니다.**